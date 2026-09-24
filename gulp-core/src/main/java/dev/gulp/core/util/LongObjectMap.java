package dev.gulp.core.util;

import java.util.Arrays;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Map from {@code long} keys to objects with open addressing and linear probing, without boxing. Used for spatial grid
 * cells and chunks keyed by {@link #pack(int, int)}.
 *
 * <pre>{@code
 * LongObjectMap<Chunk> chunks = new LongObjectMap<>();
 * chunks.put(LongObjectMap.pack(cx, cy), chunk);
 * }</pre>
 *
 * @param <V> the value type
 */
public final class LongObjectMap<V> {

    private static final long EMPTY = Long.MIN_VALUE;

    private long[] keys;
    private @Nullable Object[] values;
    private int size;
    private int mask;

    /** Creates an empty map. */
    public LongObjectMap() {
        keys = new long[16];
        Arrays.fill(keys, EMPTY);
        values = new Object[16];
        mask = 15;
    }

    /**
     * Packs two ints into a key.
     *
     * @param x the high part
     * @param y the low part
     * @return the key
     */
    public static long pack(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    /**
     * Returns the high part of a packed key.
     *
     * @param key the key
     * @return x
     */
    public static int unpackX(long key) {
        return (int) (key >> 32);
    }

    /**
     * Returns the low part of a packed key.
     *
     * @param key the key
     * @return y
     */
    public static int unpackY(long key) {
        return (int) key;
    }

    private static int hash(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32));
    }

    private int find(long key) {
        int index = hash(key) & mask;
        while (true) {
            long current = keys[index];
            if (current == key) {
                return index;
            }
            if (current == EMPTY) {
                return -1;
            }
            index = (index + 1) & mask;
        }
    }

    /**
     * Returns a value.
     *
     * @param key the key, not {@link Long#MIN_VALUE}
     * @return the value, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public @Nullable V get(long key) {
        int index = find(key);
        return index < 0 ? null : (V) values[index];
    }

    /**
     * Returns whether a key is present.
     *
     * @param key the key
     * @return {@code true} if present
     */
    public boolean containsKey(long key) {
        return find(key) >= 0;
    }

    /**
     * Stores a value.
     *
     * @param key the key, not {@link Long#MIN_VALUE}
     * @param value the value
     * @return the previous value, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public @Nullable V put(long key, V value) {
        if (key == EMPTY) {
            throw new IllegalArgumentException("Key Long.MIN_VALUE is reserved");
        }
        if ((size + 1) * 4 > keys.length * 3) {
            grow();
        }
        int index = hash(key) & mask;
        while (true) {
            long current = keys[index];
            if (current == key) {
                V old = (V) values[index];
                values[index] = value;
                return old;
            }
            if (current == EMPTY) {
                keys[index] = key;
                values[index] = value;
                size++;
                return null;
            }
            index = (index + 1) & mask;
        }
    }

    /**
     * Removes a key.
     *
     * @param key the key
     * @return the removed value, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public @Nullable V remove(long key) {
        int index = find(key);
        if (index < 0) {
            return null;
        }
        V old = (V) values[index];
        keys[index] = EMPTY;
        values[index] = null;
        size--;
        // Re-insert the rest of the probe run so lookups keep finding it.
        int next = (index + 1) & mask;
        while (keys[next] != EMPTY) {
            long movedKey = keys[next];
            Object movedValue = values[next];
            keys[next] = EMPTY;
            values[next] = null;
            size--;
            put(movedKey, (V) movedValue);
            next = (next + 1) & mask;
        }
        return old;
    }

    private void grow() {
        long[] oldKeys = keys;
        Object[] oldValues = values;
        keys = new long[oldKeys.length * 2];
        Arrays.fill(keys, EMPTY);
        values = new Object[oldKeys.length * 2];
        mask = keys.length - 1;
        size = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != EMPTY) {
                @SuppressWarnings("unchecked")
                V value = (V) oldValues[i];
                put(oldKeys[i], value);
            }
        }
    }

    /**
     * Returns the number of entries.
     *
     * @return the size
     */
    public int size() {
        return size;
    }

    /** Removes every entry. */
    public void clear() {
        Arrays.fill(keys, EMPTY);
        Arrays.fill(values, null);
        size = 0;
    }

    /**
     * Runs an action for every value.
     *
     * @param action the action
     */
    @SuppressWarnings("unchecked")
    public void forEachValue(Consumer<V> action) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != EMPTY) {
                action.accept((V) values[i]);
            }
        }
    }

    /**
     * Returns the keys.
     *
     * @return a new array of the keys
     */
    public long[] keys() {
        long[] result = new long[size];
        int n = 0;
        for (long key : keys) {
            if (key != EMPTY) {
                result[n++] = key;
            }
        }
        return result;
    }
}
