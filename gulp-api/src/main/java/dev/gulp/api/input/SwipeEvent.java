package dev.gulp.api.input;

/**
 * A finger moved fast in one direction and lifted.
 *
 * <pre>{@code
 * on(SwipeEvent.class, e -> {
 *     if (e.directionX() < -0.7f) { nextPage(); }
 * });
 * }</pre>
 */
public final class SwipeEvent extends InputEvent {

    private final float directionX;
    private final float directionY;
    private final float speed;

    /**
     * Creates the event.
     *
     * @param directionX unit direction, horizontal part
     * @param directionY unit direction, vertical part
     * @param speed points per second
     */
    public SwipeEvent(float directionX, float directionY, float speed) {
        this.directionX = directionX;
        this.directionY = directionY;
        this.speed = speed;
    }

    /**
     * Returns unit direction, horizontal part.
     *
     * @return unit direction, horizontal part
     */
    public float directionX() {
        return directionX;
    }

    /**
     * Returns unit direction, vertical part.
     *
     * @return unit direction, vertical part
     */
    public float directionY() {
        return directionY;
    }

    /**
     * Returns points per second.
     *
     * @return points per second
     */
    public float speed() {
        return speed;
    }
}
