package dev.gulp.backend.headless;

import dev.gulp.platform.FrameHandler;
import dev.gulp.platform.PlatformLoop;
import org.jspecify.annotations.Nullable;

/**
 * Loop driven by hand. {@link #run(FrameHandler)} only registers the handler; each {@link #step(int)} runs frames with
 * simulated time advancing by {@link #frameNanos()} per frame.
 *
 * <pre>{@code
 * loop.run(handler);
 * loop.step(60); // one simulated second at the default 60 frames per second
 * }</pre>
 */
public final class HeadlessLoop implements PlatformLoop {

    /** Default simulated frame duration: 1/60 s. */
    public static final long DEFAULT_FRAME_NANOS = 1_000_000_000L / 60;

    private final Runnable beforeFrame;
    private @Nullable FrameHandler handler;
    private boolean stopRequested;
    private boolean inFrame;
    private long nanoTime;
    private long frameNanos = DEFAULT_FRAME_NANOS;

    HeadlessLoop(Runnable beforeFrame) {
        this.beforeFrame = beforeFrame;
    }

    @Override
    public void run(FrameHandler handler) {
        if (this.handler != null) {
            throw new IllegalStateException("Loop is already running");
        }
        this.handler = handler;
        this.stopRequested = false;
    }

    /**
     * Runs frames until {@code frames} have run or the loop stops.
     *
     * @param frames how many frames to run, at least 0
     * @return how many frames actually ran
     * @throws IllegalStateException if the loop is not running
     */
    public int step(int frames) {
        if (frames < 0) {
            throw new IllegalArgumentException("frames must not be negative, got " + frames);
        }
        if (handler == null) {
            throw new IllegalStateException("Loop is not running");
        }
        int ran = 0;
        while (ran < frames && handler != null) {
            beforeFrame.run();
            nanoTime += frameNanos;
            inFrame = true;
            boolean keepRunning;
            try {
                keepRunning = handler.frame(nanoTime);
            } finally {
                inFrame = false;
            }
            ran++;
            if (!keepRunning || stopRequested) {
                finish();
            }
        }
        return ran;
    }

    /**
     * Runs frames until the handler asks to stop or {@link #stop()} is called.
     *
     * @param maxFrames upper limit, or {@code 0} for no limit
     * @return how many frames ran
     */
    public long runUntilStopped(long maxFrames) {
        long ran = 0;
        while (handler != null && (maxFrames == 0 || ran < maxFrames)) {
            ran += step(1);
        }
        if (handler != null) {
            stop();
        }
        return ran;
    }

    @Override
    public void stop() {
        if (handler == null) {
            return;
        }
        stopRequested = true;
        if (!inFrame) {
            finish();
        }
    }

    private void finish() {
        FrameHandler finished = handler;
        handler = null;
        if (finished != null) {
            finished.exit();
        }
    }

    @Override
    public boolean isRunning() {
        return handler != null;
    }

    /**
     * Returns the simulated time of the last frame.
     *
     * @return nanoseconds since the loop was created
     */
    public long nanoTime() {
        return nanoTime;
    }

    /**
     * Returns the simulated duration of one frame.
     *
     * @return nanoseconds per frame
     */
    public long frameNanos() {
        return frameNanos;
    }

    /**
     * Changes the simulated frame duration, for example to test frame-rate independence.
     *
     * @param frameNanos nanoseconds per frame, positive
     */
    public void setFrameNanos(long frameNanos) {
        if (frameNanos <= 0) {
            throw new IllegalArgumentException("frameNanos must be positive, got " + frameNanos);
        }
        this.frameNanos = frameNanos;
    }
}
