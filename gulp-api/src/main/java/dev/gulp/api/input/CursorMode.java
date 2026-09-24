package dev.gulp.api.input;

/**
 * How the mouse cursor behaves over the game.
 *
 * <pre>{@code
 * input().setCursorMode(CursorMode.CAPTURED); // mouse look: read mouseDeltaX() and mouseDeltaY()
 * }</pre>
 */
public enum CursorMode {
    /** Visible and free. */
    NORMAL,
    /** Hidden over the game but free to leave it. */
    HIDDEN,
    /** Hidden and locked to the game; only relative motion is reported. On the web this needs a click first. */
    CAPTURED
}
