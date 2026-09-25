package dev.gulp.api.ui;

/**
 * Where a node sticks to its parent when it is not placed by a row, column or grid: in a {@link Stack}, on the HUD or
 * as the root of a screen. Each edge has an anchor from 0 (left or top of the parent) to 1 (right or bottom).
 *
 * <p>When an axis has one anchor point ({@code min == max}) the node keeps its measured size there, and {@link
 * Node#offset} moves it inward from the edge it sticks to (or right and down from the centre). When an axis spans
 * ({@code min < max}) the node stretches, and the offset insets both ends.
 *
 * <pre>{@code
 * ui().hud().add(this, label(coins.map(c -> "Coins: " + c)).anchor(Anchor.TOP_LEFT).offset(8, 8));
 * button("Pause").anchor(Anchor.TOP_RIGHT).offset(8, 8);   // 8 points from the top-right corner
 * panel(chat).anchor(Anchor.BOTTOM_WIDE);                  // full width along the bottom
 * }</pre>
 *
 * @param minX anchor of the left edge
 * @param minY anchor of the top edge
 * @param maxX anchor of the right edge
 * @param maxY anchor of the bottom edge
 */
public record Anchor(float minX, float minY, float maxX, float maxY) {

    /** Top-left corner. */
    public static final Anchor TOP_LEFT = new Anchor(0f, 0f, 0f, 0f);
    /** Middle of the top edge. */
    public static final Anchor TOP = new Anchor(0.5f, 0f, 0.5f, 0f);
    /** Top-right corner. */
    public static final Anchor TOP_RIGHT = new Anchor(1f, 0f, 1f, 0f);
    /** Middle of the left edge. */
    public static final Anchor LEFT = new Anchor(0f, 0.5f, 0f, 0.5f);
    /** Centre. */
    public static final Anchor CENTER = new Anchor(0.5f, 0.5f, 0.5f, 0.5f);
    /** Middle of the right edge. */
    public static final Anchor RIGHT = new Anchor(1f, 0.5f, 1f, 0.5f);
    /** Bottom-left corner. */
    public static final Anchor BOTTOM_LEFT = new Anchor(0f, 1f, 0f, 1f);
    /** Middle of the bottom edge. */
    public static final Anchor BOTTOM = new Anchor(0.5f, 1f, 0.5f, 1f);
    /** Bottom-right corner. */
    public static final Anchor BOTTOM_RIGHT = new Anchor(1f, 1f, 1f, 1f);
    /** Full width along the top edge. */
    public static final Anchor TOP_WIDE = new Anchor(0f, 0f, 1f, 0f);
    /** Full width along the bottom edge. */
    public static final Anchor BOTTOM_WIDE = new Anchor(0f, 1f, 1f, 1f);
    /** Full height along the left edge. */
    public static final Anchor LEFT_WIDE = new Anchor(0f, 0f, 0f, 1f);
    /** Full height along the right edge. */
    public static final Anchor RIGHT_WIDE = new Anchor(1f, 0f, 1f, 1f);
    /** The whole parent. */
    public static final Anchor FULL = new Anchor(0f, 0f, 1f, 1f);

    /**
     * Creates anchors, each from 0 to 1.
     *
     * @param minX anchor of the left edge
     * @param minY anchor of the top edge
     * @param maxX anchor of the right edge
     * @param maxY anchor of the bottom edge
     * @throws IllegalArgumentException if a value is outside 0..1 or a minimum is above its maximum
     */
    public Anchor {
        if (minX < 0f || minY < 0f || maxX > 1f || maxY > 1f || minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("Anchors must satisfy 0 <= min <= max <= 1: " + this);
        }
    }

    /**
     * Places a node of a given size inside a parent rectangle along one axis.
     *
     * @param min anchor of the start edge
     * @param max anchor of the end edge
     * @param start parent start
     * @param length parent length
     * @param size the node size along the axis (used when the axis has one anchor point)
     * @param offset the node offset along the axis
     * @param start1 whether to return the start ({@code true}) or the length ({@code false})
     * @return the start or the length of the node
     */
    static float place(float min, float max, float start, float length, float size, float offset, boolean start1) {
        if (min < max) {
            float from = start + min * length + offset;
            float to = start + max * length - offset;
            return start1 ? from : Math.max(0f, to - from);
        }
        if (!start1) {
            return size;
        }
        float point = start + min * length;
        if (min == 0f) {
            return point + offset;
        }
        if (min == 1f) {
            return point - size - offset;
        }
        return point - size * min + offset;
    }
}
