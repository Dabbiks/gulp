package dev.gulp.backend.headless;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.core.GulpEngine;
import java.util.function.Consumer;

/**
 * Starts a game on the headless backend and lets a test advance it frame by frame, with simulated time (one frame is
 * one tick at the default 60 ticks per second and 60 frames per second).
 *
 * <pre>{@code
 * HeadlessRunner runner = HeadlessRunner.start(new CoinGame());
 * runner.step(1);                      // configs load, onLoad and onStart run, modules are enabled
 * runner.step(60);                     // one simulated second
 * assertThat(runner.engine().tick()).isEqualTo(60);
 * runner.stop();
 * }</pre>
 */
public final class HeadlessRunner {

    private final GameSettings settings;
    private final HeadlessBackend backend;
    private final GulpEngine engine;

    private HeadlessRunner(GameSettings settings, HeadlessBackend backend, GulpEngine engine) {
        this.settings = settings;
        this.backend = backend;
        this.engine = engine;
    }

    /**
     * Configures the game, creates the backend and the engine and starts loading. The game itself starts on the first
     * {@link #step(int)}.
     *
     * @param game the game
     * @return the runner
     * @throws IllegalStateException if the game or its modules are invalid
     */
    public static HeadlessRunner start(Game game) {
        return start(game, backend -> {});
    }

    /**
     * Like {@link #start(Game)}, but lets the test prepare the backend first, for example to add config assets.
     *
     * <pre>{@code
     * HeadlessRunner.start(game, backend ->
     *         backend.files().putAsset("coins/config/game.yml", "lives: 5".getBytes(UTF_8)));
     * }</pre>
     *
     * @param game the game
     * @param prepare called with the backend before the engine starts
     * @return the runner
     */
    public static HeadlessRunner start(Game game, Consumer<HeadlessBackend> prepare) {
        GameSettings settings = GulpEngine.configure(game);
        HeadlessBackend backend = new HeadlessBackend(GulpEngine.windowConfig(settings));
        prepare.accept(backend);
        GulpEngine engine = new GulpEngine(game, settings, backend);
        engine.start();
        return new HeadlessRunner(settings, backend, engine);
    }

    /**
     * Runs frames.
     *
     * @param frames how many frames to run
     * @return how many frames actually ran before the game stopped
     * @throws IllegalStateException if the game failed to start, with the cause
     */
    public int step(int frames) {
        int ran = backend.loop().step(frames);
        Throwable failure = engine.failure();
        if (failure != null) {
            throw new IllegalStateException("The game failed to start", failure);
        }
        return ran;
    }

    /** Stops the game (modules are disabled, {@code onStop} runs) and releases the backend. Safe to call twice. */
    public void stop() {
        engine.stop();
        backend.loop().stop();
        backend.dispose();
    }

    /**
     * Returns whether the game still runs.
     *
     * @return {@code false} after the game or the test stopped it
     */
    public boolean isRunning() {
        return backend.loop().isRunning();
    }

    /**
     * Returns the settings chosen by the game.
     *
     * @return the settings
     */
    public GameSettings settings() {
        return settings;
    }

    /**
     * Returns the backend, for injecting input and inspecting counters.
     *
     * @return the backend
     */
    public HeadlessBackend backend() {
        return backend;
    }

    /**
     * Returns the engine.
     *
     * @return the engine
     */
    public GulpEngine engine() {
        return engine;
    }
}
