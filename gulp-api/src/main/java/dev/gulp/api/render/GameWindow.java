package dev.gulp.api.render;

import java.util.List;

/**
 * The game window: title, size, fullscreen, borderless mode, VSync and the monitor it uses. Settings screens change
 * these while the game runs; {@link dev.gulp.api.GameSettings} gives the values at start.
 *
 * <p>On the web the window is the canvas: fullscreen uses the browser Fullscreen API (it needs a user gesture, so call
 * it from a click or key handler), the size follows the page, and there is one monitor. Borderless mode and VSync have
 * no effect there.
 *
 * <pre>{@code
 * GameWindow window = display().window();
 * window.setFullscreen(!window.isFullscreen());
 * window.setVsync(false);
 * if (window.monitors().size() > 1) {
 *     window.setMonitor(1);
 * }
 * }</pre>
 */
public interface GameWindow {

    /**
     * Returns the title.
     *
     * @return the window title
     */
    String title();

    /**
     * Changes the title.
     *
     * @param title the new title
     */
    void setTitle(String title);

    /**
     * Returns the width.
     *
     * @return width in logical points
     */
    int width();

    /**
     * Returns the height.
     *
     * @return height in logical points
     */
    int height();

    /**
     * Resizes the window when it is not fullscreen.
     *
     * @param width width in logical points
     * @param height height in logical points
     */
    void setSize(int width, int height);

    /**
     * Returns whether the window fills its monitor.
     *
     * @return {@code true} in fullscreen
     */
    boolean isFullscreen();

    /**
     * Switches fullscreen on the current monitor on or off.
     *
     * @param fullscreen whether to fill the monitor
     */
    void setFullscreen(boolean fullscreen);

    /**
     * Returns whether the window has no frame or title bar.
     *
     * @return {@code true} if borderless
     */
    boolean isBorderless();

    /**
     * Hides or shows the window frame and title bar (desktop).
     *
     * @param borderless whether to hide the frame
     */
    void setBorderless(boolean borderless);

    /**
     * Returns whether frames wait for the display refresh.
     *
     * @return {@code true} if VSync is on
     */
    boolean isVsync();

    /**
     * Turns VSync on or off (desktop).
     *
     * @param vsync whether to wait for the display refresh
     */
    void setVsync(boolean vsync);

    /**
     * Returns the names of the connected monitors; the first one is the primary monitor.
     *
     * @return monitor names, at least one
     */
    List<String> monitors();

    /**
     * Returns the monitor the window uses.
     *
     * @return an index into {@link #monitors()}
     */
    int monitor();

    /**
     * Moves the window to another monitor (centred), or switches its fullscreen to that monitor.
     *
     * @param index an index into {@link #monitors()}
     * @throws IndexOutOfBoundsException if there is no such monitor
     */
    void setMonitor(int index);

    /**
     * Returns whether the window has the keyboard focus.
     *
     * @return {@code true} if focused
     */
    boolean isFocused();
}
