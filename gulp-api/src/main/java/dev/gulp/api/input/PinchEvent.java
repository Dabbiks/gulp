package dev.gulp.api.input;

/**
 * Two fingers moved apart or together. Fired at most once per frame.
 *
 * <pre>{@code
 * on(PinchEvent.class, e -> camera.setZoom(camera.zoom() * e.scale()));
 * }</pre>
 */
public final class PinchEvent extends InputEvent {

    private final float centerX;
    private final float centerY;
    private final float scale;

    /**
     * Creates the event.
     *
     * @param centerX point between the fingers
     * @param centerY point between the fingers
     * @param scale distance change since the last event, above 1 when spreading
     */
    public PinchEvent(float centerX, float centerY, float scale) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.scale = scale;
    }

    /**
     * Returns point between the fingers.
     *
     * @return point between the fingers
     */
    public float centerX() {
        return centerX;
    }

    /**
     * Returns point between the fingers.
     *
     * @return point between the fingers
     */
    public float centerY() {
        return centerY;
    }

    /**
     * Returns distance change since the last event, above 1 when spreading.
     *
     * @return distance change since the last event, above 1 when spreading
     */
    public float scale() {
        return scale;
    }
}
