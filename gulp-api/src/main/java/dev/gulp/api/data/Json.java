package dev.gulp.api.data;

import dev.gulp.api.spi.ApiSupport;

/**
 * Reads and writes JSON text. The parser lives in {@code gulp-core}, which must be on the runtime classpath (it always
 * is with any backend).
 *
 * <pre>{@code
 * JsonValue level = Json.parse(text);
 * String compact = Json.write(level);
 * String readable = Json.writePretty(level);
 * }</pre>
 */
public final class Json {

    private Json() {}

    /**
     * Parses JSON text (RFC 8259).
     *
     * @param text the text
     * @return the value
     * @throws JsonParseException with line and column if the text is not valid JSON
     */
    public static JsonValue parse(String text) {
        return ApiSupport.get().parseJson(text);
    }

    /**
     * Writes compact JSON text.
     *
     * @param value the value
     * @return the text
     */
    public static String write(JsonValue value) {
        return ApiSupport.get().writeJson(value, false);
    }

    /**
     * Writes JSON text indented with two spaces.
     *
     * @param value the value
     * @return the text
     */
    public static String writePretty(JsonValue value) {
        return ApiSupport.get().writeJson(value, true);
    }
}
