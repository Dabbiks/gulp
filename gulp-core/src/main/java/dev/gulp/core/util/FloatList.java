package dev.gulp.core.util;

import java.util.Arrays;

/**
 * Growable list of {@code float} values without boxing.
 *
 * <pre>{@code
 * FloatList widths = new FloatList();
 * widths.add(12.5f);
 * float total = widths.sum();
 * }</pre>
 */
public final class FloatList {

    private float[] items;
    private int size;

    /** Creates an empty list with room for 16 values. */
    public FloatList() {
        this(16);
    }

    /**
     * Creates an empty list.
     *
     * @param capacity initial room
     */
    public FloatList(int capacity) {
        items = new float[Math.max(1, capacity)];
    }

    /**
     * Appends a value.
     *
     * @param value the value
     */
    public void add(float value) {
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
    public float get(int index) {
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
    public void set(int index, float value) {
        checkIndex(index);
        items[index] = value;
    }

    /**
     * Returns the sum of all values.
     *
     * @return the sum, {@code 0} when empty
     */
    public float sum() {
        float total = 0f;
        for (int i = 0; i < size; i++) {
            total += items[i];
        }
        return total;
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
     * Copies the values into a new array.
     *
     * @return the values
     */
    public float[] toArray() {
        return Arrays.copyOf(items, size);
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index " + index + " out of bounds for size " + size);
        }
    }
}
