package dev.gulp.api.scheduler;

import dev.gulp.api.Owner;
import org.jspecify.annotations.Nullable;

/**
 * A task as a class that can cancel itself from inside {@link #run()}, useful for tasks with their own state.
 *
 * <pre>{@code
 * new TaskRunnable() {
 *     int seconds = 10;
 *     @Override public void run() {
 *         hud.showCountdown(seconds--);
 *         if (seconds < 0) cancel();
 *     }
 * }.runEvery(this, 20);
 * }</pre>
 */
public abstract class TaskRunnable implements Runnable {

    private @Nullable Task task;

    /** Creates the runnable; schedule it with one of the {@code run*} methods. */
    protected TaskRunnable() {}

    /** The work done on each run. */
    @Override
    public abstract void run();

    /**
     * Schedules this runnable on the next tick.
     *
     * @param owner the owner of the task
     * @return the scheduled task
     */
    public final Task runNext(Owner owner) {
        return schedule(owner.scheduler().owner(owner).run(this));
    }

    /**
     * Schedules this runnable after a delay.
     *
     * @param owner the owner of the task
     * @param delayTicks the delay in ticks
     * @return the scheduled task
     */
    public final Task runLater(Owner owner, long delayTicks) {
        return schedule(owner.scheduler().owner(owner).later(delayTicks, this));
    }

    /**
     * Schedules this runnable repeatedly, first after one period.
     *
     * @param owner the owner of the task
     * @param periodTicks the period in ticks
     * @return the scheduled task
     */
    public final Task runEvery(Owner owner, long periodTicks) {
        return schedule(owner.scheduler().owner(owner).every(periodTicks, this));
    }

    private Task schedule(Task scheduled) {
        if (task != null && !task.isDone()) {
            scheduled.cancel();
            throw new IllegalStateException("TaskRunnable is already scheduled");
        }
        task = scheduled;
        return scheduled;
    }

    /**
     * Cancels the scheduled task.
     *
     * @throws IllegalStateException if the runnable was never scheduled
     */
    public final void cancel() {
        if (task == null) {
            throw new IllegalStateException("TaskRunnable is not scheduled");
        }
        task.cancel();
    }

    /**
     * Returns whether the scheduled task was cancelled.
     *
     * @return {@code true} if cancelled; {@code false} if never scheduled
     */
    public final boolean isCancelled() {
        return task != null && task.isCancelled();
    }
}
