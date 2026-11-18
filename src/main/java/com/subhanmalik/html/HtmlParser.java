package com.subhanmalik.html;

import com.subhanmalik.html.Dom.Document;
import com.subhanmalik.html.Dom.Element;
import com.subhanmalik.html.Dom.Node;
import com.subhanmalik.html.Dom.TextNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;

/**
 * Turns the token stream from {@link Tokenizer} into a DOM tree.
 *
 * <p>Real-world HTML is sloppy — unclosed tags, stray end tags, block
 * elements opened inside a paragraph — so the builder recovers the way
 * browsers do, with a small, explicit rule set:
 * <ul>
 *   <li>void elements ({@code br}, {@code img}, ...) never go on the stack</li>
 *   <li>opening a block element implicitly closes an open {@code <p>};
 *       {@code <li>} closes a previous {@code <li>}, {@code <td>} closes a
 *       previous {@code <td>}, and so on (see {@link #IMPLIED_CLOSES})</li>
 *   <li>a mismatched end tag pops everything up to the matching open
 *       element; an end tag with no open element is ignored</li>
 *   <li>anything left open at end-of-input is closed implicitly</li>
 * </ul>
 *
 * <p>One deliberate simplification: the parser does <em>not</em> synthesize
 * {@code <html>}, {@code <head>} or {@code <body>} wrappers. The tree
 * mirrors the source document, which keeps {@code outerHtml()} round-trips
 * predictable for fragments as well as full pages.
 */
public final class HtmlParser {

    /** Elements that never have children or an end tag. */
    public static final Set<String> VOID_ELEMENTS = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr");

    /** Elements that bound the search for an element to implicitly close. */
    private static final Set<String> SCOPE_BOUNDARIES = Set.of(
            "article", "aside", "blockquote", "div", "dl", "fieldset", "figure",
            "footer", "form", "header", "li", "main", "nav", "section", "table",
            "td", "th", "tr", "ul", "ol", "body", "html", "button", "caption",
            "details", "figcaption", "h1", "h2", "h3", "h4", "h5", "h6");

