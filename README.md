# MiniHTML — a hand-rolled HTML parser and CSS selector engine in Java

A zero-dependency HTML parser for Java: a tokenizer, a DOM tree builder with
browser-style error recovery, and a CSS selector engine — written from scratch
to show how the pieces of a real browser parser fit together.

## What it does

- **Tokenizes HTML** — start/end/self-closing tags, double-quoted,
  single-quoted, unquoted and boolean attributes, comments, doctype,
  character references (`&amp;`, `&#169;`, `&#xA9;`), and raw-text elements
  (`script`, `style`, `textarea`, `title`) whose contents are not parsed as
  markup.
- **Builds a DOM** — elements, text and comment nodes with `textContent`,
  `outerHTML` / `innerHTML`, `getElementById`, `getElementsByTagName`,
  `getElementsByClassName`.
- **Recovers from sloppy markup the way browsers do** — void elements,
  implied end tags (`<li>` closes `<li>`, a block element closes `<p>`,
  `<td>` closes `<td>`), mismatched end tags that pop to the matching open
  element, stray end tags that are ignored, and unclosed tags at end of input.
- **Queries with CSS selectors** — type, `#id`, `.class`, attribute selectors
  (`[href]`, `[href="/x"]`, `~=`, `|=`, `^=`, `$=`, `*=`), descendant/child/
  adjacent/general-sibling combinators, selector lists, and pseudo-classes
  (`:first-child`, `:last-child`, `:only-child`, `:empty`,
  `:nth-child(An+B)`, `:not(...)`). Matching runs right-to-left, like a
  browser's selector engine.

## Design decisions

1. **Tokenizer and tree builder are separate stages.** The tokenizer only
   knows characters and tokens; all nesting and recovery logic lives in
   `HtmlParser`. That split is what makes each half testable on its own —
   and it mirrors how real parsers are structured.
2. **Implied closes are a data-driven rule table, not ad-hoc `if`s.**
   `HtmlParser.IMPLIED_CLOSES` maps each opening tag to the open tags it
   closes plus the scope boundaries the search must not cross, so adding a
   rule is one table row. (The first version of this table had the mapping
   backwards — opening `<p>` popped its `<article>` parent — and the
   `listItemImplicitlyClosesPreviousListItem` test caught it.)
3. **No synthesized `<html>` / `<head>` / `<body>` wrappers.** The tree
   mirrors the source document, so `outerHtml()` round-trips fragments as
   well as full pages and the output stays predictable.
4. **Selectors match right-to-left.** The rightmost compound is checked
   against the candidate element first; each combinator then walks to the
   ancestor or sibling the next compound must match. This is the same
   strategy browsers use, and it keeps deep-document queries fast.
5. **No dependencies, no build tool required.** Plain `javac` + `java`,
   JDK 17+. The test suite runs on the JUnit Platform console standalone jar.

## Usage

Build:

```bash
./build.sh            # compiles and builds out/html-parser.jar
./build.sh test       # compiles and runs the 46-test JUnit suite
```

Run:

```bash
# Print the DOM as an indented tree
java -jar out/html-parser.jar page.html

# Print the outer HTML of every match
java -jar out/html-parser.jar page.html "article.post h2"

# Print just the text of each match
java -jar out/html-parser.jar page.html "a[href^=https]" --text

# Serialize the parsed document back to HTML
java -jar out/html-parser.jar page.html --html

# Read from stdin (e.g. piped from curl)
curl -s https://example.com | java -jar out/html-parser.jar - "p"
```

Use it as a library:

```java
import com.subhanmalik.html.HtmlParser;
import com.subhanmalik.html.Dom.Document;
import com.subhanmalik.html.Dom.Element;

Document doc = HtmlParser.parse("<main><p class=\"intro\">Hello</p></main>");
System.out.println(doc.querySelector("p.intro").textContent()); // Hello
System.out.println(doc.querySelectorAll("main p").size());      // 1
System.out.println(doc.outerHtml());
```

## Project structure

```
src/main/java/com/subhanmalik/html/
  Token.java        Token types produced by the tokenizer
  Tokenizer.java    Character-level scanner (tags, attributes, entities, raw text)
  Dom.java          Node / Document / Element / TextNode / CommentNode
  HtmlParser.java   Tree builder with implied-close and error-recovery rules
  CssSelector.java  Selector parser and right-to-left matcher
  Main.java         CLI front end
src/test/java/com/subhanmalik/html/
  TokenizerTest.java   (15 tests)
  HtmlParserTest.java  (16 tests)
  CssSelectorTest.java (15 tests)
```

## Tech stack

- Java 17, zero dependencies
- JUnit 5 (test scope only, via the console standalone jar)

## License

MIT — see [LICENSE](LICENSE).
