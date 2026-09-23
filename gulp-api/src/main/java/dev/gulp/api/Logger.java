package dev.gulp.api;

import org.jspecify.annotations.Nullable;

/**
 * Logger with levels, one per engine, game and module. Desktop writes to the terminal and {@code logs/latest.log}; the
 * web writes to the browser console.
 *
 * <pre>{@code
 * logger().info("Level loaded in " + millis + " ms");
 * logger().warn("Missing translation for " + key);
 * logger().error("Could not save", exception);
 * }</pre>
 *
 * <p>Build messages only when needed on hot paths: {@code if (logger().isEnabled(LogLevel.DEBUG)) ...}.
 */
@ThreadSafe
public interface Logger {

    /**
     * Returns the logger name, the owner id.
     *
     * @return the name
     */
    String name();

    /**
     * Returns whether messages of a level are written.
     *
     * @param level the level
     * @return {@code true} if the level is enabled
     */
    boolean isEnabled(LogLevel level);

    /**
     * Writes a message.
     *
     * @param level the level
     * @param message the message
     * @param error an exception to print with its stack trace, or {@code null}
     */
    void log(LogLevel level, String message, @Nullable Throwable error);

    /**
     * Writes a {@link LogLevel#DEBUG} message.
     *
     * @param message the message
     */
    default void debug(String message) {
        log(LogLevel.DEBUG, message, null);
    }

    /**
     * Writes an {@link LogLevel#INFO} message.
     *
     * @param message the message
     */
    default void info(String message) {
        log(LogLevel.INFO, message, null);
    }

    /**
     * Writes a {@link LogLevel#WARN} message.
     *
     * @param message the message
     */
    default void warn(String message) {
        log(LogLevel.WARN, message, null);
    }

    /**
     * Writes a {@link LogLevel#WARN} message with an exception.
     *
     * @param message the message
     * @param error the exception
     */
    default void warn(String message, Throwable error) {
        log(LogLevel.WARN, message, error);
    }

    /**
     * Writes an {@link LogLevel#ERROR} message.
     *
     * @param message the message
     */
    default void error(String message) {
        log(LogLevel.ERROR, message, null);
    }

    /**
     * Writes an {@link LogLevel#ERROR} message with an exception.
     *
     * @param message the message
     * @param error the exception
     */
    default void error(String message, Throwable error) {
        log(LogLevel.ERROR, message, error);
    }
}
