package dev.gulp.api.data;

/**
 * Thrown when JSON or YAML text cannot be parsed.
 *
 * <pre>{@code
 * try {
 *     Json.parse(text);
 * } catch (JsonParseException e) {
 *     logger().warn("Bad level file at line " + e.line() + ", column " + e.column());
 * }
 * }</pre>
 */
public final class JsonParseException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final int line;
    private final int column;

    /**
     * Creates the exception.
     *
     * @param message what is wrong
     * @param line 1-based line of the error
     * @param column 1-based column of the error
     */
    public JsonParseException(String message, int line, int column) {
        super(message + " (line " + line + ", column " + column + ")");
        this.line = line;
        this.column = column;
    }

    /**
     * Returns the line of the error.
     *
     * @return the 1-based line
     */
    public int line() {
        return line;
    }

    /**
     * Returns the column of the error.
     *
     * @return the 1-based column
     */
    public int column() {
        return column;
    }
}
