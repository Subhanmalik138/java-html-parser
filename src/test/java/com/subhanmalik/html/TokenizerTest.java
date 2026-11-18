package com.subhanmalik.html;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenizerTest {

    private List<Token> tokenize(String input) {
        Tokenizer tokenizer = new Tokenizer(input);
        List<Token> tokens = new ArrayList<>();
        Token token;
        while ((token = tokenizer.nextToken()).type() != Token.Type.EOF) {
            tokens.add(token);
        }
        return tokens;
    }

    @Test
    void textOnly() {
        List<Token> tokens = tokenize("hello world");
        assertEquals(1, tokens.size());
        assertEquals(Token.Type.TEXT, tokens.get(0).type());
        assertEquals("hello world", tokens.get(0).data());
    }

    @Test
    void simpleStartAndEndTags() {
        List<Token> tokens = tokenize("<p>hi</p>");
        assertEquals(3, tokens.size());
        assertEquals(Token.Type.START_TAG, tokens.get(0).type());
        assertEquals("p", tokens.get(0).name());
        assertEquals("hi", tokens.get(1).data());
        assertEquals(Token.Type.END_TAG, tokens.get(2).type());
        assertEquals("p", tokens.get(2).name());
    }

    @Test
    void tagNamesAreLowerCased() {
        List<Token> tokens = tokenize("<DIV CLASS=\"Box\">x</DIV>");
        assertEquals("div", tokens.get(0).name());
        assertEquals("Box", tokens.get(0).attributes().get("class"));
    }

    @Test
    void doubleQuotedSingleQuotedAndUnquotedAttributes() {
        List<Token> tokens = tokenize("<a href=\"/x\" title='Hi there' rel=nofollow>x</a>");
        assertEquals("/x", tokens.get(0).attributes().get("href"));
        assertEquals("Hi there", tokens.get(0).attributes().get("title"));
        assertEquals("nofollow", tokens.get(0).attributes().get("rel"));
    }

    @Test
    void booleanAttributesHaveEmptyValue() {
        List<Token> tokens = tokenize("<input disabled required>");
        assertTrue(tokens.get(0).attributes().containsKey("disabled"));
        assertEquals("", tokens.get(0).attributes().get("disabled"));
        assertTrue(tokens.get(0).attributes().containsKey("required"));
    }

    @Test
    void selfClosingTag() {
        List<Token> tokens = tokenize("<br/><img src=\"a.png\" />");
        assertTrue(tokens.get(0).selfClosing());
        assertEquals("br", tokens.get(0).name());
        assertTrue(tokens.get(1).selfClosing());
        assertEquals("a.png", tokens.get(1).attributes().get("src"));
    }

    @Test
    void commentAndDoctype() {
        List<Token> tokens = tokenize("<!DOCTYPE html><!-- note --><p>x</p>");
        assertEquals(Token.Type.DOCTYPE, tokens.get(0).type());
        assertEquals(Token.Type.COMMENT, tokens.get(1).type());
        assertEquals(" note ", tokens.get(1).data());
    }

    @Test
    void namedNumericAndHexEntities() {
        List<Token> tokens = tokenize("Fish &amp; Chips &#169; &#xA9; &lt;tag&gt;");
        assertEquals("Fish & Chips © © <tag>", tokens.get(0).data());
    }

    @Test
    void unknownEntityIsLeftLiteral() {
        List<Token> tokens = tokenize("a &nosuchthing; b");
        assertEquals("a &nosuchthing; b", tokens.get(0).data());
    }

    @Test
    void entitiesInAttributeValues() {
        List<Token> tokens = tokenize("<a title=\"Tom &amp; Jerry\">x</a>");
        assertEquals("Tom & Jerry", tokens.get(0).attributes().get("title"));
    }

    @Test
    void strayLessThanIsLiteralText() {
        List<Token> tokens = tokenize("a < b and 1 < 2");
        assertEquals(1, tokens.size());
        assertEquals("a < b and 1 < 2", tokens.get(0).data());
    }

    @Test
    void scriptBodyIsNotTokenizedAsMarkup() {
        List<Token> tokens = tokenize("<script>if (a < b) { s = \"<div>\"; }</script><p>after</p>");
        assertEquals(Token.Type.START_TAG, tokens.get(0).type());
        assertEquals("script", tokens.get(0).name());
        assertEquals(Token.Type.TEXT, tokens.get(1).type());
        assertEquals("if (a < b) { s = \"<div>\"; }", tokens.get(1).data());
        assertEquals(Token.Type.END_TAG, tokens.get(2).type());
        assertEquals("p", tokens.get(3).name());
    }

    @Test
    void styleBodyIsNotEntityDecoded() {
        List<Token> tokens = tokenize("<style>a::after { content: \"&amp;\"; }</style>");
        assertEquals("a::after { content: \"&amp;\"; }", tokens.get(1).data());
    }

    @Test
    void textareaBodyIsEntityDecoded() {
        List<Token> tokens = tokenize("<textarea>Tom &amp; Jerry</textarea>");
        assertEquals("Tom & Jerry", tokens.get(1).data());
    }

    @Test
    void unterminatedCommentRunsToEndOfInput() {
        List<Token> tokens = tokenize("<!-- never closed");
        assertEquals(1, tokens.size());
        assertEquals(Token.Type.COMMENT, tokens.get(0).type());
        assertEquals(" never closed", tokens.get(0).data());
    }
}
