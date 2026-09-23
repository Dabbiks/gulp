package dev.gulp.api;

import dev.gulp.api.data.Config;
import dev.gulp.api.registry.Key;

/**
 * Main class of a game, with a lifecycle driven by the engine. It owns everything it registers, like a
 * {@link dev.gulp.api.module.GameModule} does, and has the same shortcuts ({@link Owner}).
 *
 * <p>The engine calls the methods in this order: {@link #configure(GameSettings)} before the platform starts,
 * {@link #onLoad()} before registries are frozen and assets are loaded, {@link #onStart()} once the game is ready (then
 * modules are enabled), and {@link #onStop()} when it shuts down (after modules are disabled).
 *
 * <pre>{@code
 * public final class CoinGame extends Game {
 *     @Override public String id() { return "coins"; }
 *
 *     @Override public void configure(GameSettings settings) {
 *         settings.title("Coin Hunter").windowSize(960, 540).modules(new HudModule(), new CoinModule());
 *     }
 *
 *     @Override public void onLoad() {
 *         registries().create(key("items"), Item.class);
 *     }
 *
 *     @Override public void onStart() {
 *         logger().info("Difficulty: " + config().getString("difficulty", "normal"));
 *     }
 * }
 * }</pre>
 */
public abstract class Game implements Owner {

    /** Creates a game. Subclasses must not touch engine services in the constructor. */
    protected Game() {}

    /**
     * Returns the namespace of this game, used for its keys, config and save files.
     *
     * @return a namespace matching {@code [a-z0-9_.-]+}, for example {@code "coins"}
     */
    @Override
    public abstract String id();

    /**
     * Adjusts window, tick rate and modules before the platform starts. Called exactly once; engine services are not
     * available yet.
     *
     * @param settings mutable settings, pre-filled with defaults
     */
    public void configure(GameSettings settings) {}

    /** Registers content types in registries. Called after configs are loaded and before registries are frozen. */
    public void onLoad() {}

    /** Starts the game: opens the first world or screen. Called once startup is done, before modules are enabled. */
    public abstract void onStart();

    /** Releases game-wide state. Called once when the engine stops, after every module is disabled. */
    public void onStop() {}

    /**
     * Returns the running engine.
     *
     * @return the engine
     */
    @Override
    public final Engine engine() {
        return Gulp.engine();
    }

    /**
     * Returns the game logger, named after {@link #id()}.
     *
     * @return the logger
     */
    @Override
    public final Logger logger() {
        return engine().modules().logger(this);
    }

    /**
     * Returns the game configuration from {@code config/game.yml}, with defaults from
     * {@code assets/<id>/config/game.yml}.
     *
     * @return the configuration
     */
    @Override
    public final Config config() {
        return engine().modules().config(this);
    }

    /**
     * Creates a key in this game's namespace.
     *
     * @param path key path matching {@code [a-z0-9_./-]+}
     * @return the key {@code <id>:<path>}
     */
    @Override
    public final Key key(String path) {
        return Key.of(id(), path);
    }

    /**
     * Returns whether the game is running.
     *
     * @return {@code true} from {@code onLoad} until {@code onStop}
     */
    @Override
    public final boolean isEnabled() {
        return Gulp.isRunning() && engine().game() == this;
    }
}
