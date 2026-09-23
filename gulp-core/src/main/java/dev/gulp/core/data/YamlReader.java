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
import org.jspecify.annotations.Nullable;

/**
 * Parser of the YAML subset used for configuration: block mappings and sequences, plain, single- and double-quoted
 * scalars, comments, literal ({@code |}) and folded ({@code >}) block scalars with chomping indicators, and flow
 * collections ({@code [a, b]}, {@code {x: 1}}). Anchors, aliases, tags and multiple documents are not supported.
 * Scalars resolve like YAML 1.2 core schema: {@code null}/{@code ~}, booleans, integers, floats, otherwise strings.
 */
public final class YamlReader {

    private record Line(int number, int indent, String content, String raw) {
        boolean isBlank() {
            return content.isEmpty();
        }
    }

    private final List<Line> lines;
    private int pos;

    private YamlReader(String text) {
        this.lines = new ArrayList<>();
        String[] raw = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int i = 0; i < raw.length; i++) {
            lines.add(toLine(i + 1, raw[i]));
        }
    }

    /**
     * Parses a YAML document.
     *
     * @param text the text
     * @return the value; an empty document gives an empty object
     * @throws JsonParseException with line and column if the text is outside the supported subset or malformed
     */
    public static JsonValue parse(String text) {
        YamlReader reader = new YamlReader(text);
        Line first = reader.peek();
        if (first != null && first.content.equals("---")) {
            reader.pos++;
            first = reader.peek();
        }
        if (first == null) {
            return JsonObject.EMPTY;
        }
        if (first.indent != 0) {
            throw error("The document must not be indented", first.number, first.indent + 1);
        }
        JsonValue value = reader.parseNode(0);
        Line rest = reader.peek();
        if (rest != null) {
            throw error("Unexpected indentation or content", rest.number, rest.indent + 1);
        }
        return value;
    }

    private static Line toLine(int number, String raw) {
        int indent = 0;
        while (indent < raw.length() && raw.charAt(indent) == ' ') {
            indent++;
        }
        String content = stripComment(raw.substring(indent));
        if (!content.isEmpty() && content.charAt(0) == '\t') {
            throw error("Tabs are not allowed for indentation", number, indent + 1);
        }
        return new Line(number, indent, content, raw);
    }

    /** Removes a trailing comment ({@code #} at the start or after whitespace, outside quotes) and trailing spaces. */
    private static String stripComment(String text) {
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else if (c == '\\' && quote == '"') {
                    i++;
                }
            } else if (c == '"' || c == '\'') {
                if (i == 0 || text.charAt(i - 1) == ' ' || "[{,:-".indexOf(text.charAt(i - 1)) >= 0) {
                    quote = c;
                }
            } else if (c == '#' && (i == 0 || text.charAt(i - 1) == ' ' || text.charAt(i - 1) == '\t')) {
                return text.substring(0, i).stripTrailing();
            }
        }
        return text.stripTrailing();
    }

    /** Returns the next non-blank line without consuming it, skipping blank and comment-only lines. */
    private @Nullable Line peek() {
        while (pos < lines.size() && lines.get(pos).isBlank()) {
            pos++;
        }
        return pos < lines.size() ? lines.get(pos) : null;
    }

    private JsonValue parseNode(int indent) {
        Line line = peek();
        if (line == null) {
            return JsonNull.INSTANCE;
        }
        if (isSequenceItem(line.content)) {
            return parseSequence(line.indent);
        }
        if (keyColon(line.content) >= 0) {
            return parseMapping(line.indent);
        }
        pos++;
        return parseInline(line.content, line.number, line.indent + 1);
    }

    private JsonObject parseMapping(int indent) {
        Map<String, JsonValue> map = new LinkedHashMap<>();
        Line line;
        while ((line = peek()) != null && line.indent == indent && !isSequenceItem(line.content)) {
            int colon = keyColon(line.content);
            if (colon < 0) {
                throw error("Expected 'key: value'", line.number, line.indent + 1);
            }
            String key = parseKey(line.content.substring(0, colon).trim(), line);
            if (map.containsKey(key)) {
                throw error("Duplicate key '" + key + "'", line.number, line.indent + 1);
            }
            String rest = line.content.substring(colon + 1).trim();
            pos++;
            JsonValue value;
            if (rest.isEmpty()) {
                Line next = peek();
                if (next != null && (next.indent > indent || (next.indent == indent && isSequenceItem(next.content)))) {
                    value = parseNode(next.indent);
                } else {
                    value = JsonNull.INSTANCE;
                }
            } else if (rest.charAt(0) == '|' || rest.charAt(0) == '>') {
                value = parseBlockScalar(rest, indent, line);
            } else {
                value = parseInline(rest, line.number, line.indent + colon + 2);
            }
            map.put(key, value);
            Line next = peek();
            if (next != null && next.indent > indent) {
                throw error("Unexpected indentation", next.number, next.indent + 1);
            }
        }
        return new JsonObject(map);
    }

    private JsonArray parseSequence(int indent) {
        List<JsonValue> items = new ArrayList<>();
        Line line;
        while ((line = peek()) != null && line.indent == indent && isSequenceItem(line.content)) {
            int offset = 1;
            while (offset < line.content.length() && line.content.charAt(offset) == ' ') {
                offset++;
            }
            String rest = line.content.substring(offset);
            JsonValue value;
            if (rest.isEmpty()) {
                pos++;
                Line next = peek();
                value = next != null && next.indent > indent ? parseNode(next.indent) : JsonNull.INSTANCE;
            } else if (isSequenceItem(rest) || keyColon(rest) >= 0) {
                // "- key: value" or "- - item": the item is a block that starts on this line.
                lines.set(pos, new Line(line.number, indent + offset, rest, line.raw));
                value = parseNode(indent + offset);
            } else if (rest.charAt(0) == '|' || rest.charAt(0) == '>') {
                pos++;
                value = parseBlockScalar(rest, indent, line);
            } else {
                pos++;
                value = parseInline(rest, line.number, indent + offset + 1);
            }
            items.add(value);
            Line next = peek();
            if (next != null && next.indent > indent) {
                throw error("Unexpected indentation", next.number, next.indent + 1);
            }
        }
        return new JsonArray(items);
    }

    private JsonString parseBlockScalar(String header, int parentIndent, Line headerLine) {
        boolean folded = header.charAt(0) == '>';
        char chomp = 'c';
        for (int i = 1; i < header.length(); i++) {
            char c = header.charAt(i);
            if (c == '-' || c == '+') {
                chomp = c;
            } else if (c != ' ') {
                throw error(
                        "Unsupported block scalar header '" + header + "'", headerLine.number, headerLine.indent + 1);
            }
        }
        int blockIndent = -1;
        List<String> content = new ArrayList<>();
        while (pos < lines.size()) {
            String raw = lines.get(pos).raw;
            int indent = 0;
            while (indent < raw.length() && raw.charAt(indent) == ' ') {
                indent++;
            }
            boolean blank = raw.trim().isEmpty();
            if (!blank) {
                if (blockIndent < 0) {
                    if (indent <= parentIndent) {
                        break;
                    }
                    blockIndent = indent;
                } else if (indent < blockIndent) {
                    break;
                }
            }
            content.add(blank ? "" : raw.substring(blockIndent));
            pos++;
        }
        // Blank lines after the block belong to the chomping, not to following content.
        int trailingBlank = 0;
        while (!content.isEmpty() && content.getLast().isEmpty()) {
            content.removeLast();
            trailingBlank++;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < content.size(); i++) {
            String current = content.get(i);
            if (i > 0) {
                String previous = content.get(i - 1);
                if (!folded) {
                    text.append('\n');
                } else if (!previous.isEmpty() && !current.isEmpty() && !current.startsWith(" ")) {
                    text.append(' ');
                } else if (previous.isEmpty() || !current.isEmpty()) {
                    // Folding: the break before a run of empty lines disappears; each empty line is one newline.
                    text.append('\n');
                }
            }
            text.append(current);
        }
        if (!content.isEmpty()) {
            switch (chomp) {
                case '-' -> {}
                case '+' -> text.append("\n".repeat(trailingBlank + 1));
                default -> text.append('\n');
            }
        }
        return new JsonString(text.toString());
    }

    private static boolean isSequenceItem(String content) {
        return content.equals("-") || content.startsWith("- ");
    }

    /** Index of the colon that separates a key from its value, or -1 if the content is not a mapping entry. */
    private static int keyColon(String content) {
        if (content.isEmpty() || content.charAt(0) == '[' || content.charAt(0) == '{') {
            return -1;
        }
        int start = 0;
        if (content.charAt(0) == '"' || content.charAt(0) == '\'') {
            char quote = content.charAt(0);
            int i = 1;
            while (i < content.length()) {
                char c = content.charAt(i);
                if (c == '\\' && quote == '"') {
                    i += 2;
                    continue;
                }
                if (c == quote) {
                    if (quote == '\'' && i + 1 < content.length() && content.charAt(i + 1) == '\'') {
                        i += 2;
                        continue;
                    }
                    break;
                }
                i++;
            }
            start = i + 1;
        }
        for (int i = start; i < content.length(); i++) {
            if (content.charAt(i) == ':' && (i + 1 == content.length() || content.charAt(i + 1) == ' ')) {
                return i;
            }
        }
        return -1;
    }

    private static String parseKey(String key, Line line) {
        if (key.isEmpty()) {
            throw error("Empty key", line.number, line.indent + 1);
        }
        char first = key.charAt(0);
        if (first == '"' || first == '\'') {
            return new FlowParser(key, line.number, line.indent + 1).parseQuoted();
        }
        return key;
    }

    private static JsonValue parseInline(String text, int lineNumber, int column) {
        FlowParser parser = new FlowParser(text, lineNumber, column);
        JsonValue value = parser.parseValue(false);
        parser.skipSpaces();
        if (!parser.atEnd()) {
            throw error("Unexpected text after value", lineNumber, column + parser.index);
        }
        return value;
    }

    /**
     * Resolves a plain (unquoted) scalar to null, boolean, number or string.
     *
     * @param text the scalar text, trimmed
     * @return the value
     */
    static JsonValue resolvePlain(String text) {
        switch (text) {
            case "", "~", "null", "Null", "NULL" -> {
                return JsonNull.INSTANCE;
            }
            case "true", "True", "TRUE" -> {
                return JsonBoolean.TRUE;
            }
            case "false", "False", "FALSE" -> {
                return JsonBoolean.FALSE;
            }
            default -> {}
        }
        if (isInteger(text)) {
            String digits = text.charAt(0) == '+' ? text.substring(1) : text;
            if (digits.length() <= 18) {
                return JsonNumber.of(Long.parseLong(digits));
            }
            return new JsonString(text);
        }
        if (isFloat(text)) {
            return JsonNumber.of(Double.parseDouble(text));
        }
        return new JsonString(text);
    }

    private static boolean isInteger(String text) {
        int i = text.charAt(0) == '-' || text.charAt(0) == '+' ? 1 : 0;
        if (i == text.length()) {
            return false;
        }
        for (; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean isFloat(String text) {
        int i = text.charAt(0) == '-' || text.charAt(0) == '+' ? 1 : 0;
        int digits = 0;
        while (i < text.length() && Character.isDigit(text.charAt(i))) {
            i++;
            digits++;
        }
        if (i < text.length() && text.charAt(i) == '.') {
            i++;
            while (i < text.length() && Character.isDigit(text.charAt(i))) {
                i++;
                digits++;
            }
        }
        if (digits == 0) {
            return false;
        }
        if (i < text.length() && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
            i++;
            if (i < text.length() && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
                i++;
            }
            int exponentDigits = 0;
            while (i < text.length() && Character.isDigit(text.charAt(i))) {
                i++;
                exponentDigits++;
            }
            if (exponentDigits == 0) {
                return false;
            }
        }
        return i == text.length();
    }

    private static JsonParseException error(String message, int line, int column) {
        return new JsonParseException(message, line, column);
    }

    /** Parses one inline value: quoted scalars, flow collections and plain scalars. */
    private static final class FlowParser {
        private final String text;
        private final int line;
        private final int column;
        private int index;

        FlowParser(String text, int line, int column) {
            this.text = text;
            this.line = line;
            this.column = column;
        }

        boolean atEnd() {
            return index >= text.length();
        }

        void skipSpaces() {
            while (index < text.length() && text.charAt(index) == ' ') {
                index++;
            }
        }

        JsonValue parseValue(boolean inFlow) {
            skipSpaces();
            if (atEnd()) {
                return JsonNull.INSTANCE;
            }
            char c = text.charAt(index);
            if (c == '[') {
                return parseFlowSequence();
            }
            if (c == '{') {
                return parseFlowMapping();
            }
            if (c == '"' || c == '\'') {
                return new JsonString(parseQuoted());
            }
            int start = index;
            if (inFlow) {
                while (index < text.length() && ",]}".indexOf(text.charAt(index)) < 0) {
                    if (text.charAt(index) == ':' && (index + 1 == text.length() || text.charAt(index + 1) == ' ')) {
                        break;
                    }
                    index++;
                }
            } else {
                index = text.length();
            }
            return resolvePlain(text.substring(start, index).trim());
        }

        String parseQuoted() {
            char quote = text.charAt(index);
            index++;
            StringBuilder out = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("Unterminated quoted string", line, column + index);
                }
                char c = text.charAt(index++);
                if (c == quote) {
                    if (quote == '\'' && index < text.length() && text.charAt(index) == '\'') {
                        out.append('\'');
                        index++;
                        continue;
                    }
                    return out.toString();
                }
                if (c == '\\' && quote == '"') {
                    if (atEnd()) {
                        throw error("Unterminated escape", line, column + index);
                    }
                    char escape = text.charAt(index++);
                    switch (escape) {
                        case 'n' -> out.append('\n');
                        case 't' -> out.append('\t');
                        case 'r' -> out.append('\r');
                        case '0' -> out.append('\0');
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case ' ' -> out.append(' ');
                        case 'u' -> {
                            if (index + 4 > text.length()) {
                                throw error("Incomplete \\u escape", line, column + index);
                            }
                            try {
                                out.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
                            } catch (NumberFormatException e) {
                                throw error("Invalid \\u escape", line, column + index);
                            }
                            index += 4;
                        }
                        default -> throw error("Unsupported escape '\\" + escape + "'", line, column + index);
                    }
                } else {
                    out.append(c);
                }
            }
        }

        private JsonArray parseFlowSequence() {
            index++; // [
            List<JsonValue> items = new ArrayList<>();
            skipSpaces();
            if (!atEnd() && text.charAt(index) == ']') {
                index++;
                return new JsonArray(items);
            }
            while (true) {
                items.add(parseValue(true));
                skipSpaces();
                if (atEnd()) {
                    throw error("Unterminated flow sequence", line, column + index);
                }
                char c = text.charAt(index++);
                if (c == ']') {
                    return new JsonArray(items);
                }
                if (c != ',') {
                    throw error("Expected ',' or ']'", line, column + index - 1);
                }
            }
        }

        private JsonObject parseFlowMapping() {
            index++; // {
            Map<String, JsonValue> map = new LinkedHashMap<>();
            skipSpaces();
            if (!atEnd() && text.charAt(index) == '}') {
                index++;
                return new JsonObject(map);
            }
            while (true) {
                skipSpaces();
                JsonValue keyValue = parseValue(true);
                String key = keyValue instanceof JsonString s ? s.value() : String.valueOf(keyValue);
                skipSpaces();
                if (atEnd() || text.charAt(index) != ':') {
                    throw error("Expected ':' in flow mapping", line, column + index);
                }
                index++;
                map.put(key, parseValue(true));
                skipSpaces();
                if (atEnd()) {
                    throw error("Unterminated flow mapping", line, column + index);
                }
                char c = text.charAt(index++);
                if (c == '}') {
                    return new JsonObject(map);
                }
                if (c != ',') {
                    throw error("Expected ',' or '}'", line, column + index - 1);
                }
            }
        }
    }
}
