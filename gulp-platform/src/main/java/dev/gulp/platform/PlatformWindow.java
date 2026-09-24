package dev.gulp.platform;

import org.jspecify.annotations.Nullable;

/**
 * The window on desktop or the canvas on the web. Game logic and UI work in logical points; the framebuffer is in
 * physical pixels ({@code framebufferWidth = width * contentScale}).
 *
 * <pre>{@code
 * gl.viewport(0, 0, window.framebufferWidth(), window.framebufferHeight());
 * }</pre>
 */
public interface PlatformWindow {

    /**
     * Returns the width in logical points.
     *
     * @return the width
     */
    int width();

    /**
     * Returns the height in logical points.
     *
     * @return the height
     */
    int height();

    /**
     * Returns the framebuffer width in physical pixels.
     *
     * @return the width
     */
    int framebufferWidth();

    /**
     * Returns the framebuffer height in physical pixels.
     *
     * @return the height
     */
    int framebufferHeight();

    /**
     * Returns the ratio of physical pixels to logical points ({@code devicePixelRatio} on the web).
     *
     * @return {@code 1.0} on standard displays, {@code 2.0} on typical HiDPI displays
     */
    float contentScale();

    /**
     * Sets the window title.
     *
     * @param title the new title
     */
    void setTitle(String title);

    /**
     * Sets the window icon. Ignored on platforms without window icons.
     *
     * @param icon RGBA icon image
     */
    void setIcon(DecodedImage icon);

    /**
     * Resizes the window. Ignored in fullscreen and on the web.
     *
     * @param width width in logical points
     * @param height height in logical points
     */
    void setSize(int width, int height);

    /**
     * Returns whether the window is fullscreen.
     *
     * @return {@code true} if fullscreen
     */
    boolean isFullscreen();

    /**
     * Enters or leaves fullscreen. On the web this only works during a user gesture.
     *
     * @param fullscreen whether to be fullscreen
     */
    void setFullscreen(boolean fullscreen);

    /**
     * Turns VSync on or off. Ignored on the web.
     *
     * @param vsync whether to synchronise with the display
     */
    void setVsync(boolean vsync);

    /**
     * Sets the cursor behaviour.
     *
     * @param mode the new mode
     */
    void setCursorMode(CursorMode mode);

    /**
     * Shows a standard system cursor.
     *
     * @param shape 0 arrow, 1 hand, 2 text, 3 crosshair, 4 horizontal resize, 5 vertical resize, 6 move, 7 not allowed
     */
    void setSystemCursor(int shape);

    /**
     * Shows a custom cursor image.
     *
     * @param image RGBA image, at most 128 by 128 pixels
     * @param hotX horizontal click point in the image
     * @param hotY vertical click point in the image
     */
    void setCustomCursor(DecodedImage image, int hotX, int hotY);

    /**
     * Returns whether the window has input focus.
     *
     * @return {@code true} if focused
     */
    boolean isFocused();

    /**
     * Returns whether the window was asked to close and should stop.
     *
     * @return {@code true} after the user closed the window or {@link #requestClose()} was called
     */
    boolean shouldClose();

    /** Marks the window for closing; the loop ends after the current frame. */
    void requestClose();

    /**
     * Sets the listener for size, focus and close events.
     *
     * @param listener the listener, or {@code null} to remove it
     */
    void setListener(@Nullable WindowListener listener);
}
