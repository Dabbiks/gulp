package dev.gulp.api.input;

/**
 * A finger touched and lifted quickly without moving.
 *
 * <pre>{@code
 * on(TapEvent.class, e -> select(e.x(), e.y()));
 * }</pre>
 */
public final class TapEvent extends InputEvent {

    private final float x;
    private final float y;

    /**
     * Creates the event.
     *
     * @param x points from the left edge
     * @param y points from the top edge
     */
    public TapEvent(float x, float y) {
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
