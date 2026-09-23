package dev.gulp.backend.desktop;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.spi.GameLauncher;
import dev.gulp.core.GulpEngine;

/**
 * Launcher for Windows, macOS and Linux. Chosen by {@code Gulp.launch} whenever this backend is on the classpath.
 *
 * <p>Order: (macOS only) restart with {@code -XstartOnFirstThread} if needed, {@code configure}, create the window and
 * OpenGL context, then run the game until the window closes or the game stops.
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
        GameSettings settings = GulpEngine.configure(game);
        DesktopBackend backend = DesktopBackend.create(game.id(), GulpEngine.windowConfig(settings));
        Throwable failure;
        try {
            GulpEngine engine = new GulpEngine(game, settings, backend);
            engine.start();
            failure = engine.failure();
        } finally {
            backend.dispose();
        }
        if (failure != null) {
            throw new IllegalStateException("The game failed to start", failure);
        }
    }
}
