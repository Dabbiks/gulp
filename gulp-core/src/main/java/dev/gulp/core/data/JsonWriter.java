package dev.gulp.core.data;

import dev.gulp.api.data.JsonArray;
import dev.gulp.api.data.JsonBoolean;
import dev.gulp.api.data.JsonNull;
import dev.gulp.api.data.JsonNumber;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonString;
import dev.gulp.api.data.JsonValue;
import java.util.Map;

/** Writes {@link JsonValue}s as compact or indented JSON text. */
public final class JsonWriter {

    private JsonWriter() {}

    /**
     * Writes a value.
     *
     * @param value the value
     * @param pretty whether to indent with two spaces
     * @return the JSON text
     */
    public static String write(JsonValue value, boolean pretty) {
        StringBuilder out = new StringBuilder();
        write(out, value, pretty, 0);
        return out.toString();
    }

    private static void write(StringBuilder out, JsonValue value, boolean pretty, int indent) {
        switch (value) {
            case JsonNull n -> out.append("null");
            case JsonBoolean b -> out.append(b.value());
            case JsonNumber n -> out.append(n);
            case JsonString s -> quote(out, s.value());
            case JsonArray array -> {
                if (array.size() == 0) {
                    out.append("[]");
                    return;
                }
                out.append('[');
                for (int i = 0; i < array.size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    newline(out, pretty, indent + 1);
                    write(out, array.get(i), pretty, indent + 1);
                }
                newline(out, pretty, indent);
                out.append(']');
            }
            case JsonObject object -> {
                if (object.size() == 0) {
                    out.append("{}");
                    return;
                }
                out.append('{');
                boolean first = true;
                for (Map.Entry<String, JsonValue> member : object.members().entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    newline(out, pretty, indent + 1);
                    quote(out, member.getKey());
                    out.append(pretty ? ": " : ":");
                    write(out, member.getValue(), pretty, indent + 1);
                }
                newline(out, pretty, indent);
                out.append('}');
            }
        }
    }

    private static void newline(StringBuilder out, boolean pretty, int indent) {
        if (pretty) {
            out.append('\n');
            for (int i = 0; i < indent; i++) {
                out.append("  ");
            }
        }
    }

    /**
     * Appends a string as a quoted, escaped JSON string.
     *
     * @param out the destination
     * @param text the string
     */
    static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u00");
                        out.append(Character.forDigit(c >> 4, 16));
                        out.append(Character.forDigit(c & 0xf, 16));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
