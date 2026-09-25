package dev.gulp.api.ui;

import dev.gulp.api.event.Subscription;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * An observable value that UI binds to. Setting a different value notifies listeners at once; labels and widgets
 * bound to it refresh without further code. Two-way widgets ({@link Slider#bind}, {@link Checkbox#bind}, {@link
 * TextField#bind}) also write to it. States are used on the main thread only.
 *
 * <pre>{@code
 * State<Integer> coins = State.of(0);
 * ui().hud().add(this, label(coins.map(c -> tr("hud.coins", c))).anchor(Anchor.TOP_LEFT).offset(8, 8));
 * coins.update(c -> c + 1);
 *
 * State<Float> music = State.of(0.8f);
 * slider(0, 1).bind(music);
 * music.subscribe(v -> audio().bus(Audio.MUSIC).setVolume(v));
 * }</pre>
 *
 * @param <T> the value type
 */
public final class State<T> implements Observable<T> {

    private final Listeners<T> listeners = new Listeners<>();
    private T value;

    private State(T value) {
        this.value = value;
    }

    /**
     * Creates a state.
     *
     * @param initial the first value
     * @param <T> the value type
     * @return the state
     */
    public static <T> State<T> of(T initial) {
        return new State<>(initial);
    }

    @Override
    public T get() {
        Listeners.read(this);
        return value;
    }

    /**
     * Changes the value and notifies listeners if it differs ({@link Objects#equals}).
     *
     * @param newValue the new value
     */
    public void set(T newValue) {
        if (Objects.equals(value, newValue)) {
            return;
        }
        value = newValue;
        listeners.notify(newValue);
    }

    /**
     * Changes the value with a function of the current one.
     *
     * @param change computes the new value
     */
    public void update(UnaryOperator<T> change) {
        set(change.apply(value));
    }

    @Override
    public Subscription subscribe(Consumer<? super T> listener) {
        return listeners.add(listener, null);
    }

    @Override
    public String toString() {
        return "State[" + value + "]";
    }
}
