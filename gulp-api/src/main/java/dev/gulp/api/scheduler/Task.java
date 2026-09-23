package dev.gulp.api.scheduler;

import dev.gulp.api.Owner;

/**
 * A scheduled task.
 *
 * <pre>{@code
 * Task blink = every(10, cursor::toggle);
 * ...
 * blink.cancel();
 * }</pre>
 *
 * <p>A task that throws three times in a row is cancelled automatically and the error is logged with its owner.
 */
public interface Task {

    /** Cancels the task; it will not run again. Safe to call from inside the task. */
    void cancel();

    /**
     * Returns whether the task was cancelled, by code, by its owner being disabled, or after repeated errors.
     *
     * @return {@code true} if cancelled
     */
    boolean isCancelled();

    /**
     * Returns whether the task will not run again: it was cancelled or, for a one-shot task, it already ran.
     *
     * @return {@code true} if finished
     */
    boolean isDone();

    /**
     * Returns the owner of the task.
     *
     * @return the game or a module
     */
    Owner owner();

    /**
     * Returns whether the task repeats.
     *
     * @return {@code true} for tasks created with {@code every}
     */
    boolean isRepeating();

    /**
     * Returns whether the task counts real ticks.
     *
     * @return {@code true} for tasks created through {@link Scheduler#realtime()}
     */
    boolean isRealtime();
}
