package com.subhanmalik.html;

import com.subhanmalik.html.Dom.Document;
import com.subhanmalik.html.Dom.Element;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CssSelectorTest {

    private static final String HTML = """
            <main id="app" class="page">
              <article class="post featured" data-kind="tutorial" lang="en-US">
                <h2 class="title">First</h2>
                <p>Intro <a href="https://example.com/guide.pdf">guide</a></p>
                <p class="note">Second paragraph</p>
              </article>
              <article class="post" data-kind="news">
                <h2 class="title">Second</h2>
                <p>News body</p>
              </article>
              <footer><a href="/about">About</a><a href="https://example.com">Home</a></footer>
              <div class="empty"></div>
            </main>
            """;

    private Document doc() {
        return HtmlParser.parse(HTML);
    }

    private List<String> texts(String selector) {
        return doc().querySelectorAll(selector).stream()
                .map(Element::textContent).toList();
    }

    private int count(String selector) {
        return doc().querySelectorAll(selector).size();
    }

    @Test
    void typeSelector() {
        assertEquals(2, count("article"));
        assertEquals(3, count("p"));
    }

    @Test
    void universalSelector() {
        assertEquals(13, count("*"));
    }

    @Test
    void idAndClassSelectors() {
        assertEquals(1, count("#app"));
        assertEquals(2, count(".post"));
        assertEquals(1, count(".featured"));
        assertEquals(2, count("article.post"));
        assertEquals(1, count("article.post.featured"));
    }

    @Test
    void descendantAndChildCombinators() {
        assertEquals(List.of("First", "Second"), texts("article h2"));
        assertEquals(List.of("First", "Second"), texts("article > h2"));
        assertEquals(0, count("main > h2")); // h2 is not a direct child of main
        assertEquals(3, count("main p"));
        assertEquals(2, count("main > article"));
    }

    @Test
    void adjacentAndGeneralSiblingCombinators() {
        assertEquals(List.of("Second paragraph"), texts("p + p"));
        assertEquals(List.of("Intro guide", "Second paragraph", "News body"), texts("h2 ~ p"));
        assertEquals(1, count("article + article"));
        assertEquals(1, count("article ~ article")); // only the second article follows one
        assertEquals(0, count("footer + article"));
    }

    @Test
    void attributePresenceAndExactMatch() {
        assertEquals(2, count("[data-kind]"));
        assertEquals(1, count("[data-kind=tutorial]"));
        assertEquals(1, count("[data-kind=\"news\"]"));
        assertEquals(1, count("[lang|=en]"));
        assertEquals(0, count("[lang|=fr]"));
    }

    @Test
    void attributeSubstringOperators() {
        assertEquals(2, count("a[href^=https]")); // guide link and Home link
        assertEquals(1, count("a[href$=.pdf]"));
        assertEquals(1, count("a[href*=guide]"));
        assertEquals(1, count("article[class~=featured]"));
        assertEquals(2, count("a[href^=\"https://example.com\"]")); // guide link and Home link
    }

    @Test
    void selectorLists() {
        assertEquals(3, count("h2, footer"));
        // Document order: h2 "First", then p.note, then h2 "Second".
        assertEquals(List.of("First", "Second paragraph", "Second"), texts("h2.title, .note"));
    }

    @Test
    void firstLastAndOnlyChild() {
        // Each article's h2 is the first element child of its own article.
        assertEquals(List.of("First", "Second"), texts("h2:first-child"));
        assertEquals(List.of("Second paragraph"), texts("article.post.featured p:last-child"));
        assertEquals(0, count("p:only-child"));
        assertEquals(1, count("footer a:last-child"));
    }

    @Test
    void nthChild() {
        // main's children: article(1), article(2), footer(3), div(4)
        assertEquals(1, count("article:nth-child(1)"));
        assertEquals(1, count("main > :nth-child(2)"));
        assertEquals(2, count("main > :nth-child(odd)"));
        assertEquals(2, count("main > :nth-child(even)"));
        assertEquals(2, count("main > :nth-child(2n+1)"));
        assertEquals(List.of("Second paragraph"), texts("article p:nth-child(3)"));
    }

    @Test
    void emptyPseudoClass() {
        assertEquals(1, count("div:empty"));
        assertEquals(0, count("p:empty"));
    }

    @Test
    void notPseudoClass() {
        assertEquals(1, count("article:not(.featured)"));
        assertEquals(1, count("a:not([href^=https])")); // only the /about link
        assertEquals(2, count("p:not(.note)"));
    }

    @Test
    void querySelectorReturnsFirstMatchOnly() {
        Element first = doc().querySelector("article h2");
        assertEquals("First", first.textContent());
        assertNull(doc().querySelector("h3"));
    }

    @Test
    void invalidSelectorsThrow() {
        assertThrows(IllegalArgumentException.class, () -> doc().querySelectorAll("div >"));
        assertThrows(IllegalArgumentException.class, () -> doc().querySelectorAll("[href"));
        assertThrows(IllegalArgumentException.class, () -> doc().querySelectorAll("p::before"));
        assertThrows(IllegalArgumentException.class, () -> doc().querySelectorAll(":nth-child(x)"));
    }

    @Test
    void combinatorChains() {
        assertEquals(List.of("guide"), texts("main article.post.featured p a[href$=.pdf]"));
        assertEquals(List.of("About"), texts("main > footer > a:first-child"));
    }
}
