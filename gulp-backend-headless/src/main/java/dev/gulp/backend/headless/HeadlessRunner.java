package dev.gulp.backend.headless;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.core.GulpRuntime;

/**
 * Starts a game on the headless backend and lets a test advance it frame by frame.
 *
 * <pre>{@code
 * HeadlessRunner runner = HeadlessRunner.start(new CoinGame());
 * runner.step(60);
 * assertThat(runner.backend().gl().clearCount()).isEqualTo(60);
 * runner.stop();
 * }</pre>
 */
public final class HeadlessRunner {

    private final GameSettings settings;
    private final HeadlessBackend backend;
    private final GulpRuntime runtime;

    private HeadlessRunner(GameSettings settings, HeadlessBackend backend, GulpRuntime runtime) {
        this.settings = settings;
        this.backend = backend;
        this.runtime = runtime;
    }

    /**
     * Configures the game, creates the backend and runs {@code onLoad} and {@code onStart}. No frame runs yet.
     *
     * @param game the game
     * @return the runner
     */
    public static HeadlessRunner start(Game game) {
        GameSettings settings = GulpRuntime.configure(game);
        HeadlessBackend backend = new HeadlessBackend(GulpRuntime.windowConfig(settings));
        GulpRuntime runtime = new GulpRuntime(game, settings, backend);
        runtime.start();
        return new HeadlessRunner(settings, backend, runtime);
    }

    /**
     * Runs frames.
     *
     * @param frames how many frames to run
     * @return how many frames actually ran before the game stopped
     */
    public int step(int frames) {
        return backend.loop().step(frames);
    }

    /** Stops the game ({@code onStop} runs) and releases the backend. Safe to call twice. */
    public void stop() {
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
     * Returns the runtime driving the game.
     *
     * @return the runtime
     */
    public GulpRuntime runtime() {
        return runtime;
    }
}
