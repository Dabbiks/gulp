package dev.gulp.api.data;

/**
 * A JSON string.
 *
 * <pre>{@code
 * JsonValue name = new JsonString("Slime");
 * }</pre>
 *
 * @param value the text
 */
public record JsonString(String value) implements JsonValue {

    /**
     * Returns the value without quotes.
     *
     * @return the text
     */
    @Override
    public String toString() {
        return value;
    }
}
