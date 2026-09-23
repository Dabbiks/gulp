package dev.gulp.api.scheduler;

import dev.gulp.api.Owner;
import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * A sequence of actions and waits run across ticks, without counting ticks by hand.
 *
 * <pre>{@code
 * scheduler().sequence()
 *         .run(() -> door.open())
 *         .wait(Duration.ofSeconds(1))
 *         .waitUntil(() -> player.isOnFloor())
 *         .run(() -> camera.shake(0.3f, 0.2f))
 *         .repeat(3)
 *         .start(this);
 * }</pre>
 *
 * <p>Consecutive {@link #run(Runnable)} steps execute in the same tick; each wait step ends the tick.
 */
public interface Sequence {

    /**
     * Adds an action.
     *
     * @param action the action
     * @return this sequence
     */
    Sequence run(Runnable action);

    /**
     * Adds a wait in game time.
     *
     * @param duration how long to wait, rounded up to whole ticks
     * @return this sequence
     */
    Sequence wait(Duration duration);

    /**
     * Adds a wait in ticks.
     *
     * @param ticks how many ticks to wait, at least 1
     * @return this sequence
     */
    Sequence waitTicks(long ticks);

    /**
     * Adds a wait until a condition holds; it is checked once per tick, starting with the tick the wait begins.
     *
     * @param condition the condition
     * @return this sequence
     */
    Sequence waitUntil(BooleanSupplier condition);

    /**
     * Runs the whole sequence this many times in total.
     *
     * @param times how many times, at least 1
     * @return this sequence
     */
    Sequence repeat(int times);

    /**
     * Repeats the whole sequence until cancelled.
     *
     * @return this sequence
     */
    Sequence repeatForever();

    /**
     * Starts the sequence on the next tick.
     *
     * @param owner the owner; the sequence stops when it is disabled
     * @return the task running the sequence
     * @throws IllegalStateException if the sequence has no steps or was already started
     */
    Task start(Owner owner);
}
