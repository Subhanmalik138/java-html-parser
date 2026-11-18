package com.subhanmalik.html;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The DOM produced by {@link HtmlParser}: a small tree of nodes.
 *
 * <p>Only three node kinds exist — {@link Document}, {@link Element} and
 * {@link TextNode} (plus {@link CommentNode}). The API is deliberately
 * close to the browser DOM surface that people actually reach for:
 * {@code textContent}, {@code outerHTML}, {@code querySelector(All)},
 * {@code getElementById}, {@code getElementsByTagName} and
 * {@code getElementsByClassName}.
 */
public final class Dom {

    private Dom() {}

    // ------------------------------------------------------------------
    // Base node
    // ------------------------------------------------------------------

    public abstract static class Node {
        private Node parent;
        private final List<Node> children = new ArrayList<>();

        public Node parent() { return parent; }
        public List<Node> children() { return children; }

        void setParent(Node parent) { this.parent = parent; }

        public void appendChild(Node child) {
            child.setParent(this);
            children.add(child);
        }

        /** Child elements only (no text or comment nodes). */
        public List<Element> childElements() {
            List<Element> out = new ArrayList<>();
            for (Node child : children) {
                if (child instanceof Element element) {
                    out.add(element);
                }
            }
            return out;
        }

        /** All descendant elements, in document order. */
        public List<Element> descendants() {
            List<Element> out = new ArrayList<>();
            collectElements(this, out);
            return out;
        }

        private static void collectElements(Node node, List<Element> out) {
            for (Node child : node.children) {
                if (child instanceof Element element) {
                    out.add(element);
                    collectElements(element, out);
                } else {
                    collectElements(child, out);
                }
            }
        }

        /** Concatenated text of this node and all its descendants. */
        public String textContent() {
            StringBuilder out = new StringBuilder();
            appendText(this, out);
            return out.toString();
        }

        private static void appendText(Node node, StringBuilder out) {
            if (node instanceof TextNode text) {
                out.append(text.text());
            } else {
                for (Node child : node.children) {
                    appendText(child, out);
                }
            }
        }

        public List<Element> querySelectorAll(String selector) {
            return CssSelector.selectAll(this, selector);
        }

        public Element querySelector(String selector) {
            List<Element> found = querySelectorAll(selector);
            return found.isEmpty() ? null : found.get(0);
        }

        public Element getElementById(String id) {
            for (Element element : descendants()) {
                if (id.equals(element.attr("id"))) {
                    return element;
                }
            }
            return null;
        }

        public List<Element> getElementsByTagName(String tagName) {
            String wanted = tagName.toLowerCase();
            List<Element> out = new ArrayList<>();
            for (Element element : descendants()) {
                if ("*".equals(wanted) || element.tagName().equals(wanted)) {
                    out.add(element);
                }
            }
            return out;
        }

        public List<Element> getElementsByClassName(String className) {
            List<Element> out = new ArrayList<>();
            for (Element element : descendants()) {
                if (element.hasClass(className)) {
                    out.add(element);
                }
            }
            return out;
        }

        public abstract String outerHtml();
    }

    // ------------------------------------------------------------------
    // Document
    // ------------------------------------------------------------------

    /** The root of a parsed document. Serializes as its children, with no wrapper. */
    public static final class Document extends Node {
        @Override
        public String outerHtml() {
            StringBuilder out = new StringBuilder();
            for (Node child : children()) {
                out.append(child.outerHtml());
            }
            return out.toString();
        }

        /** The first {@code <title>} element's text, or null. */
        public String title() {
            Element title = querySelector("title");
            return title == null ? null : title.textContent();
        }
    }

    // ------------------------------------------------------------------
    // Element
    // ------------------------------------------------------------------

    public static final class Element extends Node {
        private final String tagName;
        private final Map<String, String> attributes = new LinkedHashMap<>();

        public Element(String tagName) {
            this.tagName = tagName.toLowerCase();
        }

        public String tagName() { return tagName; }
        public Map<String, String> attributes() { return attributes; }

        public String attr(String name) {
            return attributes.get(name.toLowerCase());
        }

        public boolean hasAttr(String name) {
            return attributes.containsKey(name.toLowerCase());
        }

        public void setAttr(String name, String value) {
            attributes.put(name.toLowerCase(), value);
        }

        /** The element's {@code id} attribute, or null. */
        public String id() { return attr("id"); }

        /** The element's classes, split on whitespace. */
        public Set<String> classes() {
            String value = attr("class");
            if (value == null || value.isBlank()) {
                return Set.of();
            }
            return Set.of(value.trim().split("\\s+"));
        }

        public boolean hasClass(String className) {
            return classes().contains(className);
        }

        /** Next sibling that is an element, or null. */
        public Element nextElementSibling() {
            return siblingElement(1);
        }

        /** Previous sibling that is an element, or null. */
        public Element previousElementSibling() {
            return siblingElement(-1);
        }

        private Element siblingElement(int step) {
            Node parentNode = parent();
            if (parentNode == null) {
                return null;
            }
            List<Node> siblings = parentNode.children();
            int index = siblings.indexOf(this) + step;
            while (index >= 0 && index < siblings.size()) {
                if (siblings.get(index) instanceof Element element) {
                    return element;
                }
                index += step;
            }
            return null;
        }

        /** 1-based position among sibling elements. */
        public int elementIndex() {
            Node parentNode = parent();
            if (parentNode == null) {
                return 1;
            }
            int index = 0;
            for (Node sibling : parentNode.children()) {
                if (sibling instanceof Element) {
                    index++;
                    if (sibling == this) {
                        return index;
                    }
                }
            }
            return index;
        }

        /** Serialized inner HTML (children only). */
        public String innerHtml() {
            StringBuilder out = new StringBuilder();
            for (Node child : children()) {
                out.append(child.outerHtml());
            }
            return out.toString();
        }

        @Override
        public String outerHtml() {
            StringBuilder out = new StringBuilder();
            out.append('<').append(tagName);
            for (Map.Entry<String, String> attr : attributes.entrySet()) {
                out.append(' ').append(attr.getKey());
                if (!attr.getValue().isEmpty()) {
                    out.append("=\"").append(escapeAttr(attr.getValue())).append('"');
                }
            }
            out.append('>');
            if (HtmlParser.VOID_ELEMENTS.contains(tagName)) {
                return out.toString();
            }
            out.append(innerHtml());
            out.append("</").append(tagName).append('>');
            return out.toString();
        }

        @Override
        public String toString() {
            return outerHtml();
        }
    }

    // ------------------------------------------------------------------
    // Text and comment nodes
    // ------------------------------------------------------------------

    public static final class TextNode extends Node {
        private final String text;

        public TextNode(String text) {
            this.text = text;
        }

        public String text() { return text; }

        public boolean isWhitespace() {
            return text.isBlank();
        }

        @Override
        public String outerHtml() {
            return escapeText(text);
        }

        @Override
        public String toString() {
            return text;
        }
    }

    public static final class CommentNode extends Node {
        private final String data;

        public CommentNode(String data) {
            this.data = data;
        }

        public String data() { return data; }

        @Override
        public String outerHtml() {
            return "<!--" + data + "-->";
        }
    }

    // ------------------------------------------------------------------
    // Serialization helpers
    // ------------------------------------------------------------------

    static String escapeText(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

    static String escapeAttr(String value) {
        return escapeText(value).replace("\"", "&quot;");
    }
}
