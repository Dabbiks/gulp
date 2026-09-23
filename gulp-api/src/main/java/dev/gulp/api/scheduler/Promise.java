package dev.gulp.api.scheduler;

import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The result of an asynchronous operation. Callbacks always run on the main thread, between frames; a callback added
 * after completion runs on the next frame.
 *
 * <pre>{@code
 * scheduler().async(() -> Pathfinder.compute(grid, from, to))
 *         .thenSync(path -> enemy.follow(path))
 *         .onFailure(error -> logger().warn("No path", error));
 * }</pre>
 *
 * @param <T> the result type; {@code Void} operations complete with {@code null}
 */
public interface Promise<T extends @Nullable Object> {

    /**
     * Adds a callback for the successful result.
     *
     * @param action receives the result on the main thread
     * @return this promise
     */
    Promise<T> thenSync(Consumer<? super T> action);

    /**
     * Adds a callback for a failure.
     *
     * @param action receives the exception on the main thread
     * @return this promise
     */
    Promise<T> onFailure(Consumer<? super Throwable> action);

    /**
     * Returns a promise of a value derived on the main thread from this one's result.
     *
     * @param <R> the new result type
     * @param mapper the transformation; an exception fails the new promise
     * @return the derived promise
     */
    <R extends @Nullable Object> Promise<R> map(Function<? super T, ? extends R> mapper);

    /**
     * Returns a promise that continues with another asynchronous step once this one succeeds.
     *
     * <pre>{@code
     * Promise<Texture> texture = files.read("coins/sprites/player.png")
     *         .flatMap(graphics()::decode)
     *         .map(graphics()::texture);
     * }</pre>
     *
     * @param <R> the new result type
     * @param next starts the next step on the main thread; an exception fails the new promise
     * @return a promise of the next step's result
     */
    <R extends @Nullable Object> Promise<R> flatMap(Function<? super T, ? extends Promise<R>> next);

    /**
     * Returns whether the promise completed, successfully or not.
     *
     * @return {@code true} once completed
     */
    boolean isDone();

    /**
     * Returns whether the promise failed.
     *
     * @return {@code true} if completed with an exception
     */
    boolean isFailed();
}
