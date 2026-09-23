package dev.gulp.api.data;

/**
 * A JSON boolean.
 *
 * <pre>{@code
 * JsonValue flag = JsonBoolean.of(true);
 * }</pre>
 *
 * @param value the value
 */
public record JsonBoolean(boolean value) implements JsonValue {

    /** {@code true}. */
    public static final JsonBoolean TRUE = new JsonBoolean(true);

    /** {@code false}. */
    public static final JsonBoolean FALSE = new JsonBoolean(false);

    /**
     * Returns the shared instance for a value.
     *
     * @param value the value
     * @return {@link #TRUE} or {@link #FALSE}
     */
    public static JsonBoolean of(boolean value) {
        return value ? TRUE : FALSE;
    }

    /**
     * Returns {@code true} or {@code false}.
     *
     * @return the JSON text
     */
    @Override
    public String toString() {
        return Boolean.toString(value);
    }
}
