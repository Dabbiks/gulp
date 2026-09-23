package dev.gulp.backend.web;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.spi.GameLauncher;
import dev.gulp.core.GulpEngine;

/**
 * Starts games in the browser. {@link #launch(Game)} returns after scheduling the first frame; the browser drives the
 * rest.
 *
 * <pre>{@code
 * Gulp.launch(new CoinGame()); // picks this launcher in a TeaVM build
 * }</pre>
 */
public final class WebGameLauncher implements GameLauncher {

    /** Backend name, for {@code -Dgulp.backend}. */
    public static final String NAME = "web";

    /** Creates the launcher; instantiated by {@link java.util.ServiceLoader}. */
    public WebGameLauncher() {}

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
        try {
            GameSettings settings = GulpEngine.configure(game);
            WebBackend backend = WebBackend.create(game.id(), settings.title());
            new GulpEngine(game, settings, backend).start();
        } catch (Throwable error) {
            Js.fatal("The game failed to start: " + error);
            throw error;
        }
    }
}
