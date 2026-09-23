package dev.gulp.api;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.render.AspectMode;
import dev.gulp.api.render.StretchMode;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

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
    private final List<GameModule> modules = new ArrayList<>();
    private @Nullable Boolean developerConsole;
    private int baseWidth;
    private int baseHeight;
    private StretchMode stretchMode = StretchMode.DISABLED;
    private AspectMode aspectMode = AspectMode.KEEP;
    private boolean integerScaling;
    private boolean pixelSnap;
    private Color letterboxColor = Color.BLACK;
    private int pixelsPerUnit = 16;
    private String defaultLocale = "en_us";
    private @Nullable String locale;

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

    /**
     * Returns the base resolution width.
     *
     * @return pixels, {@code 0} when stretching is disabled
     */
    public int baseWidth() {
        return baseWidth;
    }

    /**
     * Returns the base resolution height.
     *
     * @return pixels, {@code 0} when stretching is disabled
     */
    public int baseHeight() {
        return baseHeight;
    }

    /**
     * Sets the resolution the game and its UI are designed for. If the stretch mode is still
     * {@link StretchMode#DISABLED}, it becomes {@link StretchMode#CANVAS}.
     *
     * @param width width in pixels, positive
     * @param height height in pixels, positive
     * @return this settings object
     * @throws IllegalArgumentException if a size is not positive
     */
    public GameSettings baseResolution(int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Base resolution must be positive, got " + width + "x" + height);
        }
        this.baseWidth = width;
        this.baseHeight = height;
        if (stretchMode == StretchMode.DISABLED) {
            stretchMode = StretchMode.CANVAS;
        }
        return this;
    }

    /**
     * Returns the stretch mode.
     *
     * @return {@link StretchMode#DISABLED} by default
     */
    public StretchMode stretchMode() {
        return stretchMode;
    }

    /**
     * Sets how the base resolution maps to the window.
     *
     * @param stretchMode the mode
     * @return this settings object
     */
    public GameSettings stretchMode(StretchMode stretchMode) {
        this.stretchMode = stretchMode;
        return this;
    }

    /**
     * Returns the aspect mode.
     *
     * @return {@link AspectMode#KEEP} by default
     */
    public AspectMode aspectMode() {
        return aspectMode;
    }

    /**
     * Sets how other aspect ratios are handled.
     *
     * @param aspectMode the mode
     * @return this settings object
     */
    public GameSettings aspectMode(AspectMode aspectMode) {
        this.aspectMode = aspectMode;
        return this;
    }

    /**
     * Returns whether scaling uses whole multiples.
     *
     * @return {@code false} by default
     */
    public boolean isIntegerScaling() {
        return integerScaling;
    }

    /**
     * Scales only by whole multiples, for pixel art without blur.
     *
     * @param integerScaling whether to use integer scaling
     * @return this settings object
     */
    public GameSettings integerScaling(boolean integerScaling) {
        this.integerScaling = integerScaling;
        return this;
    }

    /**
     * Returns whether world positions snap to base pixels.
     *
     * @return {@code false} by default
     */
    public boolean isPixelSnap() {
        return pixelSnap;
    }

    /**
     * Rounds world drawing positions to base pixels.
     *
     * @param pixelSnap whether to snap
     * @return this settings object
     */
    public GameSettings pixelSnap(boolean pixelSnap) {
        this.pixelSnap = pixelSnap;
        return this;
    }

    /**
     * Returns the color of bars around the game area.
     *
     * @return black by default
     */
    public Color letterboxColor() {
        return letterboxColor;
    }

    /**
     * Sets the color of bars around the game area.
     *
     * @param letterboxColor the color
     * @return this settings object
     */
    public GameSettings letterboxColor(Color letterboxColor) {
        this.letterboxColor = letterboxColor;
        return this;
    }

    /**
     * Returns how many base pixels one world unit (one tile) covers at camera zoom 1.
     *
     * @return pixels per unit, {@code 16} by default
     */
    public int pixelsPerUnit() {
        return pixelsPerUnit;
    }

    /**
     * Sets how many base pixels one world unit covers, usually the tile size of the art.
     *
     * @param pixelsPerUnit pixels, positive
     * @return this settings object
     * @throws IllegalArgumentException if not positive
     */
    public GameSettings pixelsPerUnit(int pixelsPerUnit) {
        if (pixelsPerUnit < 1) {
            throw new IllegalArgumentException("pixelsPerUnit must be positive, got " + pixelsPerUnit);
        }
        this.pixelsPerUnit = pixelsPerUnit;
        return this;
    }

    /**
     * Returns the language used when a translation is missing in the current one.
     *
     * @return a locale such as {@code en_us}, the default
     */
    public String defaultLocale() {
        return defaultLocale;
    }

    /**
     * Sets the language used when a translation is missing in the current one.
     *
     * @param defaultLocale a locale such as {@code pl_pl}
     * @return this settings object
     */
    public GameSettings defaultLocale(String defaultLocale) {
        this.defaultLocale = normalizeLocale(defaultLocale);
        return this;
    }

    /**
     * Returns the language to start with.
     *
     * @return a locale, or {@code null} to use the system language
     */
    public @Nullable String locale() {
        return locale;
    }

    /**
     * Sets the language to start with instead of the system language.
     *
     * @param locale a locale such as {@code pl_pl}, or {@code null} for the system language
     * @return this settings object
     */
    public GameSettings locale(@Nullable String locale) {
        this.locale = locale == null ? null : normalizeLocale(locale);
        return this;
    }

    /**
     * Normalizes a locale to lower case with an underscore: {@code pl-PL} becomes {@code pl_pl}.
     *
     * @param locale the locale
     * @return the normalized locale
     */
    public static String normalizeLocale(String locale) {
        return locale.trim().replace('-', '_').toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Returns the declared modules, in declaration order.
     *
     * @return an unmodifiable list
     */
    public List<GameModule> modules() {
        return List.copyOf(modules);
    }

    /**
     * Declares the game's modules. The engine orders them by their dependencies, not by this order. Each module class
     * must be annotated with {@link dev.gulp.api.module.ModuleInfo}.
     *
     * <pre>{@code
     * settings.modules(new HudModule(), new PlayerModule(), new CoinModule());
     * }</pre>
     *
     * @param modules the modules to add
     * @return this settings object
     * @throws IllegalArgumentException if the same instance is added twice
     */
    public GameSettings modules(GameModule... modules) {
        for (GameModule module : modules) {
            for (GameModule existing : this.modules) {
                if (existing == module) {
                    throw new IllegalArgumentException("Module declared twice: " + module);
                }
            }
            this.modules.add(module);
        }
        return this;
    }

    /**
     * Returns the explicit developer console choice.
     *
     * @return {@code true} or {@code false} if set, {@code null} for the default: available in development builds only
     */
    public @Nullable Boolean developerConsole() {
        return developerConsole;
    }

    /**
     * Enables or disables the developer console (in-game under {@code ~} from stage 9, and the terminal on desktop).
     * By default it is available in development builds only; {@code true} enables it in production builds too.
     *
     * @param developerConsole whether the console is available
     * @return this settings object
     */
    public GameSettings developerConsole(boolean developerConsole) {
        this.developerConsole = developerConsole;
        return this;
    }
}
