package dev.gulp.core.util;

import java.util.Arrays;

/**
 * Growable list of {@code int} values without boxing.
 *
 * <pre>{@code
 * IntList indices = new IntList(64);
 * indices.add(3);
 * int first = indices.get(0);
 * }</pre>
 */
public final class IntList {

    private int[] items;
    private int size;

    /** Creates an empty list with room for 16 values. */
    public IntList() {
        this(16);
    }

    /**
     * Creates an empty list.
     *
     * @param capacity initial room
     */
    public IntList(int capacity) {
        items = new int[Math.max(1, capacity)];
    }

    /**
     * Appends a value.
     *
     * @param value the value
     */
    public void add(int value) {
        if (size == items.length) {
            items = Arrays.copyOf(items, items.length * 2);
        }
        items[size++] = value;
    }

    /**
     * Returns a value.
     *
     * @param index the index
     * @return the value
     * @throws IndexOutOfBoundsException if out of range
     */
    public int get(int index) {
        checkIndex(index);
        return items[index];
    }

    /**
     * Replaces a value.
     *
     * @param index the index
     * @param value the new value
     * @throws IndexOutOfBoundsException if out of range
     */
    public void set(int index, int value) {
        checkIndex(index);
        items[index] = value;
    }

    /**
     * Removes a value by moving the last one into its place (order is not kept).
     *
     * @param index the index
     * @return the removed value
     * @throws IndexOutOfBoundsException if out of range
     */
    public int removeSwap(int index) {
        checkIndex(index);
        int removed = items[index];
        items[index] = items[--size];
        return removed;
    }

    /**
     * Returns the index of a value.
     *
     * @param value the value
     * @return the first index, or {@code -1}
     */
    public int indexOf(int value) {
        for (int i = 0; i < size; i++) {
            if (items[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the number of values.
     *
     * @return the size
     */
    public int size() {
        return size;
    }

    /**
     * Returns whether the list is empty.
     *
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /** Removes all values, keeping the capacity. */
    public void clear() {
        size = 0;
    }

    /**
     * Copies the values.
     *
     * @return a new array of {@link #size()} values
     */
    public int[] toArray() {
        return Arrays.copyOf(items, size);
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for size " + size);
        }
    }
}
