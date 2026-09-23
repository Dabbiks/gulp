package dev.gulp.api;

import dev.gulp.api.command.Commands;
import dev.gulp.api.event.Events;
import dev.gulp.api.module.ModuleManager;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.scheduler.Scheduler;
import dev.gulp.api.service.Services;

/**
 * The running engine: access to every service, the tick counter and time control. There is one engine per process,
 * available through {@link Gulp#engine()} or {@link Owner#engine()}.
 *
 * <pre>{@code
 * Engine engine = Gulp.engine();
 * engine.setTimeScale(0.5f);          // slow motion
 * if (engine.tps() < 55) logger().warn("Server is lagging");
 * engine.pause();
 * }</pre>
 *
 * <p>Logic runs in fixed ticks ({@link #targetTps()} per second, 60 by default); rendering runs as often as the display
 * allows. Services of later subsystems (worlds, input, audio, UI, assets, saves) are added to this interface in the
 * roadmap stage that implements them.
 */
public interface Engine {

    /**
     * Returns the running game.
     *
     * @return the game
     */
    Game game();

    /**
     * Returns the module manager.
     *
     * @return the modules
     */
    ModuleManager modules();

    /**
     * Returns the event bus.
     *
     * @return the events
     */
    Events events();

    /**
     * Returns the scheduler.
     *
     * @return the scheduler
     */
    Scheduler scheduler();

    /**
     * Returns the registries.
     *
     * @return the registries
     */
    Registries registries();

    /**
     * Returns the service registry.
     *
     * @return the services
     */
    Services services();

    /**
     * Returns the command registry.
     *
     * @return the commands
     */
    Commands commands();

    /**
     * Returns platform information.
     *
     * @return the platform
     */
    Platform platform();

    /**
     * Returns the engine logger, named {@code gulp}.
     *
     * @return the logger
     */
    Logger logger();

    /**
     * Returns the number of game ticks since start. Does not advance while paused.
     *
     * @return the tick counter
     */
    long tick();

    /**
     * Returns the measured game ticks per second over the last second.
     *
     * @return the measured rate
     */
    float tps();

    /**
     * Returns the configured tick rate.
     *
     * @return ticks per second, from {@link GameSettings#ticksPerSecond()}
     */
    int targetTps();

    /**
     * Returns the time scale.
     *
     * @return {@code 1} for normal speed
     */
    float timeScale();

    /**
     * Changes how fast game time runs; tasks, cooldowns and tweens follow it. Real-time tasks are not affected.
     *
     * @param scale {@code 0.5} for half speed, {@code 2} for double speed, between 0 and 10
     */
    void setTimeScale(float scale);

    /**
     * Returns whether game time is paused.
     *
     * @return {@code true} while paused
     */
    boolean isPaused();

    /** Stops game ticks and ordinary tasks; real-time tasks and the UI keep running. Fires {@code PauseEvent}. */
    void pause();

    /** Resumes game time. Fires {@code ResumeEvent}. */
    void resume();

    /** Stops the game after the current frame: modules are disabled in reverse order, then {@code onStop} runs. */
    void stop();
}
