package dev.gulp.api.input;

/**
 * A mouse button went up.
 *
 * <pre>{@code
 * on(MouseButtonReleaseEvent.class, e -> drop(e.x(), e.y()));
 * }</pre>
 */
public final class MouseButtonReleaseEvent extends MouseButtonEvent {

    /**
     * Creates the event.
     *
     * @param button the button
     * @param x points from the left edge
     * @param y points from the top edge
     * @param modifiers modifier bits
     */
    public MouseButtonReleaseEvent(MouseButton button, float x, float y, int modifiers) {
        super(button, x, y, modifiers);
    }
}
