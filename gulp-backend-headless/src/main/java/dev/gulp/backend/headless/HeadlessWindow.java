package dev.gulp.backend.headless;

import dev.gulp.platform.CursorMode;
import dev.gulp.platform.DecodedImage;
import dev.gulp.platform.PlatformWindow;
import dev.gulp.platform.WindowConfig;
import dev.gulp.platform.WindowListener;
import org.jspecify.annotations.Nullable;

/**
 * Window of a given size that exists only in memory. Tests can simulate resizing, focus changes and closing.
 *
 * <pre>{@code
 * backend.window().simulateResize(640, 360, 2f);
 * backend.window().simulateCloseRequest();
 * }</pre>
 */
public final class HeadlessWindow implements PlatformWindow {

    private String title;
    private int width;
    private int height;
    private float contentScale = 1f;
    private boolean fullscreen;
    private boolean vsync;
    private boolean focused = true;
    private boolean shouldClose;
    private CursorMode cursorMode = CursorMode.NORMAL;
    private @Nullable DecodedImage icon;
    private @Nullable WindowListener listener;

    HeadlessWindow(WindowConfig config) {
        this.title = config.title();
        this.width = config.width();
        this.height = config.height();
        this.fullscreen = config.fullscreen();
        this.vsync = config.vsync();
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public int framebufferWidth() {
        return Math.round(width * contentScale);
    }

    @Override
    public int framebufferHeight() {
        return Math.round(height * contentScale);
    }

    @Override
    public float contentScale() {
        return contentScale;
    }

    /**
     * Returns the current title.
     *
     * @return the title
     */
    public String title() {
        return title;
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Returns the icon set last.
     *
     * @return the icon, or {@code null} if none was set
     */
    public @Nullable DecodedImage icon() {
        return icon;
    }

    @Override
    public void setIcon(DecodedImage icon) {
        this.icon = icon;
    }

    @Override
    public void setSize(int width, int height) {
        simulateResize(width, height, contentScale);
    }

    @Override
    public boolean isFullscreen() {
        return fullscreen;
    }

    @Override
    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    /**
     * Returns whether VSync was requested.
     *
     * @return the last VSync setting
     */
    public boolean isVsync() {
        return vsync;
    }

    @Override
    public void setVsync(boolean vsync) {
        this.vsync = vsync;
    }

    /**
     * Returns the cursor mode set last.
     *
     * @return the cursor mode
     */
    public CursorMode cursorMode() {
        return cursorMode;
    }

    @Override
    public void setCursorMode(CursorMode mode) {
        this.cursorMode = mode;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public boolean shouldClose() {
        return shouldClose;
    }

    @Override
    public void requestClose() {
        shouldClose = true;
    }

    @Override
    public void setListener(@Nullable WindowListener listener) {
        this.listener = listener;
    }

    /**
     * Changes the size and content scale and notifies the listener.
     *
     * @param width new width in logical points
     * @param height new height in logical points
     * @param contentScale new ratio of pixels to points
     */
    public void simulateResize(int width, int height, float contentScale) {
        if (width < 1 || height < 1 || !(contentScale > 0f)) {
            throw new IllegalArgumentException("Invalid size " + width + "x" + height + " @" + contentScale);
        }
        boolean scaleChanged = this.contentScale != contentScale;
        this.width = width;
        this.height = height;
        this.contentScale = contentScale;
        if (listener != null) {
            if (scaleChanged) {
                listener.contentScaleChanged(contentScale);
            }
            listener.resized(width, height, framebufferWidth(), framebufferHeight());
        }
    }

    /**
     * Changes focus and notifies the listener.
     *
     * @param focused whether the window has focus
     */
    public void simulateFocus(boolean focused) {
        this.focused = focused;
        if (listener != null) {
            listener.focusChanged(focused);
        }
    }

    /** Simulates the user closing the window: notifies the listener and marks the window for closing. */
    public void simulateCloseRequest() {
        if (listener != null) {
            listener.closeRequested();
        }
        shouldClose = true;
    }
}
