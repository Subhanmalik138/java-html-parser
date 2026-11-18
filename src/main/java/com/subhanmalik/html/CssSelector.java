package com.subhanmalik.html;

import com.subhanmalik.html.Dom.Element;
import com.subhanmalik.html.Dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * A small CSS selector engine over the DOM from {@link HtmlParser}.
 *
 * <p>Supported syntax:
 * <ul>
 *   <li>type selectors ({@code div}), the universal selector ({@code *})</li>
 *   <li>{@code #id}, {@code .class}</li>
 *   <li>attribute selectors: {@code [href]}, {@code [href="/x"]},
 *       {@code [class~=active]}, {@code [lang|=en]}, {@code [href^=https]},
 *       {@code [href$=pdf]}, {@code [href*=download]}</li>
 *   <li>combinators: descendant ({@code div p}), child ({@code div > p}),
 *       adjacent sibling ({@code h1 + p}), general sibling ({@code h1 ~ p})</li>
 *   <li>selector lists ({@code h1, h2, h3})</li>
 *   <li>pseudo-classes: {@code :first-child}, {@code :last-child},
 *       {@code :only-child}, {@code :empty}, {@code :nth-child(An+B)},
 *       {@code :not(...)} (compound selector argument)</li>
 * </ul>
 *
 * <p>Matching runs right-to-left, the way browsers do: the rightmost
 * compound selector is checked against the candidate element first, then
 * each combinator walks to the ancestor or sibling the next compound must
 * match. A selector that fails to parse throws
 * {@link IllegalArgumentException} with the offending input.
 */
public final class CssSelector {

    private CssSelector() {}

    /** Returns every element under {@code root} matching {@code selector}, in document order. */
    public static List<Element> selectAll(Node root, String selector) {
        List<Selector> selectors = parseSelectorList(selector);
        List<Element> out = new ArrayList<>();
        for (Element element : root.descendants()) {
            for (Selector candidate : selectors) {
                if (candidate.matches(element)) {
                    out.add(element);
                    break;
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Selector model
    // ------------------------------------------------------------------

    /** One selector in a comma-separated list: compounds joined by combinators. */
    static final class Selector {
        /** Compounds left to right; combinators[i] joins compounds[i] to compounds[i+1]. */
        private final List<Compound> compounds;
        private final List<Character> combinators; // ' ', '>', '+', '~'

        Selector(List<Compound> compounds, List<Character> combinators) {
            this.compounds = compounds;
            this.combinators = combinators;
        }

        boolean matches(Element element) {
            return matchFrom(element, compounds.size() - 1);
        }

        private boolean matchFrom(Element element, int index) {
            if (!compounds.get(index).matches(element)) {
                return false;
            }
            if (index == 0) {
                return true;
            }
            char combinator = combinators.get(index - 1);
            return switch (combinator) {
                case ' ' -> {
                    Element ancestor = parentElement(element);
                    boolean found = false;
                    while (ancestor != null && !found) {
                        if (matchFrom(ancestor, index - 1)) {
                            found = true;
                        } else {
                            ancestor = parentElement(ancestor);
                        }
                    }
                    yield found;
                }
                case '>' -> {
                    Element parent = parentElement(element);
                    yield parent != null && matchFrom(parent, index - 1);
                }
                case '+' -> {
                    Element prev = element.previousElementSibling();
                    yield prev != null && matchFrom(prev, index - 1);
                }
                case '~' -> {
                    Element prev = element.previousElementSibling();
                    boolean found = false;
                    while (prev != null && !found) {
                        if (matchFrom(prev, index - 1)) {
                            found = true;
                        } else {
                            prev = prev.previousElementSibling();
                        }
                    }
                    yield found;
                }
                default -> false;
            };
        }

        private static Element parentElement(Element element) {
            return element.parent() instanceof Element parent ? parent : null;
        }
    }

    /** One compound selector: an optional type plus id/class/attribute/pseudo conditions. */
    static final class Compound {
        private String tag; // null = any tag, "*" = any tag
        private String id;
        private final List<String> classes = new ArrayList<>();
        private final List<AttrCondition> attrs = new ArrayList<>();
        private final List<Pseudo> pseudos = new ArrayList<>();

        boolean matches(Element element) {
            if (tag != null && !"*".equals(tag) && !element.tagName().equals(tag)) {
                return false;
            }
            if (id != null && !id.equals(element.id())) {
                return false;
            }
            for (String cls : classes) {
                if (!element.hasClass(cls)) {
                    return false;
                }
            }
            for (AttrCondition attr : attrs) {
                if (!attr.matches(element)) {
                    return false;
                }
            }
            for (Pseudo pseudo : pseudos) {
                if (!pseudo.matches(element)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** One [attr op value] condition. */
    static final class AttrCondition {
        private final String name;
        private final String op; // null = presence only
        private final String value;

        AttrCondition(String name, String op, String value) {
            this.name = name;
            this.op = op;
            this.value = value;
        }

        boolean matches(Element element) {
            String actual = element.attr(name);
            if (op == null) {
                return actual != null;
            }
            if (actual == null) {
                return false;
            }
            return switch (op) {
                case "=" -> actual.equals(value);
                case "~=" -> List.of(actual.split("\\s+")).contains(value);
                case "|=" -> actual.equals(value) || actual.startsWith(value + "-");
                case "^=" -> actual.startsWith(value);
                case "$=" -> actual.endsWith(value);
                case "*=" -> actual.contains(value);
                default -> false;
            };
        }
    }

    /** One :pseudo-class condition. */
    static final class Pseudo {
        private final String name;
        private final String arg; // for :nth-child(...) and :not(...)

        Pseudo(String name, String arg) {
            this.name = name;
            this.arg = arg;
        }

        boolean matches(Element element) {
            return switch (name) {
                case "first-child" -> element.previousElementSibling() == null;
                case "last-child" -> element.nextElementSibling() == null;
                case "only-child" -> element.previousElementSibling() == null
                        && element.nextElementSibling() == null;
                case "empty" -> element.children().stream()
                        .noneMatch(child -> child instanceof Element
                                || (child instanceof Dom.TextNode text && !text.text().isEmpty()));
                case "nth-child" -> nthMatches(element.elementIndex(), arg);
                case "not" -> {
                    Compound inner = parseCompound(new Parser(arg));
                    yield !inner.matches(element);
                }
                default -> throw new IllegalArgumentException("unsupported pseudo-class :" + name);
            };
        }

        /** Evaluates An+B against a 1-based sibling index. */
        private static boolean nthMatches(int index, String arg) {
            String expr = arg == null ? "" : arg.trim().toLowerCase();
            if (expr.equals("odd")) {
                return index % 2 == 1;
            }
            if (expr.equals("even")) {
                return index % 2 == 0;
            }
            int nPos = expr.indexOf('n');
            if (nPos < 0) {
                try {
                    return index == Integer.parseInt(expr);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("bad :nth-child() argument: " + arg);
                }
            }
            String aPart = expr.substring(0, nPos).trim();
            int a = switch (aPart) {
                case "", "+" -> 1;
                case "-" -> -1;
                default -> parseNthInt(aPart);
            };
            String bPart = expr.substring(nPos + 1).trim();
            int b = bPart.isEmpty() ? 0 : parseNthInt(bPart);
            int diff = index - b;
            if (a == 0) {
                return diff == 0;
            }
            return diff % a == 0 && diff / a >= 0;
        }

        private static int parseNthInt(String s) {
            try {
                return Integer.parseInt(s.replace("+", ""));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("bad :nth-child() argument: " + s);
            }
        }
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    static List<Selector> parseSelectorList(String input) {
        List<Selector> out = new ArrayList<>();
        for (String part : splitTopLevel(input, ',')) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("empty selector in list: " + input);
            }
            out.add(parseSelector(new Parser(trimmed)));
        }
        return out;
    }

    private static Selector parseSelector(Parser p) {
        List<Compound> compounds = new ArrayList<>();
        List<Character> combinators = new ArrayList<>();
        compounds.add(parseCompound(p));
        while (true) {
            p.skipWhitespace();
            if (p.atEnd()) {
                break;
            }
            char combinator = ' ';
            char c = p.peek();
            if (c == '>' || c == '+' || c == '~') {
                combinator = c;
                p.advance();
                p.skipWhitespace();
            }
            if (p.atEnd()) {
                throw new IllegalArgumentException("selector ends with a combinator: " + p.input);
            }
            combinators.add(combinator);
            compounds.add(parseCompound(p));
        }
        return new Selector(compounds, combinators);
    }

    private static Compound parseCompound(Parser p) {
        Compound compound = new Compound();
        // Optional type selector or universal selector.
        if (p.peek() == '*') {
            compound.tag = "*";
            p.advance();
        } else if (isNameStart(p.peek())) {
            compound.tag = p.readName().toLowerCase();
        }
        while (!p.atEnd()) {
            char c = p.peek();
            if (c == '#') {
                p.advance();
                compound.id = p.readName();
            } else if (c == '.') {
                p.advance();
                compound.classes.add(p.readName());
            } else if (c == '[') {
                compound.attrs.add(parseAttr(p));
            } else if (c == ':') {
                compound.pseudos.add(parsePseudo(p));
            } else {
                break;
            }
        }
        if (compound.tag == null && compound.id == null && compound.classes.isEmpty()
                && compound.attrs.isEmpty() && compound.pseudos.isEmpty()) {
            throw new IllegalArgumentException("empty compound selector in: " + p.input);
        }
        return compound;
    }

    private static AttrCondition parseAttr(Parser p) {
        p.expect('[');
        p.skipWhitespace();
        String name = p.readName().toLowerCase();
        p.skipWhitespace();
        if (p.peek() == ']') {
            p.advance();
            return new AttrCondition(name, null, null);
        }
        StringBuilder op = new StringBuilder();
        while (!p.atEnd() && "~|^$*".indexOf(p.peek()) >= 0) {
            op.append(p.peek());
            p.advance();
        }
        if (!p.expect('=')) {
            throw new IllegalArgumentException("bad attribute selector near: " + p.remaining());
        }
        p.skipWhitespace();
        String value = p.readStringOrName();
        p.skipWhitespace();
        p.expect(']');
        return new AttrCondition(name, op + "=", value);
    }

    private static Pseudo parsePseudo(Parser p) {
        p.expect(':');
        if (p.peek() == ':') {
            throw new IllegalArgumentException("pseudo-elements are not supported: " + p.remaining());
        }
        String name = p.readName().toLowerCase();
        String arg = null;
        if (p.peek() == '(') {
            p.advance();
            int depth = 1;
            int start = p.pos;
            while (!p.atEnd() && depth > 0) {
                char c = p.peek();
                if (c == '(') depth++;
                if (c == ')') depth--;
                if (depth > 0) p.advance();
            }
            if (depth != 0) {
                throw new IllegalArgumentException("unbalanced parentheses in :" + name);
            }
            arg = p.input.substring(start, p.pos);
            p.advance(); // consume ')'
        }
        return new Pseudo(name, arg);
    }

    /** Splits on a delimiter that is not inside [], () or quotes. */
    private static List<String> splitTopLevel(String input, char delimiter) {
        List<String> parts = new ArrayList<>();
        int depthBracket = 0, depthParen = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[') {
                depthBracket++;
            } else if (c == ']') {
                depthBracket--;
            } else if (c == '(') {
                depthParen++;
            } else if (c == ')') {
                depthParen--;
            } else if (c == delimiter && depthBracket == 0 && depthParen == 0) {
                parts.add(input.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(input.substring(start));
        return parts;
    }

    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '-';
    }

    /** Tiny cursor over a selector string. */
    static final class Parser {
        final String input;
        int pos;

        Parser(String input) {
            this.input = input;
        }

        boolean atEnd() { return pos >= input.length(); }
        char peek() { return atEnd() ? 0 : input.charAt(pos); }
        void advance() { pos++; }
        String remaining() { return input.substring(pos); }

        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(peek())) {
                pos++;
            }
        }

        boolean expect(char c) {
            if (peek() == c) {
                pos++;
                return true;
            }
            return false;
        }

        String readName() {
            int start = pos;
            while (!atEnd()) {
                char c = peek();
                if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c >= 0x80) {
                    pos++;
                } else {
                    break;
                }
            }
            if (pos == start) {
                throw new IllegalArgumentException("expected a name at: " + remaining());
            }
            return input.substring(start, pos);
        }

        String readStringOrName() {
            if (peek() == '"' || peek() == '\'') {
                char quote = peek();
                advance();
                int start = pos;
                while (!atEnd() && peek() != quote) {
                    pos++;
                }
                if (atEnd()) {
                    throw new IllegalArgumentException("unterminated string in selector: " + input);
                }
                String value = input.substring(start, pos);
                advance();
                return value;
            }
            // Unquoted value: read up to whitespace or ']' (lenient on purpose —
            // selectors like [href$=.pdf] are common even though ".pdf" is not
            // a strict CSS identifier).
            int start = pos;
            while (!atEnd() && !Character.isWhitespace(peek()) && peek() != ']') {
                pos++;
            }
            if (pos == start) {
                throw new IllegalArgumentException("expected a value at: " + remaining());
            }
            return input.substring(start, pos);
        }
    }
}
