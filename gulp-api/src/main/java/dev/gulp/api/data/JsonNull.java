package dev.gulp.api.data;

/**
 * The JSON {@code null} value.
 *
 * <pre>{@code
 * if (value == JsonNull.INSTANCE) useDefault();
 * }</pre>
 */
public enum JsonNull implements JsonValue {
    /** The only instance. */
    INSTANCE;

    /**
     * Returns {@code null}.
     *
     * @return the JSON text
     */
    @Override
    public String toString() {
        return "null";
    }
}
