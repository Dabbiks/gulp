package dev.gulp.api.ui;

/**
 * How a screen appears and disappears: fading, sliding up from below or growing from the centre. Transitions run in
 * real time, so they also play while the game is paused.
 *
 * <pre>{@code
 * public PauseScreen() {
 *     pausesGame(true).enter(ScreenTransition.scale(0.2f)).exit(ScreenTransition.fade(0.15f));
 * }
 * }</pre>
 */
public final class ScreenTransition {

    /** Kinds of screen transition. */
    public enum Kind {
        /** No animation. */
        NONE,
        /** Opacity from 0 to 1. */
        FADE,
        /** Fades in while sliding up from below. */
        SLIDE_UP,
        /** Fades in while growing from 90% size. */
        SCALE
    }

    /** No animation. */
    public static final ScreenTransition NONE = new ScreenTransition(Kind.NONE, 0f);

    private final Kind kind;
    private final float seconds;

    private ScreenTransition(Kind kind, float seconds) {
        this.kind = kind;
        this.seconds = Math.max(0f, seconds);
    }

    /**
     * Fades the screen.
     *
     * @param seconds duration
     * @return the transition
     */
    public static ScreenTransition fade(float seconds) {
        return new ScreenTransition(Kind.FADE, seconds);
    }

    /**
     * Slides the screen up while fading it.
     *
     * @param seconds duration
     * @return the transition
     */
    public static ScreenTransition slideUp(float seconds) {
        return new ScreenTransition(Kind.SLIDE_UP, seconds);
    }

    /**
     * Grows the screen from the centre while fading it.
     *
     * @param seconds duration
     * @return the transition
     */
    public static ScreenTransition scale(float seconds) {
        return new ScreenTransition(Kind.SCALE, seconds);
    }

    /**
     * Returns the kind.
     *
     * @return the kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns the duration.
     *
     * @return seconds
     */
    public float seconds() {
        return seconds;
    }

    /**
     * Returns the opacity at a point of the transition.
     *
     * @param progress {@code 0} hidden, {@code 1} fully shown
     * @return {@code 0..1}
     */
    public float alphaAt(float progress) {
        return kind == Kind.NONE ? 1f : ease(progress);
    }

    /**
     * Returns the vertical shift at a point of the transition.
     *
     * @param progress {@code 0} hidden, {@code 1} fully shown
     * @param height screen height in UI points
     * @return UI points down
     */
    public float offsetYAt(float progress, float height) {
        return kind == Kind.SLIDE_UP ? (1f - ease(progress)) * height * 0.08f : 0f;
    }

    /**
     * Returns the scale at a point of the transition.
     *
     * @param progress {@code 0} hidden, {@code 1} fully shown
     * @return the factor
     */
    public float scaleAt(float progress) {
        return kind == Kind.SCALE ? 0.9f + 0.1f * ease(progress) : 1f;
    }

    private static float ease(float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        float inverse = 1f - clamped;
        return 1f - inverse * inverse * inverse;
    }
}
