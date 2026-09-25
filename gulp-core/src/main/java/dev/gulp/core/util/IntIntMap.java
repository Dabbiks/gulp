package dev.gulp.core.util;

import java.util.Arrays;

/**
 * Hash map from {@code int} keys to {@code int} values without boxing, with open addressing and linear probing.
 *
 * <pre>{@code
 * IntIntMap counts = new IntIntMap();
 * counts.increment(tileId, 1);
 * int seen = counts.get(tileId, 0);
 * }</pre>
 */
public final class IntIntMap {

    private static final float LOAD_FACTOR = 0.6f;

    private int[] keys;
    private int[] values;
    private boolean[] used;
    private int size;

    /** Creates an empty map. */
    public IntIntMap() {
        this(16);
    }

    /**
     * Creates an empty map.
     *
     * @param capacity expected number of entries
     */
    public IntIntMap(int capacity) {
        int tableSize = Integer.highestOneBit(Math.max(4, (int) (capacity / LOAD_FACTOR)) - 1) << 1;
        keys = new int[tableSize];
        values = new int[tableSize];
        used = new boolean[tableSize];
    }

    /**
     * Stores a value.
     *
     * @param key the key
     * @param value the value
     */
    public void put(int key, int value) {
        int slot = find(key);
        if (!used[slot]) {
            used[slot] = true;
            keys[slot] = key;
            size++;
        }
        values[slot] = value;
        if (size > keys.length * LOAD_FACTOR) {
            resize(keys.length * 2);
        }
    }

    /**
     * Adds to a value, starting from zero when the key is missing.
     *
     * @param key the key
     * @param amount what to add
     * @return the new value
     */
    public int increment(int key, int amount) {
        int value = get(key, 0) + amount;
        put(key, value);
        return value;
    }

    /**
     * Returns a value.
     *
     * @param key the key
     * @param fallback returned when the key is missing
     * @return the value or the fallback
     */
    public int get(int key, int fallback) {
        int slot = find(key);
        return used[slot] ? values[slot] : fallback;
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
     * Removes a key.
     *
     * @param key the key
     * @return {@code true} if it was present
     */
    public boolean remove(int key) {
        int slot = find(key);
        if (!used[slot]) {
            return false;
        }
        used[slot] = false;
        size--;
        // Re-insert the rest of the probe run so lookups do not stop at the hole.
        int mask = keys.length - 1;
        int next = (slot + 1) & mask;
        while (used[next]) {
            int movedKey = keys[next];
            int movedValue = values[next];
            used[next] = false;
            int target = find(movedKey);
            used[target] = true;
            keys[target] = movedKey;
            values[target] = movedValue;
            next = (next + 1) & mask;
        }
        return true;
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
        Arrays.fill(used, false);
        size = 0;
    }

    private int find(int key) {
        int mask = keys.length - 1;
        int slot = ((key * 0x9E3779B9) >>> 7) & mask;
        while (used[slot] && keys[slot] != key) {
            slot = (slot + 1) & mask;
        }
        return slot;
    }

    private void resize(int tableSize) {
        int[] oldKeys = keys;
        int[] oldValues = values;
        boolean[] oldUsed = used;
        keys = new int[tableSize];
        values = new int[tableSize];
        used = new boolean[tableSize];
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldUsed[i]) {
                int slot = find(oldKeys[i]);
                used[slot] = true;
                keys[slot] = oldKeys[i];
                values[slot] = oldValues[i];
            }
        }
    }
}
