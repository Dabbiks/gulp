package dev.gulp.api.graphics;

/**
 * Immutable RGBA color with float components in the range {@code [0, 1]}, not premultiplied.
 *
 * <pre>{@code
 * Color sky = Color.rgb(0x1d2b53);
 * Color glass = new Color(1f, 1f, 1f, 0.25f);
 * Color faded = sky.withAlpha(0.5f);
 * }</pre>
 *
 * @param r red component, {@code 0..1}
 * @param g green component, {@code 0..1}
 * @param b blue component, {@code 0..1}
 * @param a alpha component, {@code 0..1}, where {@code 1} is opaque
 */
public record Color(float r, float g, float b, float a) {

    /** Opaque black. */
    public static final Color BLACK = new Color(0f, 0f, 0f, 1f);

    /** Opaque white. */
    public static final Color WHITE = new Color(1f, 1f, 1f, 1f);

    /** Fully transparent black. */
    public static final Color CLEAR = new Color(0f, 0f, 0f, 0f);

    /**
     * Validates the components.
     *
     * @throws IllegalArgumentException if any component is outside {@code [0, 1]} or NaN
     */
    public Color {
        requireUnit("r", r);
        requireUnit("g", g);
        requireUnit("b", b);
        requireUnit("a", a);
    }

    /**
     * Creates an opaque color from a {@code 0xRRGGBB} value.
     *
     * @param rgb color as {@code 0xRRGGBB}; higher bits are ignored
     * @return the opaque color
     */
    public static Color rgb(int rgb) {
        return new Color(channel(rgb, 16), channel(rgb, 8), channel(rgb, 0), 1f);
    }

    /**
     * Creates a color from a {@code 0xRRGGBBAA} value.
     *
     * @param rgba color as {@code 0xRRGGBBAA}
     * @return the color
     */
    public static Color rgba(int rgba) {
        return new Color(channel(rgba, 24), channel(rgba, 16), channel(rgba, 8), channel(rgba, 0));
    }

    /**
     * Returns this color with a different alpha.
     *
     * @param alpha the new alpha, {@code 0..1}
     * @return a new color with the same RGB components
     */
    public Color withAlpha(float alpha) {
        return new Color(r, g, b, alpha);
    }

    /**
     * Packs this color into a {@code 0xRRGGBBAA} integer, rounding each component to 8 bits.
     *
     * @return the packed color
     */
    public int toRgba8888() {
        return (to8(r) << 24) | (to8(g) << 16) | (to8(b) << 8) | to8(a);
    }

    private static float channel(int value, int shift) {
        return ((value >>> shift) & 0xff) / 255f;
    }

    private static int to8(float component) {
        return Math.round(component * 255f);
    }

    private static void requireUnit(String name, float value) {
        if (!(value >= 0f && value <= 1f)) {
            throw new IllegalArgumentException("Color component " + name + " must be in [0, 1], got " + value);
        }
    }
}
