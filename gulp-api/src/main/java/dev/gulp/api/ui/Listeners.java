package dev.gulp.api.ui;

import dev.gulp.api.event.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Listener list of an observable, safe against changes while notifying, and dependency tracking for computed values. */
final class Listeners<T> {

    static final Subscription NONE = new Subscription() {
        @Override
        public void cancel() {}

        @Override
        public boolean isActive() {
            return false;
        }
    };

    /** Collects the observables read while a computed value evaluates (UI runs on the main thread only). */
    private static final List<List<Observable<?>>> TRACKING = new ArrayList<>();

    private final List<@Nullable Consumer<? super T>> listeners = new ArrayList<>();
    private int live;
    private int notifying;

    static void read(Observable<?> observable) {
        if (!TRACKING.isEmpty()) {
            List<Observable<?>> reads = TRACKING.get(TRACKING.size() - 1);
            if (!reads.contains(observable)) {
                reads.add(observable);
            }
        }
    }

    static void startTracking(List<Observable<?>> into) {
        TRACKING.add(into);
    }

    static void stopTracking() {
        TRACKING.remove(TRACKING.size() - 1);
    }

    Subscription add(Consumer<? super T> listener, @Nullable Runnable onEmpty) {
        listeners.add(listener);
        live++;
        return new Subscription() {
            private boolean active = true;

            @Override
            public void cancel() {
                if (!active) {
                    return;
                }
                active = false;
                int index = listeners.indexOf(listener);
                if (index >= 0) {
                    if (notifying > 0) {
                        listeners.set(index, null);
                    } else {
                        listeners.remove(index);
                    }
                }
                live--;
                if (live == 0 && onEmpty != null) {
                    onEmpty.run();
                }
            }

            @Override
            public boolean isActive() {
                return active;
            }
        };
    }

    boolean isEmpty() {
        return live == 0;
    }

    void notify(T value) {
        notifying++;
        try {
            int count = listeners.size();
            for (int i = 0; i < count; i++) {
                Consumer<? super T> listener = listeners.get(i);
                if (listener != null) {
                    listener.accept(value);
                }
            }
        } finally {
            notifying--;
            if (notifying == 0) {
                listeners.removeIf(l -> l == null);
            }
        }
    }
}
