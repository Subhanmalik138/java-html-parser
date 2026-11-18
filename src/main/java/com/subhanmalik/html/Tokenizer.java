package com.subhanmalik.html;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Hand-rolled HTML tokenizer: turns a source string into a stream of
 * {@link Token}s.
 *
 * <p>Deliberately small but faithful to the parts of the HTML spec that
 * matter for real-world pages:
 * <ul>
 *   <li>start tags, end tags and self-closing tags</li>
 *   <li>double-quoted, single-quoted, unquoted and boolean attributes</li>
 *   <li>comments ({@code <!-- ... -->}) and the doctype</li>
 *   <li>character references in text and attribute values
 *       ({@code &amp;}, {@code &lt;}, {@code &#169;}, {@code &#xA9;}, ...)</li>
 *   <li>raw-text elements ({@code script}, {@code style}, {@code textarea},
 *       {@code title}, {@code xmp}) whose contents are not tokenized as
 *       markup — a stray {@code <div>} inside a script is just script text</li>
 *   <li>tag and attribute names are lower-cased, as browsers do</li>
 * </ul>
 *
 * <p>A {@code <} that does not start a valid tag (e.g. {@code "a < b"}) is
 * emitted as literal text, matching browser behaviour.
 */
public final class Tokenizer {

    private static final Set<String> RAW_TEXT_ELEMENTS =
            Set.of("script", "style", "textarea", "title", "xmp",
                   "iframe", "noembed", "noframes", "noscript");

    private static final Map<String, Integer> NAMED_ENTITIES = new HashMap<>();

    static {
        // The references that show up in practice; anything else is left
        // literal rather than guessed at.
        String[][] pairs = {
            {"amp", "&"}, {"lt", "<"}, {"gt", ">"}, {"quot", "\""},
            {"apos", "'"}, {"nbsp", "\u00A0"}, {"copy", "\u00A9"},
            {"reg", "\u00AE"}, {"trade", "\u2122"}, {"hellip", "\u2026"},
            {"mdash", "\u2014"}, {"ndash", "\u2013"}, {"lsquo", "\u2018"},
            {"rsquo", "\u2019"}, {"ldquo", "\u201C"}, {"rdquo", "\u201D"},
            {"laquo", "\u00AB"}, {"raquo", "\u00BB"}, {"times", "\u00D7"},
            {"divide", "\u00F7"}, {"plusmn", "\u00B1"}, {"deg", "\u00B0"},
            {"frac12", "\u00BD"}, {"frac14", "\u00BC"}, {"frac34", "\u00BE"},
            {"eacute", "\u00E9"}, {"egrave", "\u00E8"}, {"agrave", "\u00E0"},
            {"aacute", "\u00E1"}, {"uuml", "\u00FC"}, {"ouml", "\u00F6"},
            {"szlig", "\u00DF"}, {"sect", "\u00A7"}, {"para", "\u00B6"},
            {"middot", "\u00B7"}, {"bull", "\u2022"}, {"dagger", "\u2020"},
            {"Dagger", "\u2021"}, {"permil", "\u2030"}, {"euro", "\u20AC"},
            {"pound", "\u00A3"}, {"yen", "\u00A5"}, {"cent", "\u00A2"},
            {"curren", "\u00A4"}, {"brvbar", "\u00A6"}, {"uml", "\u00A8"},
            {"ordf", "\u00AA"}, {"ordm", "\u00BA"}, {"iexcl", "\u00A1"},
            {"iquest", "\u00BF"}, {"not", "\u00AC"}, {"macr", "\u00AF"},
            {"acute", "\u00B4"}, {"cedil", "\u00B8"}, {"sup1", "\u00B9"},
            {"sup2", "\u00B2"}, {"sup3", "\u00B3"}, {"micro", "\u00B5"},
            {"para", "\u00B6"}
        };
        for (String[] pair : pairs) {
            NAMED_ENTITIES.put(pair[0], pair[1].codePointAt(0));
        }
    }

    private final String input;
    private int pos;
    /** Element whose raw text we are currently consuming, or null. */
    private String rawTextTag;

    public Tokenizer(String input) {
        this.input = input == null ? "" : input;
    }

    public Token nextToken() {
        if (rawTextTag != null) {
            return nextRawTextToken();
        }
        if (pos >= input.length()) {
            return Token.eof();
        }
        char c = input.charAt(pos);
        if (c == '<') {
            Token tag = tryReadMarkup();
            if (tag != null) {
                return tag;
            }
            // A '<' that starts nothing valid is literal text.
        }
        return readText();
    }

    // ------------------------------------------------------------------
    // Text
    // ------------------------------------------------------------------

    private Token readText() {
        int start = pos;
        // Consume until a '<' that actually begins markup; a stray '<'
        // (e.g. "a < b") is literal text and is consumed as such.
        while (pos < input.length()) {
            if (input.charAt(pos) == '<' && pos != start && startsMarkupAt(pos)) {
                break;
            }
            pos++;
        }
        return Token.text(decodeEntities(input.substring(start, pos)));
    }

    /** Pure lookahead: does markup begin at {@code at}? Never moves {@code pos}. */
    private boolean startsMarkupAt(int at) {
        if (input.startsWith("<!--", at)
                || startsWithIgnoreCase(at, "<!doctype")
                || startsWithIgnoreCase(at, "<![CDATA[")) {
            return true;
        }
        if (input.startsWith("</", at)) {
            return at + 2 < input.length() && isAsciiLetter(input.charAt(at + 2));
        }
        return at + 1 < input.length() && isAsciiLetter(input.charAt(at + 1));
    }

    /** Reads the body of a raw-text element up to its matching end tag. */
    private Token nextRawTextToken() {
        String tag = rawTextTag;
        String close = "</" + tag;
        int end = indexOfIgnoreCase(input, close, pos);
        if (end < 0) {
            // Unterminated raw text: the rest of the document is the body.
            String body = input.substring(pos);
            pos = input.length();
            rawTextTag = null;
            return Token.text(decodeRawText(body, tag));
        }
        String body = input.substring(pos, end);
        pos = end;
        rawTextTag = null;
        if (body.isEmpty()) {
            // Empty body — fall through to the end tag itself.
            return nextToken();
        }
        return Token.text(decodeRawText(body, tag));
    }

    /**
     * {@code textarea} and {@code title} are RCDATA: character references
     * are decoded. {@code script} and {@code style} are true raw text and
     * are left untouched.
     */
    private static String decodeRawText(String body, String tag) {
        return (tag.equals("textarea") || tag.equals("title"))
                ? decodeEntities(body) : body;
    }

    // ------------------------------------------------------------------
    // Markup starting with '<'
    // ------------------------------------------------------------------

    /** Returns a token if the input at {@code pos} starts valid markup, else null. */
    private Token tryReadMarkup() {
        if (input.startsWith("<!--", pos)) {
            return readComment();
        }
        if (startsWithIgnoreCase(pos, "<!doctype")) {
            return readDoctype();
        }
        if (startsWithIgnoreCase(pos, "<![CDATA[")) {
            // Treat CDATA as text; it only appears in foreign content.
            int end = input.indexOf("]]>", pos + 9);
            int stop = end < 0 ? input.length() : end + 3;
            String body = input.substring(pos + 9, end < 0 ? stop : end);
            pos = stop;
            return Token.text(body);
        }
        if (input.startsWith("</", pos)) {
            return readEndTag();
        }
        if (pos + 1 < input.length() && isAsciiLetter(input.charAt(pos + 1))) {
            return readStartTag();
        }
        return null;
    }

    private Token readComment() {
        int start = pos + 4;
        int end = input.indexOf("-->", start);
        if (end < 0) {
            pos = input.length();
            return Token.comment(input.substring(start));
        }
        pos = end + 3;
        return Token.comment(input.substring(start, end));
    }

    private Token readDoctype() {
        int end = input.indexOf('>', pos);
        if (end < 0) {
            pos = input.length();
            return Token.doctype(input.substring(pos));
        }
        String body = input.substring(pos + 9, end).trim();
        pos = end + 1;
        return Token.doctype(body);
    }

    private Token readStartTag() {
        pos++; // consume '<'
        String name = readTagName();
        Token token = Token.startTag(name);
        readAttributes(token);
        if (RAW_TEXT_ELEMENTS.contains(name) && !token.selfClosing()) {
            rawTextTag = name;
        }
        return token;
    }

    private Token readEndTag() {
        pos += 2; // consume '</'
        String name = readTagName();
        // Skip junk up to '>' (browsers tolerate attributes on end tags).
        while (pos < input.length() && input.charAt(pos) != '>') {
            pos++;
        }
        if (pos < input.length()) {
            pos++; // consume '>'
        }
        return Token.endTag(name);
    }

    private String readTagName() {
        int start = pos;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isWhitespace(c) || c == '/' || c == '>') {
                break;
            }
            pos++;
        }
        return input.substring(start, pos).toLowerCase();
    }

    private void readAttributes(Token token) {
        while (true) {
            skipWhitespace();
            if (pos >= input.length()) {
                return;
            }
            char c = input.charAt(pos);
            if (c == '>') {
                pos++;
                return;
            }
            if (c == '/') {
                if (pos + 1 < input.length() && input.charAt(pos + 1) == '>') {
                    token.setSelfClosing(true);
                    pos += 2;
                    return;
                }
                pos++; // stray slash, ignore
                continue;
            }
            int nameStart = pos;
            while (pos < input.length()) {
                char ch = input.charAt(pos);
                if (Character.isWhitespace(ch) || ch == '=' || ch == '/' || ch == '>') {
                    break;
                }
                pos++;
            }
            if (pos == nameStart) {
                pos++; // defensive: never stall on an unexpected char
                continue;
            }
            String attrName = input.substring(nameStart, pos).toLowerCase();
            skipWhitespace();
            String value = "";
            if (pos < input.length() && input.charAt(pos) == '=') {
                pos++;
                skipWhitespace();
                value = readAttributeValue();
            }
            token.setAttribute(attrName, value);
        }
    }

    private String readAttributeValue() {
        if (pos >= input.length()) {
            return "";
        }
        char quote = input.charAt(pos);
        if (quote == '"' || quote == '\'') {
            int start = pos + 1;
            int end = input.indexOf(quote, start);
            if (end < 0) {
                pos = input.length();
                return decodeEntities(input.substring(start));
            }
            pos = end + 1;
            return decodeEntities(input.substring(start, end));
        }
        int start = pos;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isWhitespace(c) || c == '>') {
                break;
            }
            pos++;
        }
        return decodeEntities(input.substring(start, pos));
    }

    // ------------------------------------------------------------------
    // Character references
    // ------------------------------------------------------------------

    /**
     * Decodes {@code &name;}, {@code &#123;} and {@code &#xAB;} references.
     * An unknown or unterminated reference is left literal, as browsers do.
     */
    static String decodeEntities(String text) {
        int amp = text.indexOf('&');
        if (amp < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c != '&') {
                out.append(c);
                i++;
                continue;
            }
            int semi = text.indexOf(';', i + 1);
            if (semi < 0 || semi - i > 10) {
                out.append(c);
                i++;
                continue;
            }
            String ref = text.substring(i + 1, semi);
            Integer codePoint = resolveEntity(ref);
            if (codePoint == null) {
                out.append(text, i, semi + 1);
            } else {
                out.appendCodePoint(codePoint);
            }
            i = semi + 1;
        }
        return out.toString();
    }

    private static Integer resolveEntity(String ref) {
        if (ref.isEmpty()) {
            return null;
        }
        if (ref.charAt(0) == '#') {
            try {
                int value;
                if (ref.length() > 2 && (ref.charAt(1) == 'x' || ref.charAt(1) == 'X')) {
                    value = Integer.parseInt(ref.substring(2), 16);
                } else {
                    value = Integer.parseInt(ref.substring(1), 10);
                }
                if (value == 0 || value > Character.MAX_CODE_POINT || Character.isSurrogate((char) value)) {
                    return 0xFFFD; // replacement character
                }
                return value;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return NAMED_ENTITIES.get(ref);
    }

    // ------------------------------------------------------------------
    // Small helpers
    // ------------------------------------------------------------------

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private boolean startsWithIgnoreCase(int at, String prefix) {
        return input.regionMatches(true, at, prefix, 0, prefix.length());
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        int max = haystack.length() - needle.length();
        for (int i = from; i <= max; i++) {
            if (haystack.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
}