    /**
     * Implied-close rules, keyed by the element being opened. Opening one of
     * these elements closes an open element named in the value's target set —
     * for example opening a {@code <div>} closes an open {@code <p>}, and
     * opening a {@code <li>} closes an open {@code <li>}. This mirrors the
     * HTML spec's "a start tag whose tag name is ..." rules in cut-down form.
     */
    private static final Map<String, CloseRule> IMPLIED_CLOSES = Map.ofEntries(
            // Block-level elements close an open <p>.
            Map.entry("address", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("article", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("aside", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("blockquote", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("details", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("div", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("dl", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("fieldset", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("figcaption", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("figure", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("footer", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("form", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("h1", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("h2", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("h3", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("h4", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("h5", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("h6", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("header", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("hgroup", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("hr", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("main", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("menu", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("nav", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("ol", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("p", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("pre", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("section", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("table", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            Map.entry("ul", new CloseRule(Set.of("p"), SCOPE_BOUNDARIES)),
            // Same-tag closes.
            Map.entry("li", new CloseRule(Set.of("li"), Set.of("ul", "ol"))),
            Map.entry("dt", new CloseRule(Set.of("dt", "dd"), Set.of("dl"))),
            Map.entry("dd", new CloseRule(Set.of("dt", "dd"), Set.of("dl"))),
            Map.entry("option", new CloseRule(Set.of("option", "optgroup"), Set.of("select"))),
            Map.entry("optgroup", new CloseRule(Set.of("optgroup"), Set.of("select"))),
            Map.entry("tr", new CloseRule(Set.of("tr"), Set.of("table"))),
            Map.entry("td", new CloseRule(Set.of("td", "th"), Set.of("table"))),
            Map.entry("th", new CloseRule(Set.of("td", "th"), Set.of("table"))),
            Map.entry("thead", new CloseRule(Set.of("tbody", "tfoot"), Set.of("table"))),
            Map.entry("tbody", new CloseRule(Set.of("tbody", "tfoot"), Set.of("table"))),
            Map.entry("a", new CloseRule(Set.of("a"), Set.of())),
            Map.entry("button", new CloseRule(Set.of("button"), Set.of())),
            Map.entry("nobr", new CloseRule(Set.of("nobr"), Set.of()))
    );

    /** One implied-close rule: which open tags it closes, and where the search stops. */
    private record CloseRule(Set<String> closes, Set<String> boundaries) {}

    private HtmlParser() {}

    /** Parses {@code html} into a {@link Document}. Never returns null. */
    public static Document parse(String html) {
        Document document = new Document();
        Deque<Element> stack = new ArrayDeque<>();
        Node current = document;

        Tokenizer tokenizer = new Tokenizer(html);
        while (true) {
            Token token = tokenizer.nextToken();
            if (token.type() == Token.Type.EOF) {
                break;
            }
            switch (token.type()) {
                case TEXT -> {
                    if (!token.data().isEmpty()) {
                        current.appendChild(new TextNode(token.data()));
                    }
                }
                case COMMENT -> current.appendChild(new Dom.CommentNode(token.data()));
                case DOCTYPE -> { /* doctype carries no tree structure; ignore */ }
                case START_TAG -> current = openElement(document, stack, current, token);
                case END_TAG -> current = closeElement(document, stack, current, token.name());
                default -> { /* no-op */ }
            }
        }
        return document;
    }

    private static Node openElement(Document document, Deque<Element> stack,
                                    Node current, Token token) {
        String tag = token.name();
        if (tag.isEmpty()) {
            return current;
        }
        // Implied closes may pop the stack; the new element belongs under
        // whatever is open now, not under the element we were inside before.
        current = applyImpliedCloses(document, stack, tag);

        Element element = new Element(tag);
        for (Map.Entry<String, String> attr : token.attributes().entrySet()) {
            element.setAttr(attr.getKey(), attr.getValue());
        }
        current.appendChild(element);

        if (VOID_ELEMENTS.contains(tag) || token.selfClosing()) {
            return current; // void / self-closing elements never open a scope
        }
        stack.push(element);
        return element;
    }

    /**
     * Applies the implied-close rule for {@code tag}: searches the stack from
     * the top for an open element the new tag closes, stopping at a scope
     * boundary, and pops everything down to and including it. Returns the
     * node the new element should be appended under.
     */
    private static Node applyImpliedCloses(Document document, Deque<Element> stack, String tag) {
        CloseRule rule = IMPLIED_CLOSES.get(tag);
        if (rule == null || stack.isEmpty()) {
            return stack.isEmpty() ? document : stack.peek();
        }
        int depth = 0;
        boolean found = false;
        for (Element open : stack) {
            if (rule.boundaries().contains(open.tagName())) {
                break;
            }
            if (rule.closes().contains(open.tagName())) {
                found = true;
                break;
            }
            depth++;
        }
        if (!found) {
            return stack.peek();
        }
        for (int i = 0; i <= depth; i++) {
            stack.pop();
        }
        return stack.isEmpty() ? document : stack.peek();
    }

    private static Node closeElement(Document document, Deque<Element> stack,
                                     Node current, String tag) {
        if (tag.isEmpty() || stack.isEmpty()) {
            return current;
        }
        // Walk the stack for the matching open element; pop everything above
        // it too (their end tags were simply missing).
        Deque<Element> popped = new ArrayDeque<>();
        boolean found = false;
        while (!stack.isEmpty()) {
            Element element = stack.pop();
            popped.push(element);
            if (element.tagName().equals(tag)) {
                found = true;
                break;
            }
        }
        if (!found) {
            // Stray end tag — restore the stack untouched.
            while (!popped.isEmpty()) {
                stack.push(popped.pop());
            }
            return current;
        }
        return stack.isEmpty() ? document : stack.peek();
    }
}
