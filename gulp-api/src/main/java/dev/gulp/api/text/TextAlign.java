package dev.gulp.api.text;

/**
 * Where text sits relative to a point or inside a rectangle: a horizontal and a vertical part.
 *
 * <pre>{@code
 * draw.text("Pauza", display().width() / 2, display().height() / 2, TextStyle.of(32), TextAlign.CENTER);
 * }</pre>
 */
public enum TextAlign {
    /** Left and top. */
    TOP_LEFT(0f, 0f),
    /** Centered horizontally, top. */
    TOP(0.5f, 0f),
    /** Right and top. */
    TOP_RIGHT(1f, 0f),
    /** Left, centered vertically. */
    LEFT(0f, 0.5f),
    /** Centered both ways. */
    CENTER(0.5f, 0.5f),
    /** Right, centered vertically. */
    RIGHT(1f, 0.5f),
    /** Left and bottom. */
    BOTTOM_LEFT(0f, 1f),
    /** Centered horizontally, bottom. */
    BOTTOM(0.5f, 1f),
    /** Right and bottom. */
    BOTTOM_RIGHT(1f, 1f);

    private final float horizontal;
    private final float vertical;

    TextAlign(float horizontal, float vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    /**
     * Returns the horizontal part.
     *
     * @return 0 left, 0.5 center, 1 right
     */
    public float horizontal() {
        return horizontal;
    }

    /**
     * Returns the vertical part.
     *
     * @return 0 top, 0.5 middle, 1 bottom
     */
    public float vertical() {
        return vertical;
    }
}
