package dev.gulp.api.input;

/**
 * The mouse moved. Fired at most once per frame, with the motion of the whole frame.
 *
 * <pre>{@code
 * on(MouseMoveEvent.class, e -> hover(e.x(), e.y()));
 * }</pre>
 */
public final class MouseMoveEvent extends InputEvent {

    private final float x;
    private final float y;
    private final float deltaX;
    private final float deltaY;

    /**
     * Creates the event.
     *
     * @param x points from the left edge
     * @param y points from the top edge
     * @param deltaX horizontal motion since the last event
     * @param deltaY vertical motion since the last event
     */
    public MouseMoveEvent(float x, float y, float deltaX, float deltaY) {
        this.x = x;
        this.y = y;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
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

    /**
     * Returns horizontal motion since the last event.
     *
     * @return horizontal motion since the last event
     */
    public float deltaX() {
        return deltaX;
    }

    /**
     * Returns vertical motion since the last event.
     *
     * @return vertical motion since the last event
     */
    public float deltaY() {
        return deltaY;
    }
}
