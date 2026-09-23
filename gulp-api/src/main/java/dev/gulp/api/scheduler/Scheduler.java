package dev.gulp.api.scheduler;

import dev.gulp.api.Owner;
import dev.gulp.api.ThreadSafe;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Delayed and repeating tasks that run on the main thread, inside game ticks.
 *
 * <p>Delays and periods are in ticks ({@code long}) or {@link Duration} (rounded up to whole ticks). Ordinary tasks
 * follow game time: they stop while the game is paused and speed up or slow down with the time scale.
 * {@link #realtime()} tasks follow real time and keep running during pause.
 *
 * <pre>{@code
 * scheduler().run(() -> spawnWave());                           // next tick, owned by the game
 * scheduler().owner(this).every(20, this::regenerate);          // owned by this module
 * scheduler().realtime().owner(this).every(1, pauseMenu::animate);
 * scheduler().async(() -> generateMaze(64)).thenSync(maze -> world.load(maze));
 * }</pre>
 *
 * <p>Scheduling is thread-safe: background code returns to the main thread by scheduling a task. Every task belongs
 * to an owner and is cancelled when the owner is disabled. Without {@link #owner(Owner)}, the
 * owner is the game.
 */
@ThreadSafe
public interface Scheduler {

    /**
     * Returns a view of this scheduler whose tasks belong to an owner.
     *
     * @param owner the owner
     * @return the view
     */
    Scheduler owner(Owner owner);

    /**
     * Returns a view of this scheduler whose tasks count real ticks: independent of pause and time scale.
     *
     * @return the view
     */
    Scheduler realtime();

    /**
     * Runs a task on the next tick.
     *
     * @param task the task
     * @return the scheduled task
     */
    Task run(Runnable task);

    /**
     * Runs a task after a number of ticks.
     *
     * @param delayTicks the delay; {@code 0} and {@code 1} both mean the next tick
     * @param task the task
     * @return the scheduled task
     */
    Task later(long delayTicks, Runnable task);

    /**
     * Runs a task after a delay.
     *
     * @param delay the delay, rounded up to whole ticks
     * @param task the task
     * @return the scheduled task
     */
    Task later(Duration delay, Runnable task);

    /**
     * Runs a task repeatedly, first after one period.
     *
     * @param periodTicks the period, at least 1
     * @param task the task
     * @return the scheduled task
     */
    Task every(long periodTicks, Runnable task);

    /**
     * Runs a task repeatedly, first after one period.
     *
     * @param period the period, rounded up to whole ticks
     * @param task the task
     * @return the scheduled task
     */
    Task every(Duration period, Runnable task);

    /**
     * Runs a task repeatedly.
     *
     * @param delayTicks ticks until the first run; {@code 0} means the next tick
     * @param periodTicks ticks between runs, at least 1
     * @param task the task
     * @return the scheduled task
     */
    Task every(long delayTicks, long periodTicks, Runnable task);

    /**
     * Runs a task repeatedly.
     *
     * @param delay time until the first run
     * @param period time between runs
     * @param task the task
     * @return the scheduled task
     */
    Task every(Duration delay, Duration period, Runnable task);

    /**
     * Starts building a sequence of steps and waits, a code-only replacement for {@code await}.
     *
     * @return a new sequence builder
     */
    Sequence sequence();

    /**
     * Computes a value outside the main tick and delivers it back on the main thread.
     *
     * <p>On desktop the supplier runs on a background thread and must not touch game state. <b>On the web there are no
     * background threads: "async" means the supplier runs between frames on the main thread</b>, so split long work
     * into chunks.
     *
     * @param <T> the result type
     * @param supplier the work
     * @return a promise completed on the main thread; its callbacks are dropped if the owner is disabled first
     */
    <T> Promise<T> async(Supplier<T> supplier);
}
