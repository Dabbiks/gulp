package dev.gulp.api.data;

import org.jspecify.annotations.Nullable;

/**
 * Thrown when JSON does not have the shape a {@link Codec} expects. The message includes the path of the bad value.
 *
 * <pre>{@code
 * try {
 *     Upgrade upgrade = Codec.of(Upgrade.class).decode(json);
 * } catch (CodecException e) {
 *     logger().warn("Bad upgrade at " + e.path() + ": " + e.getMessage());
 * }
 * }</pre>
 */
public final class CodecException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String path;
    private final String reason;

    /**
     * Creates the exception at the root of the value.
     *
     * @param reason what is wrong
     */
    public CodecException(String reason) {
        this(reason, "", null);
    }

    /**
     * Creates the exception at the root of the value, with a cause.
     *
     * @param reason what is wrong
     * @param cause the underlying exception
     */
    public CodecException(String reason, Throwable cause) {
        this(reason, "", cause);
    }

    private CodecException(String reason, String path, @Nullable Throwable cause) {
        super(path.isEmpty() ? reason : path + ": " + reason, cause);
        this.reason = reason;
        this.path = path;
    }

    /**
     * Returns a copy of this exception located one level deeper, for nested codecs.
     *
     * @param segment the path segment of the enclosing value, like {@code .name} or {@code [2]}
     * @return the relocated exception
     */
    public CodecException at(String segment) {
        CodecException relocated = new CodecException(reason, segment + path, getCause());
        relocated.setStackTrace(getStackTrace());
        return relocated;
    }

    /**
     * Returns the path of the bad value.
     *
     * @return for example {@code .unlocks[2]}, or an empty string for the root
     */
    public String path() {
        return path;
    }
}
