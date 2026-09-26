package dev.gulp.core.log;

import dev.gulp.platform.PlatformLog;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Passes log lines to the platform and keeps the last few for crash reports.
 *
 * <pre>{@code
 * RecentLog log = new RecentLog(backend.log(), 50);
 * List<String> lines = log.lines();
 * }</pre>
 */
public final class RecentLog implements PlatformLog {

    private static final String[] LEVELS = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR"};

    private final PlatformLog sink;
    private final String[] ring;
    private int next;
    private int count;

    /**
     * Creates the log.
     *
     * @param sink the platform log
     * @param keep how many lines to keep
     */
    public RecentLog(PlatformLog sink, int keep) {
        this.sink = sink;
        this.ring = new String[Math.max(1, keep)];
    }

    /**
     * Returns the platform log behind this one.
     *
     * @return the sink
     */
    public PlatformLog sink() {
        return sink;
    }

    @Override
    public void write(int level, String logger, String message, @Nullable Throwable error) {
        sink.write(level, logger, message, error);
        String line = "[" + LEVELS[Math.max(0, Math.min(LEVELS.length - 1, level))] + "] [" + logger + "] " + message
                + (error == null ? "" : " (" + error + ")");
        synchronized (ring) {
            ring[next] = line;
            next = (next + 1) % ring.length;
            count = Math.min(count + 1, ring.length);
        }
    }

    /**
     * Returns the kept lines, oldest first.
     *
     * @return a copy
     */
    public List<String> lines() {
        synchronized (ring) {
            List<String> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(ring[(next - count + i + ring.length) % ring.length]);
            }
            return lines;
        }
    }
}
