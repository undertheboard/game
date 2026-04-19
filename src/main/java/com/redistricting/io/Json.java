package com.redistricting.io;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny, dependency-free JSON parser. Supports objects, arrays, strings,
 * numbers (parsed as {@link Double}), booleans, and null. Throws
 * {@link JsonException} on malformed input.
 */
public final class Json {

    public static final class JsonException extends RuntimeException {
        public JsonException(String msg) { super(msg); }
    }

    private final String src;
    private int pos;

    private Json(String src) { this.src = src; }

    /** Parse a JSON document. Returns Map, List, String, Double, Boolean, or null. */
    public static Object parse(String text) {
        Json j = new Json(text);
        j.skipWs();
        Object result = j.readValue();
        j.skipWs();
        if (j.pos != j.src.length()) {
            throw new JsonException("unexpected trailing content at position " + j.pos);
        }
        return result;
    }

    private Object readValue() {
        skipWs();
        if (pos >= src.length()) throw new JsonException("unexpected end of input");
        char c = src.charAt(pos);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't', 'f' -> readBoolean();
            case 'n' -> readNull();
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        expect('{');
        Map<String, Object> out = new LinkedHashMap<>();
        skipWs();
        if (peek() == '}') { pos++; return out; }
        while (true) {
            skipWs();
            String key = readString();
            skipWs();
            expect(':');
            Object value = readValue();
            out.put(key, value);
            skipWs();
            char c = next();
            if (c == ',') continue;
            if (c == '}') return out;
            throw new JsonException("expected ',' or '}' at position " + (pos - 1));
        }
    }

    private List<Object> readArray() {
        expect('[');
        List<Object> out = new ArrayList<>();
        skipWs();
        if (peek() == ']') { pos++; return out; }
        while (true) {
            out.add(readValue());
            skipWs();
            char c = next();
            if (c == ',') continue;
            if (c == ']') return out;
            throw new JsonException("expected ',' or ']' at position " + (pos - 1));
        }
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (pos >= src.length()) throw new JsonException("bad escape");
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"', '\\', '/' -> sb.append(esc);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > src.length()) throw new JsonException("bad \\u escape");
                        sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new JsonException("bad escape \\" + esc);
                }
            } else {
                sb.append(c);
            }
        }
        throw new JsonException("unterminated string");
    }

    private Double readNumber() {
        int start = pos;
        if (peek() == '-') pos++;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                pos++;
            } else break;
        }
        if (start == pos) throw new JsonException("expected number at position " + pos);
        try {
            return Double.parseDouble(src.substring(start, pos));
        } catch (NumberFormatException e) {
            throw new JsonException("bad number: " + src.substring(start, pos));
        }
    }

    private Boolean readBoolean() {
        if (src.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
        if (src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
        throw new JsonException("expected boolean at position " + pos);
    }

    private Object readNull() {
        if (src.startsWith("null", pos)) { pos += 4; return null; }
        throw new JsonException("expected null at position " + pos);
    }

    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private char peek() {
        if (pos >= src.length()) throw new JsonException("unexpected end of input");
        return src.charAt(pos);
    }

    private char next() {
        if (pos >= src.length()) throw new JsonException("unexpected end of input");
        return src.charAt(pos++);
    }

    private void expect(char c) {
        if (pos >= src.length() || src.charAt(pos) != c) {
            throw new JsonException("expected '" + c + "' at position " + pos);
        }
        pos++;
    }
}
