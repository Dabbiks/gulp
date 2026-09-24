package dev.gulp.api.input;

/**
 * The mouse wheel or touchpad scrolled.
 *
 * <pre>{@code
 * on(MouseScrollEvent.class, e -> camera.setZoom(camera.zoom() * (1f - e.deltaY() * 0.1f)));
 * }</pre>
 */
public final class MouseScrollEvent extends InputEvent {

    private final float deltaX;
    private final float deltaY;

    /**
     * Creates the event.
     *
     * @param deltaX scroll steps, positive to the right
     * @param deltaY scroll steps, positive downwards
     */
    public MouseScrollEvent(float deltaX, float deltaY) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }

    /**
     * Returns scroll steps, positive to the right.
     *
     * @return scroll steps, positive to the right
     */
    public float deltaX() {
        return deltaX;
    }

    /**
     * Returns scroll steps, positive downwards.
     *
     * @return scroll steps, positive downwards
     */
    public float deltaY() {
        return deltaY;
    }
}
