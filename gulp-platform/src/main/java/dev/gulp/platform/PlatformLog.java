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

    /**
     * Keeps a crash report: a file in {@code crash-reports/} on desktop, the error overlay with a copy button on the
     * web. The default writes it to the log.
     *
     * <pre>{@code
     * String where = backend.log().crash("crash-2026-09-25_14-03-07.txt", report);
     * }</pre>
     *
     * @param name the file name
     * @param report the text
     * @return where the report is, for the error screen, or an empty string
     */
    default String crash(String name, String report) {
        write(ERROR, "gulp", report, null);
        return "";
    }
}
