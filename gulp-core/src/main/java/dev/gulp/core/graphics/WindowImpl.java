package dev.gulp.core.graphics;

import dev.gulp.api.GameSettings;
import dev.gulp.api.data.Preferences;
import dev.gulp.api.render.GameWindow;
import dev.gulp.platform.PlatformWindow;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** The game window on top of the platform window, remembering what the platform does not report back. */
public final class WindowImpl implements GameWindow {

    private final PlatformWindow window;
    private String title;
    private boolean vsync;
    private @Nullable Preferences preferences;

    /** Preference keys of the window settings. */
    static final String FULLSCREEN = "gulp.window.fullscreen";

    static final String BORDERLESS = "gulp.window.borderless";
    static final String VSYNC = "gulp.window.vsync";
    static final String MONITOR = "gulp.window.monitor";

    /**
     * Wraps the platform window.
     *
     * @param window the platform window
     * @param settings the start settings (title and VSync; borderless mode and monitor are applied by {@link #apply})
     */
    public WindowImpl(PlatformWindow window, GameSettings settings) {
        this.window = window;
        this.title = settings.title();
        this.vsync = settings.isVsync();
    }

    /**
     * Applies the start settings the platform window was not created with.
     *
     * @param settings the game settings
     */
    public void apply(GameSettings settings) {
        if (settings.monitor() > 0 && settings.monitor() < window.monitors().size()) {
            window.setMonitor(settings.monitor());
        }
        if (settings.isBorderless()) {
            window.setBorderless(true);
        }
    }

    /**
     * Restores the window settings the player chose, and remembers later changes there.
     *
     * @param store the preferences
     */
    public void loadPreferences(Preferences store) {
        if (store.has(MONITOR)) {
            int index = store.getInt(MONITOR, 0);
            if (index >= 0 && index < window.monitors().size()) {
                window.setMonitor(index);
            }
        }
        if (store.has(BORDERLESS)) {
            window.setBorderless(store.getBoolean(BORDERLESS, false));
        }
        if (store.has(VSYNC)) {
            vsync = store.getBoolean(VSYNC, vsync);
            window.setVsync(vsync);
        }
        if (store.has(FULLSCREEN)) {
            window.setFullscreen(store.getBoolean(FULLSCREEN, false));
        }
        this.preferences = store;
    }

    private void remember(String key, boolean value) {
        Preferences store = preferences;
        if (store != null) {
            store.set(key, value);
        }
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public void setTitle(String title) {
        this.title = Objects.requireNonNull(title, "title");
        window.setTitle(title);
    }

    @Override
    public int width() {
        return window.width();
    }

    @Override
    public int height() {
        return window.height();
    }

    @Override
    public void setSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Window size must be positive: " + width + "x" + height);
        }
        window.setSize(width, height);
    }

    @Override
    public boolean isFullscreen() {
        return window.isFullscreen();
    }

    @Override
    public void setFullscreen(boolean fullscreen) {
        window.setFullscreen(fullscreen);
        remember(FULLSCREEN, fullscreen);
    }

    @Override
    public boolean isBorderless() {
        return window.isBorderless();
    }

    @Override
    public void setBorderless(boolean borderless) {
        window.setBorderless(borderless);
        remember(BORDERLESS, borderless);
    }

    @Override
    public boolean isVsync() {
        return vsync;
    }

    @Override
    public void setVsync(boolean vsync) {
        this.vsync = vsync;
        window.setVsync(vsync);
        remember(VSYNC, vsync);
    }

    @Override
    public List<String> monitors() {
        return window.monitors();
    }

    @Override
    public int monitor() {
        return window.monitor();
    }

    @Override
    public void setMonitor(int index) {
        List<String> monitors = window.monitors();
        if (index < 0 || index >= monitors.size()) {
            throw new IndexOutOfBoundsException("No monitor " + index + " among " + monitors);
        }
        window.setMonitor(index);
        Preferences store = preferences;
        if (store != null) {
            store.set(MONITOR, index);
        }
    }

    @Override
    public boolean isFocused() {
        return window.isFocused();
    }
}
