package dev.gulp.api.input;

/**
 * One finger dragged. Fired at most once per frame with the motion of the frame.
 *
 * <pre>{@code
 * on(PanEvent.class, e -> camera.setPosition(camera.position().sub(e.deltaX() / 16f, e.deltaY() / 16f)));
 * }</pre>
 */
public final class PanEvent extends InputEvent {

    private final float x;
    private final float y;
    private final float deltaX;
    private final float deltaY;

    /**
     * Creates the event.
     *
     * @param x current position
     * @param y current position
     * @param deltaX motion since the last event
     * @param deltaY motion since the last event
     */
    public PanEvent(float x, float y, float deltaX, float deltaY) {
        this.x = x;
        this.y = y;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }

    /**
     * Returns current position.
     *
     * @return current position
     */
    public float x() {
        return x;
    }

    /**
     * Returns current position.
     *
     * @return current position
     */
    public float y() {
        return y;
    }

    /**
     * Returns motion since the last event.
     *
     * @return motion since the last event
     */
    public float deltaX() {
        return deltaX;
    }

    /**
     * Returns motion since the last event.
     *
     * @return motion since the last event
     */
    public float deltaY() {
        return deltaY;
    }
}
