package com.subhanmalik.html;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single token produced by {@link Tokenizer}.
 *
 * <p>Tokens are the raw lexical units of an HTML document: start tags, end
 * tags, text runs, comments and the doctype. The tree builder
 * ({@link HtmlParser}) consumes them and decides how they nest.
 */
public final class Token {

    public enum Type { START_TAG, END_TAG, TEXT, COMMENT, DOCTYPE, EOF }

    private final Type type;
    private final String name;   // tag name for tags, null otherwise
    private final String data;   // text / comment body / doctype, null for tags
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private boolean selfClosing;

    private Token(Type type, String name, String data) {
        this.type = type;
        this.name = name;
        this.data = data;
    }

    public static Token startTag(String name) {
        return new Token(Type.START_TAG, name, null);
    }

    public static Token endTag(String name) {
        return new Token(Type.END_TAG, name, null);
    }

    public static Token text(String data) {
        return new Token(Type.TEXT, null, data);
    }

    public static Token comment(String data) {
        return new Token(Type.COMMENT, null, data);
    }

    public static Token doctype(String data) {
        return new Token(Type.DOCTYPE, null, data);
    }

    public static Token eof() {
        return new Token(Type.EOF, null, null);
    }

    public Type type() { return type; }
    public String name() { return name; }
    public String data() { return data; }
    public Map<String, String> attributes() { return attributes; }
    public boolean selfClosing() { return selfClosing; }

    public void setSelfClosing(boolean selfClosing) {
        this.selfClosing = selfClosing;
    }

    public void setAttribute(String name, String value) {
        attributes.put(name, value);
    }

    @Override
    public String toString() {
        return switch (type) {
            case START_TAG -> "<" + name + attributes + (selfClosing ? " /" : "") + ">";
            case END_TAG -> "</" + name + ">";
            case TEXT -> "TEXT[" + data + "]";
            case COMMENT -> "<!--" + data + "-->";
            case DOCTYPE -> "<!DOCTYPE " + data + ">";
            case EOF -> "EOF";
        };
    }
}
