package dev.gulp.core.data;

import dev.gulp.api.data.JsonArray;
import dev.gulp.api.data.JsonBoolean;
import dev.gulp.api.data.JsonNull;
import dev.gulp.api.data.JsonNumber;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonString;
import dev.gulp.api.data.JsonValue;
import java.util.Map;

/** Writes {@link JsonValue}s as block-style YAML that {@link YamlReader} reads back unchanged. */
public final class YamlWriter {

    private YamlWriter() {}

    /**
     * Writes a document.
     *
     * @param value the root, usually an object
     * @return the YAML text, ending with a newline
     */
    public static String write(JsonValue value) {
        StringBuilder out = new StringBuilder();
        switch (value) {
            case JsonObject object when object.size() > 0 -> writeMapping(out, object, 0);
            case JsonArray array when array.size() > 0 -> writeSequence(out, array, 0);
            default -> out.append(scalar(value)).append('\n');
        }
        return out.toString();
    }

    private static void writeMapping(StringBuilder out, JsonObject object, int indent) {
        for (Map.Entry<String, JsonValue> member : object.members().entrySet()) {
            indent(out, indent);
            out.append(key(member.getKey())).append(':');
            writeChild(out, member.getValue(), indent);
        }
    }

    private static void writeSequence(StringBuilder out, JsonArray array, int indent) {
        for (JsonValue item : array) {
            indent(out, indent);
            out.append('-');
            writeChild(out, item, indent);
        }
    }

    private static void writeChild(StringBuilder out, JsonValue value, int indent) {
        switch (value) {
            case JsonObject object
            when object.size() > 0 -> {
                out.append('\n');
                writeMapping(out, object, indent + 2);
            }
            case JsonArray array
            when array.size() > 0 -> {
                out.append('\n');
                writeSequence(out, array, indent + 2);
            }
            default -> out.append(' ').append(scalar(value)).append('\n');
        }
    }

    private static String key(String key) {
        return isPlainSafe(key) ? key : quoted(key);
    }

    private static String scalar(JsonValue value) {
        return switch (value) {
            case JsonNull n -> "null";
            case JsonBoolean b -> Boolean.toString(b.value());
            case JsonNumber n -> n.toString();
            case JsonString s -> isPlainSafe(s.value()) ? s.value() : quoted(s.value());
            case JsonArray a -> "[]";
            case JsonObject o -> "{}";
        };
    }

    /** Whether a string can be written without quotes and still read back as the same string. */
    static boolean isPlainSafe(String text) {
        if (text.isEmpty() || text.charAt(0) == ' ' || text.charAt(text.length() - 1) == ' ') {
            return false;
        }
        char first = text.charAt(0);
        if (!(Character.isLetterOrDigit(first) || first == '_' || first == '/' || first == '.')) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!(Character.isLetterOrDigit(c) || " _-./()+,".indexOf(c) >= 0)) {
                return false;
            }
        }
        return YamlReader.resolvePlain(text) instanceof JsonString;
    }

    private static String quoted(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u00")
                                .append(Character.forDigit(c >> 4, 16))
                                .append(Character.forDigit(c & 0xf, 16));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static void indent(StringBuilder out, int indent) {
        for (int i = 0; i < indent; i++) {
            out.append(' ');
        }
    }
}
