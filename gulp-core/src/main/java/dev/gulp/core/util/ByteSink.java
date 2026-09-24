package dev.gulp.core.util;

import java.util.Arrays;

/**
 * Growable byte buffer for decoders, with LZ-style back references that may overlap the bytes they produce.
 *
 * <pre>{@code
 * ByteSink out = new ByteSink(64);
 * out.write('a');
 * out.copyMatch(1, 3); // "aaaa"
 * }</pre>
 */
public final class ByteSink {

    private byte[] bytes;
    private int size;

    /**
     * Creates an empty buffer.
     *
     * @param capacity initial capacity in bytes
     */
    public ByteSink(int capacity) {
        bytes = new byte[Math.max(16, capacity)];
    }

    /**
     * Appends one byte.
     *
     * @param value the byte in the low 8 bits
     */
    public void write(int value) {
        ensure(1);
        bytes[size++] = (byte) value;
    }

    /**
     * Appends a range of bytes.
     *
     * @param source the bytes
     * @param offset first byte
     * @param length number of bytes
     */
    public void write(byte[] source, int offset, int length) {
        ensure(length);
        System.arraycopy(source, offset, bytes, size, length);
        size += length;
    }

    /**
     * Appends {@code length} bytes copied from {@code distance} bytes back; the ranges may overlap, repeating a
     * pattern.
     *
     * @param distance how far back the copy starts, at least 1
     * @param length number of bytes
     * @throws IllegalArgumentException if the distance reaches before the first byte
     */
    public void copyMatch(int distance, int length) {
        if (distance <= 0 || distance > size) {
            throw new IllegalArgumentException(
                    "Back reference " + distance + " outside the " + size + " bytes written");
        }
        ensure(length);
        int from = size - distance;
        for (int i = 0; i < length; i++) {
            bytes[size++] = bytes[from + i];
        }
    }

    /**
     * Returns the number of bytes written.
     *
     * @return the size
     */
    public int size() {
        return size;
    }

    /**
     * Returns a copy of the bytes written.
     *
     * @return the bytes
     */
    public byte[] toArray() {
        return Arrays.copyOf(bytes, size);
    }

    private void ensure(int extra) {
        if (size + extra > bytes.length) {
            bytes = Arrays.copyOf(bytes, Math.max(bytes.length * 2, size + extra));
        }
    }
}
