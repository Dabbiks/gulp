package dev.gulp.core.util;

/**
 * Average of the last {@code n} samples, for frame times and similar statistics; adding a sample does not allocate.
 *
 * <pre>{@code
 * RollingAverage frameMs = new RollingAverage(60);
 * frameMs.add(delta * 1000f);
 * float average = frameMs.average();
 * }</pre>
 */
public final class RollingAverage {

    private final float[] samples;
    private int next;
    private int count;
    private float sum;

    /**
     * Creates an empty average.
     *
     * @param window how many recent samples count
     * @throws IllegalArgumentException if {@code window < 1}
     */
    public RollingAverage(int window) {
        if (window < 1) {
            throw new IllegalArgumentException("Window must be at least 1, was " + window);
        }
        samples = new float[window];
    }

    /**
     * Adds a sample, dropping the oldest one when the window is full.
     *
     * @param value the sample
     */
    public void add(float value) {
        if (count == samples.length) {
            sum -= samples[next];
        } else {
            count++;
        }
        samples[next] = value;
        sum += value;
        next = (next + 1) % samples.length;
    }

    /**
     * Returns the average of the samples in the window.
     *
     * @return the average, {@code 0} without samples
     */
    public float average() {
        return count == 0 ? 0f : sum / count;
    }

    /**
     * Returns the largest sample in the window.
     *
     * @return the maximum, {@code 0} without samples
     */
    public float max() {
        if (count == 0) {
            return 0f;
        }
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            max = Math.max(max, samples[i]);
        }
        return max;
    }

    /**
     * Returns the number of samples in the window.
     *
     * @return at most the window size
     */
    public int count() {
        return count;
    }

    /** Forgets all samples. */
    public void clear() {
        next = 0;
        count = 0;
        sum = 0f;
    }
}
