package dev.gulp.backend.desktop;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.spi.GameLauncher;
import dev.gulp.core.GulpRuntime;

/**
 * Launcher for Windows, macOS and Linux. Chosen by {@code Gulp.launch} whenever this backend is on the classpath.
 *
 * <p>Order: (macOS only) restart with {@code -XstartOnFirstThread} if needed, {@code configure}, create the window and
 * OpenGL context, then run the game until the window closes.
 *
 * <pre>{@code
 * // build.gradle.kts of a game
 * runtimeOnly("dev.gulp:gulp-backend-desktop")
 * }</pre>
 */
public final class DesktopGameLauncher implements GameLauncher {

    /** Backend name. */
    public static final String NAME = "desktop";

    /** Creates the launcher; instantiated by {@link java.util.ServiceLoader}. */
    public DesktopGameLauncher() {}

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public void launch(Game game) {
        MacOsFirstThread.relaunchIfNeeded();
        GameSettings settings = GulpRuntime.configure(game);
        DesktopBackend backend = DesktopBackend.create(GulpRuntime.windowConfig(settings));
        try {
            new GulpRuntime(game, settings, backend).start();
        } finally {
            backend.dispose();
        }
    }
}
