package dev.gulp.api.debug;

import java.util.List;

/**
 * What the profiler measured, sorted from the most expensive entry.
 *
 * <pre>{@code
 * ProfileReport report = debug().profiler().stop();
 * for (ProfileReport.Entry entry : report.entries()) {
 *     if (entry.category() == ProfileReport.Category.COMPONENT) {
 *         logger().info(entry.name() + ": " + entry.millisPerTick(report.ticks()) + " ms per tick");
 *     }
 * }
 * }</pre>
 *
 * @param nanos how long the profiler ran, in real time
 * @param ticks game ticks in that time
 * @param frames frames in that time
 * @param entries measured entries, the most expensive first
 */
public record ProfileReport(long nanos, long ticks, long frames, List<Entry> entries) {

    /** An empty report. */
    public static final ProfileReport EMPTY = new ProfileReport(0, 0, 0, List.of());

    /**
     * Creates a report.
     *
     * @param nanos run time
     * @param ticks game ticks
     * @param frames frames
     * @param entries entries
     */
    public ProfileReport {
        entries = List.copyOf(entries);
    }

    /** What an entry measures. */
    public enum Category {
        /** The whole game tick. */
        TICK,
        /** The whole frame render. */
        RENDER,
        /** Everything a module's listeners and tasks took. */
        MODULE,
        /** One event handler. */
        LISTENER,
        /** Scheduled tasks of one owner. */
        TASK,
        /** {@code onTick} of one component type. */
        COMPONENT,
        /** A section the game opened. */
        SECTION
    }

    /**
     * One measured thing.
     *
     * @param category what it is
     * @param name its name, such as {@code "PlayerController"} or {@code "EntityDamageEvent (combat)"}
     * @param calls how many times it ran
     * @param totalNanos time spent in total
     * @param maxNanos the longest single run
     */
    public record Entry(Category category, String name, long calls, long totalNanos, long maxNanos) {

        /**
         * Returns the average time per tick.
         *
         * @param ticks the ticks of the report
         * @return milliseconds per tick
         */
        public double millisPerTick(long ticks) {
            return ticks <= 0 ? 0.0 : totalNanos / 1e6 / ticks;
        }
    }

    /**
     * Formats the report as a table for the log and the report file.
     *
     * @return the text
     */
    public String text() {
        StringBuilder out = new StringBuilder();
        out.append("Profile: ")
                .append(millis(nanos))
                .append(" ms, ")
                .append(ticks)
                .append(" ticks, ")
                .append(frames)
                .append(" frames\n");
        out.append("category   total ms   ms/tick    max ms      calls  name\n");
        for (Entry entry : entries) {
            out.append(pad(entry.category().name().toLowerCase(java.util.Locale.ROOT), 9))
                    .append(padLeft(millis(entry.totalNanos()), 10))
                    .append(padLeft(fixed(entry.millisPerTick(ticks)), 10))
                    .append(padLeft(millis(entry.maxNanos()), 10))
                    .append(padLeft(Long.toString(entry.calls()), 11))
                    .append("  ")
                    .append(entry.name())
                    .append('\n');
        }
        return out.toString();
    }

    private static String millis(long nanos) {
        return fixed(nanos / 1e6);
    }

    private static String fixed(double value) {
        long thousandths = Math.round(value * 1000.0);
        String sign = thousandths < 0 ? "-" : "";
        long abs = Math.abs(thousandths);
        String fraction = Long.toString(1000 + abs % 1000).substring(1);
        return sign + abs / 1000 + "." + fraction;
    }

    private static String pad(String text, int width) {
        StringBuilder out = new StringBuilder(text);
        while (out.length() < width) {
            out.append(' ');
        }
        return out.toString();
    }

    private static String padLeft(String text, int width) {
        StringBuilder out = new StringBuilder();
        while (out.length() + text.length() < width) {
            out.append(' ');
        }
        return out.append(text).toString();
    }
}
