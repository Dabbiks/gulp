package dev.gulp.core.scheduler;

import dev.gulp.api.Owner;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.core.CoreContext;
import dev.gulp.core.MainQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * {@link Promise} completed on the main thread. Callbacks run in the order added; callbacks of an owner that is no
 * longer active are dropped. A failure nobody handles is logged.
 *
 * @param <T> the result type
 */
public final class PromiseImpl<T extends @Nullable Object> implements Promise<T> {

    private enum State {
        PENDING,
        SUCCEEDED,
        FAILED
    }

    private final Owner owner;
    private final CoreContext context;
    private final MainQueue mainQueue;
    private final List<Consumer<? super T>> onSuccess = new ArrayList<>();
    private final List<Consumer<? super Throwable>> onFailure = new ArrayList<>();
    private State state = State.PENDING;
    private @Nullable T value;
    private @Nullable Throwable error;

    /**
     * Creates a pending promise.
     *
     * @param owner whose callbacks these are
     * @param context owner state and loggers
     * @param mainQueue where callbacks added after completion are run
     */
    public PromiseImpl(Owner owner, CoreContext context, MainQueue mainQueue) {
        this.owner = owner;
        this.context = context;
        this.mainQueue = mainQueue;
    }

    /**
     * Completes successfully and runs the success callbacks. Main thread only.
     *
     * @param result the result
     */
    public void complete(T result) {
        if (state != State.PENDING) {
            return;
        }
        state = State.SUCCEEDED;
        value = result;
        if (!context.acceptsRegistrations(owner)) {
            onSuccess.clear();
            onFailure.clear();
            return;
        }
        for (Consumer<? super T> callback : onSuccess) {
            run(callback, result);
        }
        onSuccess.clear();
        onFailure.clear();
    }

    /**
     * Completes with a failure and runs the failure callbacks. Main thread only.
     *
     * @param failure the exception
     */
    public void fail(Throwable failure) {
        if (state != State.PENDING) {
            return;
        }
        state = State.FAILED;
        error = failure;
        if (!context.acceptsRegistrations(owner)) {
            onSuccess.clear();
            onFailure.clear();
            return;
        }
        if (onFailure.isEmpty()) {
            context.loggerOf(owner).error("Unhandled failure of an asynchronous operation", failure);
        }
        for (Consumer<? super Throwable> callback : onFailure) {
            run(callback, failure);
        }
        onSuccess.clear();
        onFailure.clear();
    }

    @Override
    public Promise<T> thenSync(Consumer<? super T> action) {
        switch (state) {
            case PENDING -> onSuccess.add(action);
            case SUCCEEDED -> {
                T result = value;
                mainQueue.post(() -> {
                    if (context.acceptsRegistrations(owner)) {
                        run(action, result);
                    }
                });
            }
            case FAILED -> {}
        }
        return this;
    }

    @Override
    public Promise<T> onFailure(Consumer<? super Throwable> action) {
        switch (state) {
            case PENDING -> onFailure.add(action);
            case FAILED -> {
                Throwable failure = error;
                mainQueue.post(() -> {
                    if (failure != null && context.acceptsRegistrations(owner)) {
                        run(action, failure);
                    }
                });
            }
            case SUCCEEDED -> {}
        }
        return this;
    }

    @Override
    public <R extends @Nullable Object> Promise<R> map(Function<? super T, ? extends R> mapper) {
        PromiseImpl<R> mapped = new PromiseImpl<>(owner, context, mainQueue);
        thenSync(result -> {
            R converted;
            try {
                converted = mapper.apply(result);
            } catch (Throwable failure) {
                mapped.fail(failure);
                return;
            }
            mapped.complete(converted);
        });
        onFailure(mapped::fail);
        return mapped;
    }

    @Override
    public <R extends @Nullable Object> Promise<R> flatMap(Function<? super T, ? extends Promise<R>> next) {
        PromiseImpl<R> chained = new PromiseImpl<>(owner, context, mainQueue);
        thenSync(result -> {
            Promise<R> step;
            try {
                step = next.apply(result);
            } catch (Throwable failure) {
                chained.fail(failure);
                return;
            }
            step.thenSync(chained::complete);
            step.onFailure(chained::fail);
        });
        onFailure(chained::fail);
        return chained;
    }

    /**
     * Returns a promise that already succeeded.
     *
     * @param <V> the value type
     * @param owner owner of the callbacks
     * @param context engine context
     * @param mainQueue main-thread queue
     * @param value the result
     * @return the completed promise
     */
    public static <V extends @Nullable Object> PromiseImpl<V> completed(
            Owner owner, CoreContext context, MainQueue mainQueue, V value) {
        PromiseImpl<V> promise = new PromiseImpl<>(owner, context, mainQueue);
        promise.complete(value);
        return promise;
    }

    /**
     * Returns a promise that already failed, without logging the failure as unhandled.
     *
     * @param <V> the value type
     * @param owner owner of the callbacks
     * @param context engine context
     * @param mainQueue main-thread queue
     * @param failure the error
     * @return the failed promise
     */
    public static <V extends @Nullable Object> PromiseImpl<V> failed(
            Owner owner, CoreContext context, MainQueue mainQueue, Throwable failure) {
        PromiseImpl<V> promise = new PromiseImpl<>(owner, context, mainQueue);
        promise.onFailure(error -> {});
        promise.fail(failure);
        promise.onFailure.clear();
        return promise;
    }

    @Override
    public boolean isDone() {
        return state != State.PENDING;
    }

    @Override
    public boolean isFailed() {
        return state == State.FAILED;
    }

    private <V> void run(Consumer<? super V> callback, V argument) {
        try {
            callback.accept(argument);
        } catch (Throwable failure) {
            context.loggerOf(owner).error("Unhandled exception in a promise callback", failure);
        }
    }
}
