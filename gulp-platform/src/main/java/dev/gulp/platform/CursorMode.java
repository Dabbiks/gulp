package dev.gulp.platform;

/**
 * How the mouse cursor behaves over the window.
 *
 * <pre>{@code
 * window.setCursorMode(CursorMode.CAPTURED); // mouse look
 * }</pre>
 */
public enum CursorMode {
    /** Visible and free to leave the window. */
    NORMAL,
    /** Invisible while over the window. */
    HIDDEN,
    /** Invisible and locked to the window; only relative motion is reported (pointer lock on the web). */
    CAPTURED
}
