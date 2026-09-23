package dev.gulp.backend.headless;

import dev.gulp.platform.PlatformLog;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Log kept in memory so tests can assert on it; warnings and errors are also printed to standard error.
 *
 * <pre>{@code
 * assertThat(backend.log().messages(PlatformLog.ERROR)).anyMatch(m -> m.contains("onEnable failed"));
 * }</pre>
 */
public final class HeadlessLog implements PlatformLog {

    /**
     * One log entry.
     *
     * @param level the level constant
     * @param logger the logger name
     * @param message the message
     * @param error the exception, or {@code null}
     */
    public record Entry(
            int level,
            String logger,
            String message,
            @Nullable Throwable error) {}

    private final List<Entry> entries = new ArrayList<>();

    HeadlessLog() {}

    @Override
    public synchronized void write(int level, String logger, String message, @Nullable Throwable error) {
        entries.add(new Entry(level, logger, message, error));
        if (level >= WARN) {
            System.err.println("[" + logger + "] " + message);
            if (error != null) {
                error.printStackTrace(System.err);
            }
        }
    }

    /**
     * Returns all entries so far.
     *
     * @return a copy, oldest first
     */
    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    /**
     * Returns the messages of one level.
     *
     * @param level the level constant
     * @return the messages, oldest first
     */
    public synchronized List<String> messages(int level) {
        List<String> messages = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.level == level) {
                messages.add(entry.message);
            }
        }
        return messages;
    }
}
