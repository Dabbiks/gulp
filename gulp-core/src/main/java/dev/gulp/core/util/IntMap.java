package dev.gulp.core.util;

import org.jspecify.annotations.Nullable;

/**
 * Hash map from {@code int} keys to objects without boxing, with open addressing and linear probing.
 *
 * <pre>{@code
 * IntMap<Entity> byId = new IntMap<>();
 * byId.put(42, entity);
 * Entity e = byId.get(42);
 * }</pre>
 *
 * @param <V> the value type
 */
public final class IntMap<V> {

    private static final float LOAD_FACTOR = 0.6f;

    private int[] keys;
    private @Nullable Object[] values;
    private boolean[] used;
    private int size;

    /** Creates an empty map. */
    public IntMap() {
        this(16);
    }

    /**
     * Creates an empty map.
     *
     * @param capacity expected number of entries
     */
    public IntMap(int capacity) {
        int tableSize = Integer.highestOneBit(Math.max(4, (int) (capacity / LOAD_FACTOR)) - 1) << 1;
        keys = new int[tableSize];
        values = new Object[tableSize];
        used = new boolean[tableSize];
    }

    /**
     * Stores a value.
     *
     * @param key the key
     * @param value the value
     * @return the previous value, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public @Nullable V put(int key, V value) {
        int slot = find(key);
        if (used[slot]) {
            V previous = (V) values[slot];
            values[slot] = value;
            return previous;
        }
        used[slot] = true;
        keys[slot] = key;
        values[slot] = value;
        if (++size > keys.length * LOAD_FACTOR) {
            resize(keys.length * 2);
        }
        return null;
    }

    /**
     * Returns a value.
     *
     * @param key the key
     * @return the value, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public @Nullable V get(int key) {
        int slot = find(key);
        return used[slot] ? (V) values[slot] : null;
    }

    /**
     * Returns whether a key is present.
     *
     * @param key the key
     * @return {@code true} if present
     */
    public boolean containsKey(int key) {
        return used[find(key)];
    }

    /**
     * Removes a value.
     *
     * @param key the key
     * @return the removed value, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public @Nullable V remove(int key) {
        int slot = find(key);
        if (!used[slot]) {
            return null;
        }
        V removed = (V) values[slot];
        used[slot] = false;
        values[slot] = null;
        size--;
        // Re-insert the rest of the probe run so lookups do not stop at the hole.
        int mask = keys.length - 1;
        int next = (slot + 1) & mask;
        while (used[next]) {
            int movedKey = keys[next];
            Object movedValue = values[next];
            used[next] = false;
            values[next] = null;
            int target = find(movedKey);
            used[target] = true;
            keys[target] = movedKey;
            values[target] = movedValue;
            next = (next + 1) & mask;
        }
        return removed;
    }

    /**
     * Returns the number of entries.
     *
     * @return the size
     */
    public int size() {
        return size;
    }

    /**
     * Returns whether the map is empty.
     *
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /** Removes all entries. */
    public void clear() {
        java.util.Arrays.fill(used, false);
        java.util.Arrays.fill(values, null);
        size = 0;
    }

    private int find(int key) {
        int mask = keys.length - 1;
        int slot = mix(key) & mask;
        while (used[slot] && keys[slot] != key) {
            slot = (slot + 1) & mask;
        }
        return slot;
    }

    private static int mix(int key) {
        int h = key * 0x9E3779B9;
        return h ^ (h >>> 16);
    }

    private void resize(int newSize) {
        int[] oldKeys = keys;
        Object[] oldValues = values;
        boolean[] oldUsed = used;
        keys = new int[newSize];
        values = new Object[newSize];
        used = new boolean[newSize];
        size = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldUsed[i]) {
                int slot = find(oldKeys[i]);
                used[slot] = true;
                keys[slot] = oldKeys[i];
                values[slot] = oldValues[i];
                size++;
            }
        }
    }
}
