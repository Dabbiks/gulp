package dev.gulp.core.util;

/**
 * Measures elapsed time with {@link System#nanoTime()}, for statistics and profiling.
 *
 * <pre>{@code
 * Stopwatch watch = new Stopwatch().start();
 * rebuildMeshes();
 * logger.debug("Rebuilt in " + watch.millis() + " ms");
 * }</pre>
 */
public final class Stopwatch {

    private long startedAt;
    private long accumulated;
    private boolean running;

    /** Creates a stopped stopwatch at zero. */
    public Stopwatch() {}

    /**
     * Starts or resumes timing.
     *
     * @return this stopwatch
     */
    public Stopwatch start() {
        if (!running) {
            startedAt = System.nanoTime();
            running = true;
        }
        return this;
    }

    /**
     * Stops timing, keeping the elapsed time.
     *
     * @return the elapsed nanoseconds
     */
    public long stop() {
        if (running) {
            accumulated += System.nanoTime() - startedAt;
            running = false;
        }
        return accumulated;
    }

    /** Stops and sets the elapsed time to zero. */
    public void reset() {
        running = false;
        accumulated = 0L;
    }

    /**
     * Returns whether the stopwatch runs.
     *
     * @return {@code true} while timing
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the elapsed time.
     *
     * @return nanoseconds, including the running interval
     */
    public long nanos() {
        return running ? accumulated + System.nanoTime() - startedAt : accumulated;
    }

    /**
     * Returns the elapsed time.
     *
     * @return milliseconds
     */
    public float millis() {
        return nanos() / 1_000_000f;
    }
}
