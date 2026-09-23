package dev.gulp.api;

import dev.gulp.api.spi.GameLauncher;
import java.util.ServiceLoader;
import org.jspecify.annotations.Nullable;

/**
 * Static facade of the framework and the entry point of every game.
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *     Gulp.launch(new CoinGame());
 * }
 * }</pre>
 *
 * <p>The backend is not chosen by game code: {@link #launch(Game)} picks the highest-priority {@link GameLauncher}
 * found on the runtime classpath (the desktop backend in a desktop build, the headless backend in tests). Set the
 * system property {@value #BACKEND_PROPERTY} to force a backend by name, for example {@code -Dgulp.backend=headless}.
 */
public final class Gulp {

    /** System property that forces a backend by its {@link GameLauncher#name() name}. */
    public static final String BACKEND_PROPERTY = "gulp.backend";

    private Gulp() {}

    /**
     * Starts the game on the best available backend. On desktop this call blocks until the game stops; on the web it
     * returns after the first frame is scheduled.
     *
     * @param game the game to run
     * @throws IllegalStateException if no backend is on the classpath or the forced backend is missing
     */
    public static void launch(Game game) {
        select(ServiceLoader.load(GameLauncher.class), System.getProperty(BACKEND_PROPERTY))
                .launch(game);
    }

    /**
     * Chooses a launcher: the one named {@code forcedName} if given, otherwise the one with the highest priority.
     *
     * @param launchers candidate launchers
     * @param forcedName launcher name to force, or {@code null} to choose by priority
     * @return the chosen launcher
     * @throws IllegalStateException if there is no candidate or the forced one is missing
     */
    static GameLauncher select(Iterable<GameLauncher> launchers, @Nullable String forcedName) {
        GameLauncher best = null;
        StringBuilder available = new StringBuilder();
        for (GameLauncher launcher : launchers) {
            if (!available.isEmpty()) {
                available.append(", ");
            }
            available.append(launcher.name());
            if (forcedName != null) {
                if (launcher.name().equals(forcedName)) {
                    return launcher;
                }
            } else if (best == null || launcher.priority() > best.priority()) {
                best = launcher;
            }
        }
        if (forcedName != null) {
            throw new IllegalStateException("Backend '" + forcedName + "' requested by -D" + BACKEND_PROPERTY
                    + " is not on the classpath. Available: [" + available + "]");
        }
        if (best == null) {
            throw new IllegalStateException("No Gulp backend on the classpath. Add gulp-backend-desktop (or"
                    + " gulp-backend-headless for tests) as a runtime dependency.");
        }
        return best;
    }
}
