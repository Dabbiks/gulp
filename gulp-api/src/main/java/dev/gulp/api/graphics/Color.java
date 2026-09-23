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

    /** Opaque red. */
    public static final Color RED = new Color(1f, 0f, 0f, 1f);
    /** Opaque green. */
    public static final Color GREEN = new Color(0f, 1f, 0f, 1f);
    /** Opaque blue. */
    public static final Color BLUE = new Color(0f, 0f, 1f, 1f);
    /** Opaque yellow. */
    public static final Color YELLOW = new Color(1f, 1f, 0f, 1f);
    /** Opaque cyan. */
    public static final Color CYAN = new Color(0f, 1f, 1f, 1f);
    /** Opaque magenta. */
    public static final Color MAGENTA = new Color(1f, 0f, 1f, 1f);
    /** Opaque orange. */
    public static final Color ORANGE = new Color(1f, 0.5f, 0f, 1f);
    /** Opaque 50% gray. */
    public static final Color GRAY = new Color(0.5f, 0.5f, 0.5f, 1f);

    /**
     * Parses {@code #rgb}, {@code #rrggbb} or {@code #rrggbbaa} (the {@code #} is optional).
     *
     * @param text the hex text
     * @return the color
     * @throws IllegalArgumentException if the text is not a hex color
     */
    public static Color hex(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        try {
            return switch (hex.length()) {
                case 3 ->
                    rgb(Integer.parseInt(
                            "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2)
                                    + hex.charAt(2),
                            16));
                case 6 -> rgb(Integer.parseInt(hex, 16));
                case 8 -> rgba((int) Long.parseLong(hex, 16));
                default -> throw new IllegalArgumentException("Not a hex color: '" + text + "'");
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Not a hex color: '" + text + "'", e);
        }
    }

    /**
     * Creates an opaque color from hue, saturation and value.
     *
     * @param hue degrees, any value (wrapped to {@code 0..360})
     * @param saturation {@code 0..1}
     * @param value {@code 0..1}
     * @return the color
     */
    public static Color hsv(float hue, float saturation, float value) {
        float h = ((hue % 360f) + 360f) % 360f / 60f;
        float c = value * saturation;
        float x = c * (1f - Math.abs(h % 2f - 1f));
        float m = value - c;
        float r;
        float g;
        float b;
        if (h < 1f) {
            r = c;
            g = x;
            b = 0f;
        } else if (h < 2f) {
            r = x;
            g = c;
            b = 0f;
        } else if (h < 3f) {
            r = 0f;
            g = c;
            b = x;
        } else if (h < 4f) {
            r = 0f;
            g = x;
            b = c;
        } else if (h < 5f) {
            r = x;
            g = 0f;
            b = c;
        } else {
            r = c;
            g = 0f;
            b = x;
        }
        return new Color(clamp(r + m), clamp(g + m), clamp(b + m), 1f);
    }

    /**
     * Interpolates towards another color, component by component.
     *
     * @param to the target
     * @param t the factor, {@code 0..1}
     * @return the interpolated color
     */
    public Color lerp(Color to, float t) {
        float f = clamp(t);
        return new Color(r + (to.r - r) * f, g + (to.g - g) * f, b + (to.b - b) * f, a + (to.a - a) * f);
    }

    /**
     * Multiplies component by component (tinting).
     *
     * @param other the other color
     * @return the product
     */
    public Color mul(Color other) {
        return new Color(r * other.r, g * other.g, b * other.b, a * other.a);
    }

    /**
     * Packs premultiplied color into {@code 0xAABBGGRR}, the byte order of GPU vertex colors.
     *
     * @return the packed premultiplied color
     */
    public int toPremultipliedAbgr() {
        return (to8(a) << 24) | (to8(b * a) << 16) | (to8(g * a) << 8) | to8(r * a);
    }

    private static float clamp(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

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
