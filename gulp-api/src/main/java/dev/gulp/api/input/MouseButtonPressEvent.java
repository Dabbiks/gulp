package dev.gulp.api.input;

/**
 * A mouse button went down.
 *
 * <pre>{@code
 * on(MouseButtonPressEvent.class, e -> {
 *     if (e.button() == MouseButton.LEFT) { select(e.x(), e.y()); }
 * });
 * }</pre>
 */
public final class MouseButtonPressEvent extends MouseButtonEvent {

    /**
     * Creates the event.
     *
     * @param button the button
     * @param x points from the left edge
     * @param y points from the top edge
     * @param modifiers modifier bits
     */
    public MouseButtonPressEvent(MouseButton button, float x, float y, int modifiers) {
        super(button, x, y, modifiers);
    }
}
