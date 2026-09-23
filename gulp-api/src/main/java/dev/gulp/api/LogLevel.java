package dev.gulp.api;

/**
 * Severity of a log message, from the most verbose.
 *
 * <pre>{@code
 * if (logger().isEnabled(LogLevel.DEBUG)) logger().debug(describe(state));
 * }</pre>
 */
public enum LogLevel {
    /** Very detailed diagnostics, off by default. */
    TRACE,
    /** Diagnostics for developers. */
    DEBUG,
    /** Normal operation. */
    INFO,
    /** Something unexpected that the game survives. */
    WARN,
    /** A failure: a feature does not work. */
    ERROR
}
