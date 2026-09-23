package dev.gulp.backend.desktop;

import dev.gulp.platform.PlatformLog;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

/**
 * Writes log lines to the terminal (warnings and errors to standard error) and to {@code logs/latest.log} in the data
 * directory. The previous run's log is kept as {@code logs/previous.log}; full rotation arrives in stage 11.
 */
public final class DesktopLog implements PlatformLog {

    private static final String[] LEVELS = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR"};

    private @Nullable BufferedWriter file;

    DesktopLog(Path logsDirectory) {
        try {
            Files.createDirectories(logsDirectory);
            Path latest = logsDirectory.resolve("latest.log");
            if (Files.exists(latest)) {
                Files.move(latest, logsDirectory.resolve("previous.log"), StandardCopyOption.REPLACE_EXISTING);
            }
            file = Files.newBufferedWriter(latest, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[gulp] Cannot write log file in " + logsDirectory + ": " + e);
        }
    }

    /**
     * Formats one line.
     *
     * @param time the time of day
     * @param level the level constant
     * @param logger the logger name
     * @param message the message
     * @return {@code [HH:mm:ss] [LEVEL] [logger] message}
     */
    static String format(LocalTime time, int level, String logger, String message) {
        return "[" + two(time.getHour()) + ":" + two(time.getMinute()) + ":" + two(time.getSecond()) + "] ["
                + LEVELS[Math.max(0, Math.min(LEVELS.length - 1, level))] + "] [" + logger + "] " + message;
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    @Override
    public synchronized void write(int level, String logger, String message, @Nullable Throwable error) {
        String line = format(LocalTime.now(), level, logger, message);
        String trace = null;
        if (error != null) {
            StringWriter text = new StringWriter();
            error.printStackTrace(new PrintWriter(text));
            trace = text.toString();
        }
        var out = level >= WARN ? System.err : System.out;
        out.println(line);
        if (trace != null) {
            out.print(trace);
        }
        if (file != null) {
            try {
                file.write(line);
                file.newLine();
                if (trace != null) {
                    file.write(trace);
                }
                file.flush();
            } catch (IOException e) {
                file = null;
                System.err.println("[gulp] Log file disabled: " + e);
            }
        }
    }

    synchronized void close() {
        if (file != null) {
            try {
                file.close();
            } catch (IOException ignored) {
                // nothing left to do
            }
            file = null;
        }
    }
}
