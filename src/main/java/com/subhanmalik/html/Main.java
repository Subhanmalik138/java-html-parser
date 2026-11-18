package com.subhanmalik.html;

import com.subhanmalik.html.Dom.Document;
import com.subhanmalik.html.Dom.Element;
import com.subhanmalik.html.Dom.Node;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Command-line front end for the parser.
 *
 * <pre>
 *   html-parser &lt;file.html&gt; [selector] [--text] [--tree] [--html]
 *   html-parser - [selector] ...          # read HTML from stdin
 * </pre>
 *
 * With no selector it prints the DOM as an indented tree. With a selector
 * it prints the {@code outerHTML} of every match (or its text content with
 * {@code --text}). {@code --html} prints the serialized document instead of
 * the tree.
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        if (args.length == 0 || hasFlag(args, "--help") || hasFlag(args, "-h")) {
            printUsage();
            return;
        }
        String source = null;
        String selector = null;
        boolean text = false;
        boolean tree = false;
        boolean html = false;
        for (String arg : args) {
            switch (arg) {
                case "--text" -> text = true;
                case "--tree" -> tree = true;
                case "--html" -> html = true;
                default -> {
                    if (arg.startsWith("--")) {
                        fail("unknown option: " + arg);
                    } else if (source == null) {
                        source = arg;
                    } else if (selector == null) {
                        selector = arg;
                    } else {
                        fail("unexpected argument: " + arg);
                    }
                }
            }
        }
        if (source == null) {
            fail("missing input file (use - for stdin)");
        }

        String input = readInput(source);
        Document document = HtmlParser.parse(input);

        if (selector != null) {
            List<Element> matches;
            try {
                matches = document.querySelectorAll(selector);
            } catch (IllegalArgumentException e) {
                fail("invalid selector: " + e.getMessage());
                return; // unreachable — fail() exits
            }
            for (Element match : matches) {
                System.out.println(text ? match.textContent() : match.outerHtml());
            }
            System.err.println(matches.size() + " match(es)");
            return;
        }

        if (html) {
            System.out.println(document.outerHtml());
        } else {
            printTree(document, 0);
        }
    }

    private static String readInput(String source) {
        try {
            if (source.equals("-")) {
                return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            }
            return Files.readString(Path.of(source), StandardCharsets.UTF_8);
        } catch (IOException e) {
            fail("cannot read " + source + ": " + e.getMessage());
            return ""; // unreachable — fail() exits
        }
    }

    private static void printTree(Node node, int depth) {
        for (Node child : node.children()) {
            printNode(child, depth);
        }
    }

    private static void printNode(Node node, int depth) {
        String indent = "  ".repeat(depth);
        if (node instanceof Element element) {
            StringBuilder line = new StringBuilder(indent).append('<').append(element.tagName());
            element.attributes().forEach((name, value) ->
                    line.append(' ').append(name).append("=\"").append(value).append('"'));
            line.append('>');
            System.out.println(line);
            printTree(element, depth + 1);
        } else if (node instanceof Dom.TextNode textNode) {
            String text = textNode.text().strip();
            if (!text.isEmpty()) {
                System.out.println(indent + "\"" + text + "\"");
            }
        } else if (node instanceof Dom.CommentNode comment) {
            System.out.println(indent + "<!--" + comment.data() + "-->");
        }
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equals(flag)) {
                return true;
            }
        }
        return false;
    }

    private static void printUsage() {
        System.out.println("""
                Usage: html-parser <file.html> [selector] [--text] [--tree] [--html]
                       html-parser - [selector] ...   (read HTML from stdin)

                  <file.html>   HTML file to parse, or - for stdin
                  [selector]    CSS selector; prints outerHTML of each match
                  --text        with a selector, print text content instead
                  --tree        print the DOM as an indented tree (default)
                  --html        print the serialized document

                Examples:
                  html-parser page.html
                  html-parser page.html "article.post h2"
                  html-parser page.html "a[href^=https]" --text
                  curl -s https://example.com | html-parser - "p"
                """);
    }

    private static void fail(String message) {
        System.err.println("html-parser: " + message);
        System.exit(2);
    }
}
