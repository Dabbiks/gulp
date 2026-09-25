package dev.gulp.api.graphics;

import java.util.Arrays;

/**
 * Colours that change over {@code 0..1}, blended between stops: the colour of a particle over its lifetime, for
 * example. Immutable.
 *
 * <pre>{@code
 * Gradient fire = Gradient.of(Color.rgb(0xfff3a0), Color.rgb(0xff8c1a), Color.rgb(0x5a1a0a));
 * Color middle = fire.at(0.5f);
 * }</pre>
 */
public final class Gradient {

    /** Always white. */
    public static final Gradient WHITE = constant(Color.WHITE);

    private final float[] times;
    private final Color[] colors;

    private Gradient(float[] times, Color[] colors) {
        this.times = times;
        this.colors = colors;
    }

    /**
     * Returns a single colour.
     *
     * @param color the colour
     * @return the gradient
     */
    public static Gradient constant(Color color) {
        return new Gradient(new float[] {0f}, new Color[] {color});
    }

    /**
     * Returns colours spread evenly from {@code 0} to {@code 1}.
     *
     * @param colors at least one colour
     * @return the gradient
     */
    public static Gradient of(Color... colors) {
        if (colors.length == 0) {
            throw new IllegalArgumentException("A gradient needs a colour");
        }
        float[] times = new float[colors.length];
        for (int i = 0; i < colors.length; i++) {
            times[i] = colors.length == 1 ? 0f : i / (float) (colors.length - 1);
        }
        return new Gradient(times, colors.clone());
    }

    /**
     * Returns a gradient with an extra stop.
     *
     * @param t where the stop is, {@code 0..1}
     * @param color its colour
     * @return a new gradient
     */
    public Gradient with(float t, Color color) {
        int n = times.length;
        float[] nt = Arrays.copyOf(times, n + 1);
        Color[] nc = Arrays.copyOf(colors, n + 1);
        int i = n;
        while (i > 0 && nt[i - 1] > t) {
            nt[i] = nt[i - 1];
            nc[i] = nc[i - 1];
            i--;
        }
        nt[i] = t;
        nc[i] = color;
        return new Gradient(nt, nc);
    }

    /**
     * Returns the colour at a point.
     *
     * @param t {@code 0..1}, clamped to the stops
     * @return the colour
     */
    public Color at(float t) {
        int n = times.length;
        if (t <= times[0]) {
            return colors[0];
        }
        if (t >= times[n - 1]) {
            return colors[n - 1];
        }
        int i = 1;
        while (times[i] < t) {
            i++;
        }
        float span = times[i] - times[i - 1];
        return colors[i - 1].lerp(colors[i], span <= 0f ? 1f : (t - times[i - 1]) / span);
    }

    /**
     * Returns the number of stops.
     *
     * @return at least {@code 1}
     */
    public int size() {
        return times.length;
    }

    /**
     * Returns where a stop is.
     *
     * @param index the stop
     * @return {@code 0..1}
     */
    public float time(int index) {
        return times[index];
    }

    /**
     * Returns the colour of a stop.
     *
     * @param index the stop
     * @return the colour
     */
    public Color color(int index) {
        return colors[index];
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Gradient g && Arrays.equals(g.times, times) && Arrays.equals(g.colors, colors);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(times) * 31 + Arrays.hashCode(colors);
    }

    @Override
    public String toString() {
        return "Gradient" + Arrays.toString(colors);
    }
}
