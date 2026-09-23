package dev.gulp.api.spi;

import dev.gulp.api.Game;

/**
 * Starts a game on one backend. Implemented by backends and registered in
 * {@code META-INF/services/dev.gulp.api.spi.GameLauncher}; game code never implements or calls it directly and uses
 * {@link dev.gulp.api.Gulp#launch(Game)} instead.
 *
 * <pre>{@code
 * public final class DesktopGameLauncher implements GameLauncher {
 *     @Override public String name() { return "desktop"; }
 *     @Override public int priority() { return 100; }
 *     @Override public void launch(Game game) { ... }
 * }
 * }</pre>
 */
public interface GameLauncher {

    /**
     * Returns the backend name used with {@code -Dgulp.backend=<name>}.
     *
     * @return a short lowercase name such as {@code "desktop"}
     */
    String name();

    /**
     * Returns the priority used when several backends are on the classpath; the highest wins.
     *
     * @return the priority; real platforms use {@code 100}, test backends {@code 0}
     */
    int priority();

    /**
     * Configures the game, starts the platform and runs the game loop.
     *
     * @param game the game to run
     */
    void launch(Game game);
}
