package dev.gulp.platform;

/**
 * Receives window changes. Called on the main thread, before the next frame.
 *
 * <pre>{@code
 * window.setListener(new WindowListener() { ... });
 * }</pre>
 */
public interface WindowListener {

    /**
     * The window or its framebuffer changed size.
     *
     * @param width new width in logical points
     * @param height new height in logical points
     * @param framebufferWidth new width in physical pixels
     * @param framebufferHeight new height in physical pixels
     */
    void resized(int width, int height, int framebufferWidth, int framebufferHeight);

    /**
     * The window gained or lost focus; on the web also when the tab is hidden or shown.
     *
     * @param focused whether the window now has focus
     */
    void focusChanged(boolean focused);

    /**
     * The ratio between physical pixels and logical points changed, for example after moving to another monitor.
     *
     * @param scale the new content scale
     */
    void contentScaleChanged(float scale);

    /** The user asked to close the window. The engine decides whether to stop. */
    void closeRequested();
}
