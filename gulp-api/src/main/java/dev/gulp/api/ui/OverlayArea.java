package dev.gulp.api.ui;

import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;

/**
 * The screen as seen by overlay drawing: its size in UI points and helpers that place things relative to it.
 *
 * <pre>{@code
 * Rect badge = screen.anchor(Anchor.TOP_RIGHT, 8, 8, 64, 24);  // 8 points from the top-right corner
 * draw.text("FPS " + fps, screen.center().x(), 8, TextStyle.of(12), TextAlign.TOP);
 * }</pre>
 */
public interface OverlayArea {

    /**
     * Returns the width.
     *
     * @return UI points
     */
    float width();

    /**
     * Returns the height.
     *
     * @return UI points
     */
    float height();

    /**
     * Returns the centre.
     *
     * @return the middle of the screen
     */
    default Vec2 center() {
        return new Vec2(width() / 2f, height() / 2f);
    }

    /**
     * Places a rectangle of a given size by an anchor, with an inward offset like {@link Node#offset}.
     *
     * @param anchor where it sticks
     * @param dx horizontal offset
     * @param dy vertical offset
     * @param w width, or the inset for stretched axes
     * @param h height, or the inset for stretched axes
     * @return the rectangle in UI points
     */
    default Rect anchor(Anchor anchor, float dx, float dy, float w, float h) {
        float rw = Anchor.place(anchor.minX(), anchor.maxX(), 0f, width(), w, dx, false);
        float rh = Anchor.place(anchor.minY(), anchor.maxY(), 0f, height(), h, dy, false);
        return new Rect(
                Anchor.place(anchor.minX(), anchor.maxX(), 0f, width(), rw, dx, true),
                Anchor.place(anchor.minY(), anchor.maxY(), 0f, height(), rh, dy, true),
                rw,
                rh);
    }
}
