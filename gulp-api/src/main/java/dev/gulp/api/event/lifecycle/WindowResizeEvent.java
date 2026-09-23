package dev.gulp.api.event.lifecycle;

import dev.gulp.api.event.Event;

/**
 * Fired when the window or browser canvas changes size.
 *
 * <pre>{@code
 * on(WindowResizeEvent.class, e -> logger().debug("Window " + e.width() + "x" + e.height()));
 * }</pre>
 */
public final class WindowResizeEvent extends Event {

    private final int width;
    private final int height;

    /**
     * Creates the event; fired by the engine.
     *
     * @param width new width in logical points
     * @param height new height in logical points
     */
    public WindowResizeEvent(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Returns the new width.
     *
     * @return width in logical points
     */
    public int width() {
        return width;
    }

    /**
     * Returns the new height.
     *
     * @return height in logical points
     */
    public int height() {
        return height;
    }
}
