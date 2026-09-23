package dev.gulp.platform;

import org.jspecify.annotations.Nullable;

/**
 * Destination of log lines: the terminal and {@code logs/latest.log} on desktop, the browser console on the web, memory
 * in headless mode. Thread-safe: background tasks may log.
 *
 * <pre>{@code
 * backend.log().write(PlatformLog.INFO, "coins", "Level loaded", null);
 * }</pre>
 */
public interface PlatformLog {

    /** Level {@code TRACE}. */
    int TRACE = 0;
    /** Level {@code DEBUG}. */
    int DEBUG = 1;
    /** Level {@code INFO}. */
    int INFO = 2;
    /** Level {@code WARN}. */
    int WARN = 3;
    /** Level {@code ERROR}. */
    int ERROR = 4;

    /**
     * Writes one log entry.
     *
     * @param level one of the level constants
     * @param logger the logger name
     * @param message the message
     * @param error an exception to print with its stack trace, or {@code null}
     */
    void write(int level, String logger, String message, @Nullable Throwable error);
}
