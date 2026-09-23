package dev.gulp.api.render;

/**
 * What happens when the window has a different aspect ratio than the base resolution.
 *
 * <pre>{@code
 * settings.baseResolution(640, 360).aspectMode(AspectMode.EXPAND);   // show more world on wide screens
 * }</pre>
 */
public enum AspectMode {
    /** Stretches non-uniformly to fill the window. */
    IGNORE,
    /** Keeps the aspect ratio and adds bars (letterbox or pillarbox). */
    KEEP,
    /** Keeps the base width; the visible height follows the window. */
    KEEP_WIDTH,
    /** Keeps the base height; the visible width follows the window. */
    KEEP_HEIGHT,
    /** Keeps the whole base area visible and shows more on the longer side. */
    EXPAND
}
