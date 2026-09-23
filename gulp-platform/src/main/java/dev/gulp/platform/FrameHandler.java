package dev.gulp.platform;

/**
 * Receives frames from a {@link PlatformLoop}. Implemented by the engine in {@code gulp-core}.
 *
 * <pre>{@code
 * loop.run(new FrameHandler() {
 *     public boolean frame(long nanoTime) { engine.frame(nanoTime); return engine.isRunning(); }
 *     public void exit() { engine.shutdown(); }
 * });
 * }</pre>
 */
public interface FrameHandler {

    /**
     * Runs one frame: ticks as needed and renders.
     *
     * @param nanoTime monotonic time of this frame in nanoseconds; only differences between frames are meaningful
     * @return {@code true} to keep running, {@code false} to stop the loop after this frame
     */
    boolean frame(long nanoTime);

    /** Called once when the loop ends, whether {@link #frame(long)} returned {@code false} or the loop was stopped. */
    void exit();
}
