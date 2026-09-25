package dev.gulp.api.ui;

import dev.gulp.api.event.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * A value computed from other observables. The states read while computing become its dependencies automatically; it
 * recomputes when one of them changes and notifies its own listeners if the result differs. Without listeners it holds
 * no subscriptions and computes on every {@link #get()}, so it never leaks.
 *
 * <pre>{@code
 * State<Integer> hp = State.of(10);
 * State<Integer> maxHp = State.of(10);
 * Computed<Float> ratio = Computed.of(() -> hp.get() / (float) maxHp.get());
 * progressBar(ratio);
 * label(Computed.of(() -> hp.get() + " / " + maxHp.get()));
 * }</pre>
 *
 * @param <T> the value type
 */
public final class Computed<T> implements Observable<T> {

    private final Supplier<T> formula;
    private final Listeners<T> listeners = new Listeners<>();
    private final List<Subscription> upstream = new ArrayList<>();
    private final List<Observable<?>> dependencies = new ArrayList<>();
    private @Nullable T cached;
    private boolean valid;

    private Computed(Supplier<T> formula) {
        this.formula = formula;
    }

    /**
     * Creates a computed value.
     *
     * @param formula computes the value from other observables
     * @param <T> the value type
     * @return the computed value
     */
    public static <T> Computed<T> of(Supplier<T> formula) {
        return new Computed<>(formula);
    }

    @Override
    public T get() {
        Listeners.read(this);
        if (valid) {
            return cached;
        }
        T value = evaluate();
        if (!listeners.isEmpty()) {
            cached = value;
            valid = true;
        }
        return value;
    }

    private T evaluate() {
        List<Observable<?>> reads = new ArrayList<>();
        Listeners.startTracking(reads);
        T value;
        try {
            value = formula.get();
        } finally {
            Listeners.stopTracking();
        }
        if (!listeners.isEmpty() && !reads.equals(dependencies)) {
            unsubscribeUpstream();
            dependencies.addAll(reads);
            for (Observable<?> dependency : reads) {
                upstream.add(dependency.subscribe(ignored -> changed()));
            }
        }
        return value;
    }

    private void changed() {
        T previous = cached;
        T value = evaluate();
        cached = value;
        valid = true;
        if (!Objects.equals(previous, value)) {
            listeners.notify(value);
        }
    }

    private void unsubscribeUpstream() {
        for (Subscription subscription : upstream) {
            subscription.cancel();
        }
        upstream.clear();
        dependencies.clear();
    }

    @Override
    public Subscription subscribe(Consumer<? super T> listener) {
        boolean first = listeners.isEmpty();
        Subscription subscription = listeners.add(listener, () -> {
            unsubscribeUpstream();
            valid = false;
            cached = null;
        });
        if (first) {
            cached = evaluate();
            valid = true;
        }
        return subscription;
    }

    @Override
    public String toString() {
        return "Computed[" + (valid ? cached : "?") + "]";
    }
}
