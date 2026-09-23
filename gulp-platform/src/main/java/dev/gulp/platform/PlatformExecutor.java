package dev.gulp.platform;

/**
 * Runs work outside the main tick: asset decoding, chunk generation, path finding, disk writes. Desktop uses virtual
 * threads, the web runs tasks cooperatively between frames, headless runs them synchronously.
 *
 * <pre>{@code
 * executor.execute(() -> {
 *     Chunk chunk = generator.generate(x, y); // must not touch world state
 *     mainQueue.post(() -> world.addChunk(chunk));
 * });
 * }</pre>
 */
public interface PlatformExecutor {

    /**
     * Schedules work. The task must not touch game state; results go back through the scheduler in core.
     * Thread-safe.
     *
     * @param task the work
     */
    void execute(Runnable task);

    /**
     * Returns whether tasks really run in parallel with the main thread.
     *
     * @return {@code true} on desktop, {@code false} on the web and headless
     */
    boolean isConcurrent();

    /** Stops accepting tasks and waits briefly for running ones. Called by {@link PlatformBackend#dispose()}. */
    void shutdown();
}
