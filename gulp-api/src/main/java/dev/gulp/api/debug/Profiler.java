package dev.gulp.api.debug;

/**
 * Measures where time goes: every module, listener, task and component type, plus sections the game opens itself.
 * {@code /profile start} and {@code /profile stop} do the same from the console; the report goes to the log, and to
 * {@code profiles/profile-<date>.txt} on desktop.
 *
 * <pre>{@code
 * debug().profiler().start();
 * // ... play for a while ...
 * ProfileReport report = debug().profiler().stop();
 * logger().info(report.text());
 * }</pre>
 */
public interface Profiler {

    /** Starts measuring from zero; does nothing when already running. */
    void start();

    /**
     * Stops measuring and returns what was measured.
     *
     * @return the report; empty if the profiler was not running
     */
    ProfileReport stop();

    /**
     * Returns whether the profiler is measuring.
     *
     * @return {@code true} between {@link #start()} and {@link #stop()}
     */
    boolean isRunning();

    /**
     * Opens a named section; close it, best with try-with-resources. Sections nest.
     *
     * <pre>{@code
     * try (var _ = profiler.section("lighting")) {
     *     bakeLights();
     * }
     * }</pre>
     *
     * @param name the name in the report
     * @return the open section
     */
    Section section(String name);

    /** An open profiler section. */
    interface Section extends AutoCloseable {

        /** Ends the section and adds its time to the report. */
        @Override
        void close();
    }
}
