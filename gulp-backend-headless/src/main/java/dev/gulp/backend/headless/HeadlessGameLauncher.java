package dev.gulp.backend.headless;

import dev.gulp.api.Game;
import dev.gulp.api.spi.GameLauncher;

/**
 * Launcher used by {@code Gulp.launch} when only the headless backend is on the classpath, or with
 * {@code -Dgulp.backend=headless}. Runs frames as fast as possible until the game stops; the system property
 * {@value #MAX_FRAMES_PROPERTY} limits the number of frames (useful for simulations in CI).
 *
 * <pre>{@code
 * java -Dgulp.backend=headless -Dgulp.headless.maxFrames=600 -cp ... com.example.CoinGame
 * }</pre>
 */
public final class HeadlessGameLauncher implements GameLauncher {

    /** Backend name. */
    public static final String NAME = "headless";

    /** System property limiting the number of frames; {@code 0} or absent means no limit. */
    public static final String MAX_FRAMES_PROPERTY = "gulp.headless.maxFrames";

    /** Creates the launcher; instantiated by {@link java.util.ServiceLoader}. */
    public HeadlessGameLauncher() {}

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public void launch(Game game) {
        HeadlessRunner runner = HeadlessRunner.start(game);
        try {
            runner.backend().loop().runUntilStopped(Long.getLong(MAX_FRAMES_PROPERTY, 0L));
            Throwable failure = runner.engine().failure();
            if (failure != null) {
                throw new IllegalStateException("The game failed to start", failure);
            }
        } finally {
            runner.stop();
        }
    }
}
