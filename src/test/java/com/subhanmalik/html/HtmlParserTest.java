package com.subhanmalik.html;

import com.subhanmalik.html.Dom.Document;
import com.subhanmalik.html.Dom.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HtmlParserTest {

    @Test
    void parsesNestedElements() {
        Document doc = HtmlParser.parse("<html><body><div><p>Hello</p></div></body></html>");
        Element div = doc.querySelector("div");
        assertNotNull(div);
        assertEquals("Hello", div.textContent());
        assertEquals("p", div.childElements().get(0).tagName());
    }

    @Test
    void voidElementsHaveNoChildrenAndNoEndTag() {
        Document doc = HtmlParser.parse("<p>a<br>b<img src=\"x.png\">c</p>");
        Element p = doc.querySelector("p");
        assertEquals("abc", p.textContent());
        assertEquals("br", p.childElements().get(0).tagName());
        assertEquals("img", p.childElements().get(1).tagName());
        assertEquals("<p>a<br>b<img src=\"x.png\">c</p>", p.outerHtml());
    }

    @Test
    void blockElementImplicitlyClosesParagraph() {
        Document doc = HtmlParser.parse("<p>first<div>second</div>");
        assertEquals(2, doc.childElements().size());
        assertEquals("p", doc.childElements().get(0).tagName());
        assertEquals("div", doc.childElements().get(1).tagName());
        assertEquals("first", doc.childElements().get(0).textContent());
    }

    @Test
    void listItemImplicitlyClosesPreviousListItem() {
        Document doc = HtmlParser.parse("<ul><li>one<li>two<li>three</ul>");
        Element ul = doc.querySelector("ul");
        assertEquals(3, ul.childElements().size());
        assertEquals("one", ul.childElements().get(0).textContent());
        assertEquals("three", ul.childElements().get(2).textContent());
    }

    @Test
    void mismatchedEndTagPopsToMatchingElement() {
        Document doc = HtmlParser.parse("<div><p>text</div>after");
        Element div = doc.querySelector("div");
        assertNotNull(div);
        assertEquals("text", div.querySelector("p").textContent());
        assertEquals("after", ((Dom.TextNode) doc.children().get(1)).text());
    }

    @Test
    void strayEndTagIsIgnored() {
        Document doc = HtmlParser.parse("<p>hi</span></p>");
        assertEquals("hi", doc.querySelector("p").textContent());
        assertEquals(1, doc.descendants().size());
    }

    @Test
    void unclosedTagsAreClosedAtEndOfInput() {
        Document doc = HtmlParser.parse("<div><p>never closed");
        assertEquals("never closed", doc.querySelector("div").textContent());
    }

    @Test
    void entitiesDecodedInTextContent() {
        Document doc = HtmlParser.parse("<p>Fish &amp; Chips &mdash; 50&#37; off</p>");
        assertEquals("Fish & Chips — 50% off", doc.querySelector("p").textContent());
    }

    @Test
    void commentsArePreservedInTree() {
        Document doc = HtmlParser.parse("<div><!-- note --><p>x</p></div>");
        Element div = doc.querySelector("div");
        assertEquals(2, div.children().size());
        assertTrue(div.children().get(0) instanceof Dom.CommentNode);
        assertEquals(" note ", ((Dom.CommentNode) div.children().get(0)).data());
        assertEquals("x", div.textContent());
    }

    @Test
    void doctypeIsIgnored() {
        Document doc = HtmlParser.parse("<!DOCTYPE html><p>x</p>");
        assertEquals(1, doc.childElements().size());
        assertEquals("p", doc.childElements().get(0).tagName());
    }

    @Test
    void getElementByIdAndByClassName() {
        Document doc = HtmlParser.parse(
                "<div id=\"main\" class=\"wrap\"><span class=\"tag\">a</span><span class=\"tag\">b</span></div>");
        assertEquals("div", doc.getElementById("main").tagName());
        assertEquals(2, doc.getElementsByClassName("tag").size());
        assertNull(doc.getElementById("missing"));
    }

    @Test
    void getElementsByTagName() {
        Document doc = HtmlParser.parse("<div><p>a</p><p>b</p><span>c</span></div>");
        assertEquals(2, doc.getElementsByTagName("p").size());
        assertEquals(4, doc.getElementsByTagName("*").size());
    }

    @Test
    void documentTitle() {
        Document doc = HtmlParser.parse("<html><head><title>My Page</title></head></html>");
        assertEquals("My Page", doc.title());
        assertNull(HtmlParser.parse("<p>no title</p>").title());
    }

    @Test
    void outerHtmlRoundTripsSimpleDocument() {
        String html = "<div class=\"a\"><p>Hello <b>world</b></p></div>";
        assertEquals(html, HtmlParser.parse(html).outerHtml());
    }

    @Test
    void tableCellsCloseEachOther() {
        Document doc = HtmlParser.parse("<table><tr><td>a<td>b</tr></table>");
        Element tr = doc.querySelector("tr");
        assertEquals(2, tr.childElements().size());
        assertEquals("a", tr.childElements().get(0).textContent());
        assertEquals("b", tr.childElements().get(1).textContent());
    }

    @Test
    void emptyInputParsesToEmptyDocument() {
        Document doc = HtmlParser.parse("");
        assertTrue(doc.children().isEmpty());
        assertEquals("", doc.outerHtml());
    }
}
