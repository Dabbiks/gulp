package dev.gulp.api.input;

/**
 * A finger stayed still for half a second.
 *
 * <pre>{@code
 * on(LongPressEvent.class, e -> openContextMenu(e.x(), e.y()));
 * }</pre>
 */
public final class LongPressEvent extends InputEvent {

    private final float x;
    private final float y;

    /**
     * Creates the event.
     *
     * @param x points from the left edge
     * @param y points from the top edge
     */
    public LongPressEvent(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns points from the left edge.
     *
     * @return points from the left edge
     */
    public float x() {
        return x;
    }

    /**
     * Returns points from the top edge.
     *
     * @return points from the top edge
     */
    public float y() {
        return y;
    }
}
