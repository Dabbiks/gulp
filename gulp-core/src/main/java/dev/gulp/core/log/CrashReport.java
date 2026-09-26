package dev.gulp.core.log;

import dev.gulp.core.util.Timestamps;
import java.util.List;

/**
 * The text of a crash report (section 20.2): what failed, the stack trace, modules and their state, the system, the
 * GPU, versions and the last log lines.
 *
 * <pre>{@code
 * String text = CrashReport.text(details, error);
 * backend.log().crash(CrashReport.fileName(now), text);
 * }</pre>
 */
public final class CrashReport {

    private CrashReport() {}

    /**
     * What the report says around the stack trace.
     *
     * @param description what the engine was doing
     * @param epochMillis when it failed
     * @param game the game id
     * @param version the engine version
     * @param backend the backend name
     * @param system operating system and architecture
     * @param gpu the GPU description
     * @param java the Java runtime version, or {@code "?"}
     * @param modules one line per module with its state
     * @param lastLines the last log lines
     */
    public record Details(
            String description,
            long epochMillis,
            String game,
            String version,
            String backend,
            String system,
            String gpu,
            String java,
            List<String> modules,
            List<String> lastLines) {}

    /**
     * Returns the file name of a report.
     *
     * @param epochMillis when it failed
     * @return {@code crash-<date>.txt}
     */
    public static String fileName(long epochMillis) {
        return "crash-" + Timestamps.fileStamp(epochMillis) + ".txt";
    }

    /**
     * Writes the report.
     *
     * @param details the context
     * @param error what was thrown
     * @return the text
     */
    public static String text(Details details, Throwable error) {
        StringBuilder out = new StringBuilder();
        out.append("---- Gulp crash report ----\n");
        out.append("Time: ").append(Timestamps.readable(details.epochMillis())).append('\n');
        out.append("Description: ").append(details.description()).append("\n\n");
        stackTrace(out, error);
        out.append("\n-- Game --\n");
        out.append("Game: ").append(details.game()).append('\n');
        out.append("Gulp: ").append(details.version()).append('\n');
        out.append("Backend: ").append(details.backend()).append('\n');
        out.append("\n-- Modules --\n");
        if (details.modules().isEmpty()) {
            out.append("(none)\n");
        }
        for (String module : details.modules()) {
            out.append(module).append('\n');
        }
        out.append("\n-- System --\n");
        out.append("OS: ").append(details.system()).append('\n');
        out.append("GPU: ").append(details.gpu()).append('\n');
        out.append("Java: ").append(details.java()).append('\n');
        out.append("\n-- Last log lines --\n");
        for (String line : details.lastLines()) {
            out.append(line).append('\n');
        }
        return out.toString();
    }

    /**
     * Writes a throwable with its causes, like {@code printStackTrace} but without {@code java.io}.
     *
     * @param out where to write
     * @param error the throwable
     */
    public static void stackTrace(StringBuilder out, Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 10) {
            if (current != error) {
                out.append("Caused by: ");
            }
            out.append(current).append('\n');
            StackTraceElement[] trace = current.getStackTrace();
            for (int i = 0; trace != null && i < trace.length && i < 64; i++) {
                out.append("\tat ").append(trace[i]).append('\n');
            }
            current = current.getCause() == current ? null : current.getCause();
        }
    }
}
