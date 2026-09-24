package dev.gulp.api.input;

/**
 * A finger on a touch screen, in logical screen points. The object is reused after the finger lifts, so do not keep
 * it.
 *
 * <pre>{@code
 * for (Touch touch : input().touches()) {
 *     draw.circle(touch.x(), touch.y(), 20, Color.WHITE);
 * }
 * }</pre>
 */
public interface Touch {

    /**
     * Returns the identifier, stable while the finger touches.
     *
     * @return the identifier
     */
    int id();

    /**
     * Returns the horizontal position.
     *
     * @return points from the left edge
     */
    float x();

    /**
     * Returns the vertical position.
     *
     * @return points from the top edge
     */
    float y();

    /**
     * Returns where the finger went down, horizontally.
     *
     * @return points from the left edge
     */
    float startX();

    /**
     * Returns where the finger went down, vertically.
     *
     * @return points from the top edge
     */
    float startY();

    /**
     * Returns how long the finger has touched.
     *
     * @return seconds
     */
    float seconds();
}
