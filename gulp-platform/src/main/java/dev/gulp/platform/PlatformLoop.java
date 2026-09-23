package dev.gulp.platform;

/**
 * Drives frames. Desktop runs a {@code while} loop on the main thread, the web uses {@code requestAnimationFrame}, and
 * the headless backend advances only when a test calls {@code step(n)}.
 *
 * <pre>{@code
 * backend.loop().run(handler); // blocks on desktop, returns immediately on the web
 * }</pre>
 */
public interface PlatformLoop {

    /**
     * Starts calling {@link FrameHandler#frame(long)} until it returns {@code false} or {@link #stop()} is called,
     * then calls {@link FrameHandler#exit()}. Blocks on desktop; returns at once on the web and in headless mode.
     *
     * @param handler the frame receiver
     * @throws IllegalStateException if the loop is already running
     */
    void run(FrameHandler handler);

    /** Asks the loop to end after the current frame. Safe to call from inside a frame. */
    void stop();

    /**
     * Returns whether the loop is running.
     *
     * @return {@code true} between {@link #run(FrameHandler)} and {@link FrameHandler#exit()}
     */
    boolean isRunning();
}
