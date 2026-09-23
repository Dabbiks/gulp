package dev.gulp.core.data;

import dev.gulp.api.data.JsonArray;
import dev.gulp.api.data.JsonBoolean;
import dev.gulp.api.data.JsonNull;
import dev.gulp.api.data.JsonNumber;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonParseException;
import dev.gulp.api.data.JsonString;
import dev.gulp.api.data.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict RFC 8259 JSON parser with line and column in error messages. Nesting is limited to 512 levels. */
public final class JsonReader {

    private static final int MAX_DEPTH = 512;

    private final String text;
    private int pos;
    private int depth;

    private JsonReader(String text) {
        this.text = text;
    }

    /**
     * Parses a complete JSON document.
     *
     * @param text the text
     * @return the value
     * @throws JsonParseException if the text is not valid JSON
     */
    public static JsonValue parse(String text) {
        JsonReader reader = new JsonReader(text);
        reader.skipWhitespace();
        JsonValue value = reader.readValue();
        reader.skipWhitespace();
        if (reader.pos < text.length()) {
            throw reader.error("Unexpected text after the JSON value");
        }
        return value;
    }

    private JsonValue readValue() {
        if (pos >= text.length()) {
            throw error("Unexpected end of input");
        }
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> new JsonString(readString());
            case 't' -> literal("true", JsonBoolean.TRUE);
            case 'f' -> literal("false", JsonBoolean.FALSE);
            case 'n' -> literal("null", JsonNull.INSTANCE);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield readNumber();
                }
                throw error("Unexpected character '" + c + "'");
            }
        };
    }

    private JsonValue literal(String word, JsonValue value) {
        if (!text.startsWith(word, pos)) {
            throw error("Unexpected token, expected " + word);
        }
        pos += word.length();
        return value;
    }

    private JsonObject readObject() {
        enter();
        pos++; // {
        Map<String, JsonValue> members = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            depth--;
            return new JsonObject(members);
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("Expected a member name in double quotes");
            }
            String name = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            members.put(name, readValue());
            skipWhitespace();
            char next = peek();
            pos++;
            if (next == '}') {
                depth--;
                return new JsonObject(members);
            }
            if (next != ',') {
                pos--;
                throw error("Expected ',' or '}' in object");
            }
        }
    }

    private JsonArray readArray() {
        enter();
        pos++; // [
        List<JsonValue> values = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            depth--;
            return new JsonArray(values);
        }
        while (true) {
            skipWhitespace();
            values.add(readValue());
            skipWhitespace();
            char next = peek();
            pos++;
            if (next == ']') {
                depth--;
                return new JsonArray(values);
            }
            if (next != ',') {
                pos--;
                throw error("Expected ',' or ']' in array");
            }
        }
    }

    private String readString() {
        pos++; // opening quote
        StringBuilder out = null;
        int start = pos;
        while (true) {
            if (pos >= text.length()) {
                throw error("Unterminated string");
            }
            char c = text.charAt(pos);
            if (c == '"') {
                String result = out == null
                        ? text.substring(start, pos)
                        : out.append(text, start, pos).toString();
                pos++;
                return result;
            }
            if (c < 0x20) {
                throw error("Control character in string; escape it");
            }
            if (c == '\\') {
                if (out == null) {
                    out = new StringBuilder();
                }
                out.append(text, start, pos);
                pos++;
                if (pos >= text.length()) {
                    throw error("Unterminated escape sequence");
                }
                char escape = text.charAt(pos);
                switch (escape) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (pos + 4 >= text.length()) {
                            throw error("Incomplete \\u escape");
                        }
                        int code = 0;
                        for (int i = 1; i <= 4; i++) {
                            int digit = Character.digit(text.charAt(pos + i), 16);
                            if (digit < 0) {
                                throw error("Invalid \\u escape");
                            }
                            code = code * 16 + digit;
                        }
                        out.append((char) code);
                        pos += 4;
                    }
                    default -> throw error("Invalid escape '\\" + escape + "'");
                }
                pos++;
                start = pos;
            } else {
                pos++;
            }
        }
    }

    private JsonNumber readNumber() {
        int start = pos;
        boolean integral = true;
        if (peek() == '-') {
            pos++;
        }
        if (peek() == '0') {
            pos++;
        } else if (isDigit(peek())) {
            while (isDigit(peek())) {
                pos++;
            }
        } else {
            throw error("Invalid number");
        }
        if (peek() == '.') {
            integral = false;
            pos++;
            if (!isDigit(peek())) {
                throw error("Expected digits after the decimal point");
            }
            while (isDigit(peek())) {
                pos++;
            }
        }
        if (peek() == 'e' || peek() == 'E') {
            integral = false;
            pos++;
            if (peek() == '+' || peek() == '-') {
                pos++;
            }
            if (!isDigit(peek())) {
                throw error("Expected digits in the exponent");
            }
            while (isDigit(peek())) {
                pos++;
            }
        }
        String number = text.substring(start, pos);
        if (integral && number.length() <= 18) {
            return JsonNumber.of(Long.parseLong(number));
        }
        return JsonNumber.of(Double.parseDouble(number));
    }

    private void enter() {
        if (++depth > MAX_DEPTH) {
            throw error("JSON nested deeper than " + MAX_DEPTH + " levels");
        }
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private void expect(char c) {
        if (peek() != c) {
            throw error("Expected '" + c + "'");
        }
        pos++;
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                return;
            }
        }
    }

    private JsonParseException error(String message) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < pos && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new JsonParseException(message, line, column);
    }
}
