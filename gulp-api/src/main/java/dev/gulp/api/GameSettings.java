package dev.gulp.api;

import dev.gulp.api.graphics.Color;

/**
 * Settings a game chooses in {@link Game#configure(GameSettings)}, before the platform starts.
 *
 * <p>Setters are fluent and validate their arguments immediately:
 *
 * <pre>{@code
 * settings.title("Coin Hunter")
 *         .windowSize(960, 540)
 *         .vsync(true)
 *         .ticksPerSecond(60)
 *         .clearColor(Color.rgb(0x1d2b53));
 * }</pre>
 *
 * <p>Changing settings after {@code configure} returns has no effect.
 */
public final class GameSettings {

    private String title = "Gulp";
    private int windowWidth = 1280;
    private int windowHeight = 720;
    private boolean resizable = true;
    private boolean fullscreen = false;
    private boolean vsync = true;
    private int targetFps = 0;
    private int ticksPerSecond = 60;
    private Color clearColor = Color.BLACK;

    /** Creates settings with default values: 1280x720 resizable window, VSync on, 60 ticks per second. */
    public GameSettings() {}

    /**
     * Returns the window title.
     *
     * @return the window title, {@code "Gulp"} by default
     */
    public String title() {
        return title;
    }

    /**
     * Sets the window title (browser tab title on the web).
     *
     * @param title the new title
     * @return this settings object
     */
    public GameSettings title(String title) {
        this.title = title;
        return this;
    }

    /**
     * Returns the initial window width.
     *
     * @return width in logical points
     */
    public int windowWidth() {
        return windowWidth;
    }

    /**
     * Returns the initial window height.
     *
     * @return height in logical points
     */
    public int windowHeight() {
        return windowHeight;
    }

    /**
     * Sets the initial window size in logical points (HiDPI scaling is applied by the platform).
     *
     * @param width width, at least 1
     * @param height height, at least 1
     * @return this settings object
     * @throws IllegalArgumentException if either dimension is smaller than 1
     */
    public GameSettings windowSize(int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Window size must be at least 1x1, got " + width + "x" + height);
        }
        this.windowWidth = width;
        this.windowHeight = height;
        return this;
    }

    /**
     * Returns whether the user can resize the window.
     *
     * @return {@code true} by default
     */
    public boolean isResizable() {
        return resizable;
    }

    /**
     * Sets whether the user can resize the window. Ignored on the web.
     *
     * @param resizable whether resizing is allowed
     * @return this settings object
     */
    public GameSettings resizable(boolean resizable) {
        this.resizable = resizable;
        return this;
    }

    /**
     * Returns whether the game starts in fullscreen.
     *
     * @return {@code false} by default
     */
    public boolean isFullscreen() {
        return fullscreen;
    }

    /**
     * Sets whether the game starts in fullscreen. On the web fullscreen needs a user gesture, so it is entered on the
     * first click.
     *
     * @param fullscreen whether to start in fullscreen
     * @return this settings object
     */
    public GameSettings fullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        return this;
    }

    /**
     * Returns whether rendering is synchronised with the display refresh rate.
     *
     * @return {@code true} by default
     */
    public boolean isVsync() {
        return vsync;
    }

    /**
     * Sets whether rendering is synchronised with the display refresh rate. The web always uses the browser's
     * animation frame rate.
     *
     * @param vsync whether VSync is on
     * @return this settings object
     */
    public GameSettings vsync(boolean vsync) {
        this.vsync = vsync;
        return this;
    }

    /**
     * Returns the frame rate limit.
     *
     * @return frames per second, {@code 0} means no limit
     */
    public int targetFps() {
        return targetFps;
    }

    /**
     * Limits rendered frames per second. Game logic always runs at {@link #ticksPerSecond()}.
     *
     * @param targetFps frames per second, {@code 0} for no limit
     * @return this settings object
     * @throws IllegalArgumentException if the value is negative
     */
    public GameSettings targetFps(int targetFps) {
        if (targetFps < 0) {
            throw new IllegalArgumentException("targetFps must be 0 or positive, got " + targetFps);
        }
        this.targetFps = targetFps;
        return this;
    }

    /**
     * Returns the fixed logic rate.
     *
     * @return ticks per second, {@code 60} by default
     */
    public int ticksPerSecond() {
        return ticksPerSecond;
    }

    /**
     * Sets the fixed logic rate. Scheduler delays, cooldowns and lifetimes are counted in these ticks.
     *
     * @param ticksPerSecond ticks per second, between 1 and 1000
     * @return this settings object
     * @throws IllegalArgumentException if the value is out of range
     */
    public GameSettings ticksPerSecond(int ticksPerSecond) {
        if (ticksPerSecond < 1 || ticksPerSecond > 1000) {
            throw new IllegalArgumentException("ticksPerSecond must be between 1 and 1000, got " + ticksPerSecond);
        }
        this.ticksPerSecond = ticksPerSecond;
        return this;
    }

    /**
     * Returns the color the screen is cleared with before every frame.
     *
     * @return the clear color, {@link Color#BLACK} by default
     */
    public Color clearColor() {
        return clearColor;
    }

    /**
     * Sets the color the screen is cleared with before every frame.
     *
     * @param clearColor the background color
     * @return this settings object
     */
    public GameSettings clearColor(Color clearColor) {
        this.clearColor = clearColor;
        return this;
    }
}
