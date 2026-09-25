package dev.gulp.api.ui;

import dev.gulp.api.event.Subscription;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A value that tells listeners when it changes: {@link State}, {@link Computed} and {@link ListState}. Widgets take
 * observables and refresh themselves; their subscriptions belong to the node and end when it leaves the screen.
 *
 * <pre>{@code
 * State<Integer> coins = State.of(0);
 * Observable<String> text = coins.map(c -> tr("hud.coins", c));
 * label(text);
 * }</pre>
 *
 * @param <T> the value type
 */
public interface Observable<T> {

    /**
     * Returns the current value. Read inside {@link Computed#of}, it becomes a dependency.
     *
     * @return the value
     */
    T get();

    /**
     * Calls a listener after every change, with the new value.
     *
     * @param listener the listener
     * @return cancels the subscription
     */
    Subscription subscribe(Consumer<? super T> listener);

    /**
     * Returns a value computed from this one, updated when this one changes.
     *
     * @param mapper the conversion
     * @param <R> the result type
     * @return the mapped observable
     */
    default <R> Observable<R> map(Function<? super T, ? extends R> mapper) {
        return Computed.of(() -> mapper.apply(get()));
    }

    /**
     * Wraps a constant.
     *
     * @param value the value
     * @param <T> the value type
     * @return an observable that never changes
     */
    static <T> Observable<T> constant(T value) {
        return new Observable<>() {
            @Override
            public T get() {
                return value;
            }

            @Override
            public Subscription subscribe(Consumer<? super T> listener) {
                return Listeners.NONE;
            }
        };
    }
}
