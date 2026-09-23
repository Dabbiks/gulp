package dev.gulp.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Pool of reusable objects for allocation-free hot paths. Not thread-safe.
 *
 * <pre>{@code
 * Pool<Contact> contacts = new Pool<>(Contact::new, Contact::reset, 256);
 * Contact c = contacts.obtain();
 * ...
 * contacts.free(c);
 * }</pre>
 *
 * @param <T> the pooled type
 */
public final class Pool<T> {

    private final Supplier<? extends T> factory;
    private final Consumer<? super T> reset;
    private final int maxFree;
    private final List<T> free = new ArrayList<>();
    private int created;

    /**
     * Creates a pool.
     *
     * @param factory creates new objects when the pool is empty
     * @param reset prepares an object for reuse when it is freed
     * @param maxFree how many free objects to keep; more are dropped for the garbage collector
     */
    public Pool(Supplier<? extends T> factory, Consumer<? super T> reset, int maxFree) {
        if (maxFree < 0) {
            throw new IllegalArgumentException("maxFree must not be negative");
        }
        this.factory = factory;
        this.reset = reset;
        this.maxFree = maxFree;
    }

    /**
     * Returns a free object or creates one.
     *
     * @return the object
     */
    public T obtain() {
        if (free.isEmpty()) {
            created++;
            return factory.get();
        }
        return free.removeLast();
    }

    /**
     * Returns an object to the pool.
     *
     * @param object the object; must not be used afterwards
     */
    public void free(T object) {
        reset.accept(object);
        if (free.size() < maxFree) {
            free.add(object);
        }
    }

    /**
     * Creates objects until {@code count} are free.
     *
     * @param count the number of free objects wanted, at most {@code maxFree}
     */
    public void fill(int count) {
        while (free.size() < Math.min(count, maxFree)) {
            created++;
            free.add(factory.get());
        }
    }

    /**
     * Returns the number of free objects.
     *
     * @return the free count
     */
    public int freeCount() {
        return free.size();
    }

    /**
     * Returns how many objects the pool has created in total.
     *
     * @return the created count
     */
    public int createdCount() {
        return created;
    }
}
