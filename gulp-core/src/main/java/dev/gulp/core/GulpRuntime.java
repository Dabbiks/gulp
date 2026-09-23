package dev.gulp.core;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.graphics.Color;
import dev.gulp.platform.FrameHandler;
import dev.gulp.platform.Gl;
import dev.gulp.platform.PlatformBackend;
import dev.gulp.platform.PlatformWindow;
import dev.gulp.platform.WindowConfig;

/**
 * Runs one game on one backend: lifecycle calls and the frame loop.
 *
 * <p>Stage 0 scope: {@code configure → platform init → onLoad → onStart → frames → onStop}, each frame clears the
 * screen with {@link GameSettings#clearColor()}. The full {@code Engine} with fixed-step ticks, modules and events
 * replaces the frame body in stage 1.
 *
 * <pre>{@code
 * GameSettings settings = GulpRuntime.configure(game);
 * PlatformBackend backend = DesktopBackend.create(GulpRuntime.windowConfig(settings));
 * new GulpRuntime(game, settings, backend).start();
 * }</pre>
 */
public final class GulpRuntime implements FrameHandler {

    private final Game game;
    private final GameSettings settings;
    private final PlatformBackend backend;

    private boolean started;
    private boolean stopped;
    private long frameCount;

    /**
     * Creates a runtime for a configured game.
     *
     * @param game the game
     * @param settings the settings returned by {@link #configure(Game)}
     * @param backend the initialised platform
     */
    public GulpRuntime(Game game, GameSettings settings, PlatformBackend backend) {
        this.game = game;
        this.settings = settings;
        this.backend = backend;
    }

    /**
     * Calls {@link Game#configure(GameSettings)} on fresh default settings.
     *
     * @param game the game
     * @return the settings chosen by the game
     */
    public static GameSettings configure(Game game) {
        GameSettings settings = new GameSettings();
        game.configure(settings);
        return settings;
    }

    /**
     * Derives the platform window parameters from game settings.
     *
     * @param settings the game settings
     * @return the window configuration
     */
    public static WindowConfig windowConfig(GameSettings settings) {
        return new WindowConfig(
                settings.title(),
                settings.windowWidth(),
                settings.windowHeight(),
                settings.isResizable(),
                settings.isFullscreen(),
                settings.isVsync());
    }

    /**
     * Calls {@link Game#onLoad()} and {@link Game#onStart()}, then hands frames to the platform loop. Blocks until the
     * game stops on desktop; returns immediately on the web and in headless mode.
     *
     * @throws IllegalStateException if called twice
     */
    public void start() {
        if (started) {
            throw new IllegalStateException("Runtime already started");
        }
        started = true;
        game.onLoad();
        game.onStart();
        backend.loop().run(this);
    }

    @Override
    public boolean frame(long nanoTime) {
        PlatformWindow window = backend.window();
        Gl gl = backend.gl();
        Color clear = settings.clearColor();
        gl.viewport(0, 0, window.framebufferWidth(), window.framebufferHeight());
        gl.clearColor(clear.r(), clear.g(), clear.b(), clear.a());
        gl.clear(Gl.COLOR_BUFFER_BIT);
        frameCount++;
        return !window.shouldClose();
    }

    @Override
    public void exit() {
        if (stopped) {
            return;
        }
        stopped = true;
        game.onStop();
    }

    /**
     * Returns how many frames were rendered.
     *
     * @return the frame count
     */
    public long frameCount() {
        return frameCount;
    }

    /**
     * Returns whether {@link Game#onStop()} was already called.
     *
     * @return {@code true} after the loop ended
     */
    public boolean isStopped() {
        return stopped;
    }
}
