package dev.gulp.backend.desktop;

import dev.gulp.platform.PlatformLog;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

/**
 * Writes log lines to the terminal (warnings and errors to standard error) and to {@code logs/latest.log} in the data
 * directory. The previous run's log is kept as {@code logs/previous.log}; full rotation arrives in stage 11.
 */
public final class DesktopLog implements PlatformLog {

    private static final String[] LEVELS = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR"};

    /** Archived logs kept next to {@code latest.log}; older ones are deleted. */
    static final int KEEP_ARCHIVES = 10;

    private @Nullable BufferedWriter file;
    private final Path crashDirectory;

    DesktopLog(Path logsDirectory) {
        this.crashDirectory = logsDirectory.resolveSibling("crash-reports");
        try {
            Files.createDirectories(logsDirectory);
            rotate(logsDirectory, KEEP_ARCHIVES);
            file = Files.newBufferedWriter(logsDirectory.resolve("latest.log"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[gulp] Cannot write log file in " + logsDirectory + ": " + e);
        }
    }

    /**
     * Archives {@code latest.log} as {@code <date>-<n>.log.gz} (the date it was last written) and keeps the newest
     * archives only.
     *
     * @param logs the logs folder
     * @param keep how many archives to keep
     * @throws IOException if the files cannot be moved
     */
    static void rotate(Path logs, int keep) throws IOException {
        Path latest = logs.resolve("latest.log");
        if (Files.exists(latest)) {
            String date = java.time.LocalDate.ofInstant(
                            Files.getLastModifiedTime(latest).toInstant(), java.time.ZoneId.systemDefault())
                    .toString();
            int n = 1;
            Path archive;
            do {
                archive = logs.resolve(date + "-" + n++ + ".log.gz");
            } while (Files.exists(archive));
            try (var in = Files.newInputStream(latest);
                    var out = new java.util.zip.GZIPOutputStream(Files.newOutputStream(archive))) {
                in.transferTo(out);
            }
            Files.delete(latest);
        }
        java.util.List<Path> archives;
        try (var files = Files.list(logs)) {
            archives = files.filter(f -> f.getFileName().toString().endsWith(".log.gz"))
                    .sorted(java.util.Comparator.comparing((Path f) -> {
                                try {
                                    return Files.getLastModifiedTime(f);
                                } catch (IOException e) {
                                    return java.nio.file.attribute.FileTime.fromMillis(0);
                                }
                            })
                            .thenComparing(Path::toString))
                    .toList();
        }
        for (int i = 0; i < archives.size() - keep; i++) {
            Files.deleteIfExists(archives.get(i));
        }
    }

    @Override
    public String crash(String name, String report) {
        System.err.println(report);
        try {
            Files.createDirectories(crashDirectory);
            Path target = crashDirectory.resolve(name);
            Files.writeString(target, report, StandardCharsets.UTF_8);
            write(ERROR, "gulp", "Crash report written to " + target.toAbsolutePath(), null);
            return target.toAbsolutePath().toString();
        } catch (IOException e) {
            write(ERROR, "gulp", "Cannot write the crash report: " + e, null);
            return "";
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
