package dev.gulp.api;

import dev.gulp.api.asset.Assets;
import dev.gulp.api.command.CommandExecutor;
import dev.gulp.api.command.Commands;
import dev.gulp.api.data.Config;
import dev.gulp.api.event.Event;
import dev.gulp.api.event.EventPriority;
import dev.gulp.api.event.Events;
import dev.gulp.api.event.Listener;
import dev.gulp.api.event.Subscription;
import dev.gulp.api.graphics.Graphics;
import dev.gulp.api.module.ModuleManager;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.render.Display;
import dev.gulp.api.scheduler.Scheduler;
import dev.gulp.api.scheduler.Task;
import dev.gulp.api.service.Services;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Something that owns registrations: the {@link Game} or a {@link dev.gulp.api.module.GameModule}. Everything an
 * owner registers (listeners, tasks, commands, services) is removed automatically when the owner is disabled.
 *
 * <p>The default methods are shortcuts to engine services that register on behalf of this owner:
 *
 * <pre>{@code
 * @Override public void onEnable() {
 *     listen(new DamageListener());
 *     on(TickEndEvent.class, e -> checkWinCondition());
 *     every(20, this::regenerate);          // every 20 ticks, cancelled on disable
 *     later(Duration.ofSeconds(3), this::showHint);
 * }
 * }</pre>
 */
public interface Owner {

    /**
     * Returns the identifier: the game namespace for a {@link Game}, the module id for a module.
     *
     * @return the identifier
     */
    String id();

    /**
     * Returns whether this owner is currently active. Registrations of an inactive owner are rejected.
     *
     * @return {@code true} while the game runs or the module is enabled
     */
    boolean isEnabled();

    /**
     * Returns the logger named after this owner.
     *
     * @return the logger
     */
    Logger logger();

    /**
     * Returns the configuration of this owner ({@code config/game.yml} or {@code config/<module>.yml}).
     *
     * @return the configuration, loaded before {@code onLoad}
     */
    Config config();

    /**
     * Creates a key in the game namespace.
     *
     * @param path key path matching {@code [a-z0-9_./-]+}
     * @return the key {@code <game id>:<path>}
     */
    default Key key(String path) {
        return Key.of(engine().game().id(), path);
    }

    /**
     * Returns the running engine.
     *
     * @return the engine
     */
    default Engine engine() {
        return Gulp.engine();
    }

    /**
     * Returns the event bus.
     *
     * @return the event bus
     */
    default Events events() {
        return engine().events();
    }

    /**
     * Returns the scheduler; tasks created through it belong to the game. Use the shortcuts below for tasks owned by
     * this owner.
     *
     * @return the scheduler
     */
    default Scheduler scheduler() {
        return engine().scheduler();
    }

    /**
     * Returns the registries.
     *
     * @return the registries
     */
    default Registries registries() {
        return engine().registries();
    }

    /**
     * Returns the service registry.
     *
     * @return the services
     */
    default Services services() {
        return engine().services();
    }

    /**
     * Returns the command registry.
     *
     * @return the commands
     */
    default Commands commands() {
        return engine().commands();
    }

    /**
     * Returns the GPU resource factory.
     *
     * @return the graphics
     */
    default Graphics graphics() {
        return engine().graphics();
    }

    /**
     * Returns screen scaling, the camera and render layers.
     *
     * @return the display
     */
    default Display display() {
        return engine().display();
    }

    /**
     * Returns asset loading by key.
     *
     * @return the assets
     */
    default Assets assets() {
        return engine().assets();
    }

    /**
     * Returns the module manager.
     *
     * @return the module manager
     */
    default ModuleManager modules() {
        return engine().modules();
    }

    /**
     * Registers every {@code @EventHandler} method of a listener, owned by this owner.
     *
     * @param listener the listener; its class must have been processed by {@code gulp-processor}
     * @return the subscription covering all handlers of the listener
     */
    default Subscription listen(Listener listener) {
        return events().register(listener, this);
    }

    /**
     * Subscribes a lambda to an event type with {@link EventPriority#NORMAL} priority.
     *
     * @param <E> the event type
     * @param type the event class; subclasses are delivered too
     * @param handler the handler
     * @return the subscription
     */
    default <E extends Event> Subscription on(Class<E> type, Consumer<? super E> handler) {
        return events().on(type, EventPriority.NORMAL, false, handler, this);
    }

    /**
     * Subscribes a lambda to an event type.
     *
     * @param <E> the event type
     * @param type the event class; subclasses are delivered too
     * @param priority when the handler runs relative to others
     * @param handler the handler
     * @return the subscription
     */
    default <E extends Event> Subscription on(Class<E> type, EventPriority priority, Consumer<? super E> handler) {
        return events().on(type, priority, false, handler, this);
    }

    /**
     * Runs a task on the next tick.
     *
     * @param task the task
     * @return the scheduled task
     */
    default Task run(Runnable task) {
        return scheduler().owner(this).run(task);
    }

    /**
     * Runs a task after a delay in ticks.
     *
     * @param ticks delay in ticks; {@code 0} means the next tick
     * @param task the task
     * @return the scheduled task
     */
    default Task later(long ticks, Runnable task) {
        return scheduler().owner(this).later(ticks, task);
    }

    /**
     * Runs a task after a delay in game time.
     *
     * @param delay the delay, rounded up to whole ticks
     * @param task the task
     * @return the scheduled task
     */
    default Task later(Duration delay, Runnable task) {
        return scheduler().owner(this).later(delay, task);
    }

    /**
     * Runs a task repeatedly; the first run happens after one period.
     *
     * @param periodTicks period in ticks, at least 1
     * @param task the task
     * @return the scheduled task
     */
    default Task every(long periodTicks, Runnable task) {
        return scheduler().owner(this).every(periodTicks, task);
    }

    /**
     * Runs a task repeatedly; the first run happens after one period.
     *
     * @param period the period, rounded up to whole ticks
     * @param task the task
     * @return the scheduled task
     */
    default Task every(Duration period, Runnable task) {
        return scheduler().owner(this).every(period, task);
    }

    /**
     * Registers a simple command owned by this owner. Everything after the command name is available as the
     * {@code "args"} argument (possibly empty).
     *
     * @param name the command name, without the leading slash
     * @param executor what the command does
     */
    default void command(String name, CommandExecutor executor) {
        commands().register(this, dev.gulp.api.command.Command.simple(name, executor));
    }
}
