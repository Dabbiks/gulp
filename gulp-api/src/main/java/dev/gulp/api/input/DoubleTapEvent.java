package dev.gulp.api.input;

/**
 * A second tap followed the first one quickly and nearby; the first one was also reported as {@link TapEvent}.
 *
 * <pre>{@code
 * on(DoubleTapEvent.class, e -> zoomTo(e.x(), e.y()));
 * }</pre>
 */
public final class DoubleTapEvent extends InputEvent {

    private final float x;
    private final float y;

    /**
     * Creates the event.
     *
     * @param x points from the left edge
     * @param y points from the top edge
     */
    public DoubleTapEvent(float x, float y) {
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
