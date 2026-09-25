package dev.gulp.api.ui;

/**
 * Space around the four edges of a node, in UI points.
 *
 * <pre>{@code
 * margin(Insets.all(16), label("Settings"));
 * panel(child).style(s -> s.padding(Insets.symmetric(12, 6)));
 * }</pre>
 *
 * @param top space above
 * @param right space on the right
 * @param bottom space below
 * @param left space on the left
 */
public record Insets(float top, float right, float bottom, float left) {

    /** No space. */
    public static final Insets ZERO = new Insets(0f, 0f, 0f, 0f);

    /**
     * The same space on every edge.
     *
     * @param value UI points
     * @return the insets
     */
    public static Insets all(float value) {
        return new Insets(value, value, value, value);
    }

    /**
     * Horizontal space left and right, vertical space above and below.
     *
     * @param horizontal left and right
     * @param vertical top and bottom
     * @return the insets
     */
    public static Insets symmetric(float horizontal, float vertical) {
        return new Insets(vertical, horizontal, vertical, horizontal);
    }

    /**
     * Space on each edge, clockwise from the top.
     *
     * @param top space above
     * @param right space on the right
     * @param bottom space below
     * @param left space on the left
     * @return the insets
     */
    public static Insets of(float top, float right, float bottom, float left) {
        return new Insets(top, right, bottom, left);
    }

    /**
     * Returns left plus right.
     *
     * @return the horizontal total
     */
    public float horizontal() {
        return left + right;
    }

    /**
     * Returns top plus bottom.
     *
     * @return the vertical total
     */
    public float vertical() {
        return top + bottom;
    }
}
