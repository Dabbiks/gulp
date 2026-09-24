package dev.gulp.api.input;

/**
 * Base of mouse button events.
 *
 * <pre>{@code
 * on(MouseButtonPressEvent.class, e -> {
 *     if (e.button() == MouseButton.RIGHT && (e.modifiers() & KeyEvent.SHIFT) != 0) { queueOrder(e.x(), e.y()); }
 * });
 * }</pre>
 */
public abstract sealed class MouseButtonEvent extends InputEvent
        permits MouseButtonPressEvent, MouseButtonReleaseEvent {

    private final MouseButton button;
    private final float x;
    private final float y;
    private final int modifiers;

    /**
     * Creates the event.
     *
     * @param button the button
     * @param x points from the left edge
     * @param y points from the top edge
     * @param modifiers modifier bits, see {@link KeyEvent}
     */
    protected MouseButtonEvent(MouseButton button, float x, float y, int modifiers) {
        this.button = button;
        this.x = x;
        this.y = y;
        this.modifiers = modifiers;
    }

    /**
     * Returns the button.
     *
     * @return the button
     */
    public final MouseButton button() {
        return button;
    }

    /**
     * Returns the pointer position.
     *
     * @return points from the left edge
     */
    public final float x() {
        return x;
    }

    /**
     * Returns the pointer position.
     *
     * @return points from the top edge
     */
    public final float y() {
        return y;
    }

    /**
     * Returns the held modifiers.
     *
     * @return modifier bits, see {@link KeyEvent}
     */
    public final int modifiers() {
        return modifiers;
    }
}
