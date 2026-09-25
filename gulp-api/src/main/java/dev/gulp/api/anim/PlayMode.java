package dev.gulp.api.anim;

/**
 * How a {@link SpriteAnimation} goes through its frames.
 *
 * <pre>{@code
 * SpriteAnimation.builder("torch").frames(frames, 8f).mode(PlayMode.LOOP_RANDOM).build();
 * }</pre>
 */
public enum PlayMode {
    /** First to last, then stays on the last frame. */
    ONCE,
    /** First to last, over and over. */
    LOOP,
    /** First to last and back, over and over. */
    PING_PONG,
    /** Last to first, then stays on the first frame. */
    REVERSED,
    /** A random frame each time, over and over. */
    LOOP_RANDOM;

    /**
     * Returns whether the animation ends by itself.
     *
     * @return {@code true} for {@link #ONCE} and {@link #REVERSED}
     */
    public boolean ends() {
        return this == ONCE || this == REVERSED;
    }
}
