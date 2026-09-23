package dev.gulp.api;

/**
 * Main class of a game, with a lifecycle driven by the engine.
 *
 * <p>The engine calls the methods in this order: {@link #configure(GameSettings)} before the platform starts,
 * {@link #onLoad()} before registries are frozen and assets are loaded, {@link #onStart()} once the game is ready,
 * and {@link #onStop()} when it shuts down.
 *
 * <pre>{@code
 * public final class CoinGame extends Game {
 *     @Override public String id() { return "coins"; }
 *
 *     @Override public void configure(GameSettings settings) {
 *         settings.title("Coin Hunter").windowSize(960, 540).clearColor(Color.rgb(0x1d2b53));
 *     }
 *
 *     @Override public void onStart() {
 *         // open the first world or screen
 *     }
 * }
 * }</pre>
 */
public abstract class Game {

    /** Creates a game. Subclasses must not touch engine services in the constructor. */
    protected Game() {}

    /**
     * Returns the namespace of this game, used for its keys, config and save files.
     *
     * @return a namespace matching {@code [a-z0-9_.-]+}, for example {@code "coins"}
     */
    public abstract String id();

    /**
     * Adjusts window, resolution and tick settings before the platform starts. Called exactly once.
     *
     * @param settings mutable settings, pre-filled with defaults
     */
    public void configure(GameSettings settings) {}

    /** Registers content types in registries. Called after the platform starts and before assets load. */
    public void onLoad() {}

    /** Starts the game: opens the first world or screen. Called once all startup assets are loaded. */
    public abstract void onStart();

    /** Releases game-wide state. Called once when the engine stops, after every module is disabled. */
    public void onStop() {}
}
