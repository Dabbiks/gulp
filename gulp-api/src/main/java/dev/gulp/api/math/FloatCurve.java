package dev.gulp.api.math;

import java.util.Arrays;

/**
 * A value that changes over {@code 0..1}, as straight lines between keys: particle size or opacity over a lifetime,
 * for example. Immutable.
 *
 * <pre>{@code
 * FloatCurve size = FloatCurve.of(0f, 0.1f, 0.2f, 0.4f, 1f, 0f); // grows fast, then shrinks to nothing
 * float half = size.at(0.5f);
 * }</pre>
 */
public final class FloatCurve {

    /** Always {@code 1}. */
    public static final FloatCurve ONE = constant(1f);

    private final float[] times;
    private final float[] values;

    private FloatCurve(float[] times, float[] values) {
        this.times = times;
        this.values = values;
    }

    /**
     * Returns a curve with the same value everywhere.
     *
     * @param value the value
     * @return the curve
     */
    public static FloatCurve constant(float value) {
        return new FloatCurve(new float[] {0f}, new float[] {value});
    }

    /**
     * Returns a straight line from one value to another.
     *
     * @param from the value at {@code 0}
     * @param to the value at {@code 1}
     * @return the curve
     */
    public static FloatCurve linear(float from, float to) {
        return new FloatCurve(new float[] {0f, 1f}, new float[] {from, to});
    }

    /**
     * Returns a curve through keys given as pairs of time and value, times rising from {@code 0} to {@code 1}.
     *
     * @param pairs {@code t0, v0, t1, v1, ...}
     * @return the curve
     * @throws IllegalArgumentException for an odd count, no keys or falling times
     */
    public static FloatCurve of(float... pairs) {
        if (pairs.length < 2 || pairs.length % 2 != 0) {
            throw new IllegalArgumentException("A curve needs pairs of time and value");
        }
        int n = pairs.length / 2;
        float[] times = new float[n];
        float[] values = new float[n];
        for (int i = 0; i < n; i++) {
            times[i] = pairs[i * 2];
            values[i] = pairs[i * 2 + 1];
            if (i > 0 && times[i] < times[i - 1]) {
                throw new IllegalArgumentException("Curve keys must be in time order");
            }
        }
        return new FloatCurve(times, values);
    }

    /**
     * Returns the value at a time.
     *
     * @param t time, clamped to the keys
     * @return the value
     */
    public float at(float t) {
        int n = times.length;
        if (t <= times[0]) {
            return values[0];
        }
        if (t >= times[n - 1]) {
            return values[n - 1];
        }
        int i = 1;
        while (times[i] < t) {
            i++;
        }
        float span = times[i] - times[i - 1];
        float f = span <= 0f ? 1f : (t - times[i - 1]) / span;
        return values[i - 1] + (values[i] - values[i - 1]) * f;
    }

    /**
     * Returns the number of keys.
     *
     * @return at least {@code 1}
     */
    public int size() {
        return times.length;
    }

    /**
     * Returns the time of a key.
     *
     * @param index the key
     * @return the time
     */
    public float time(int index) {
        return times[index];
    }

    /**
     * Returns the value of a key.
     *
     * @param index the key
     * @return the value
     */
    public float value(int index) {
        return values[index];
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FloatCurve curve
                && Arrays.equals(curve.times, times)
                && Arrays.equals(curve.values, values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(times) * 31 + Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("FloatCurve[");
        for (int i = 0; i < times.length; i++) {
            out.append(i == 0 ? "" : ", ").append(times[i]).append(':').append(values[i]);
        }
        return out.append(']').toString();
    }
}
