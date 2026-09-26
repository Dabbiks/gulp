package dev.gulp.api.debug;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;

/**
 * Shapes drawn in the active world, in world units, over everything else. Each shape stays for a number of ticks
 * (one by default, so a call every tick draws continuously). A no-op when the developer tools are off.
 *
 * <pre>{@code
 * debug().draw()
 *         .line(a, b, Color.YELLOW)
 *         .circle(enemy.position(), enemy.viewRange(), Color.RED, 60)
 *         .text(enemy.position().add(0, -1), enemy.state(), Color.WHITE);
 * }</pre>
 */
public interface DebugDraw {

    /**
     * Draws a line for one tick.
     *
     * @param from the start
     * @param to the end
     * @param color the color
     * @return this drawing
     */
    default DebugDraw line(Vec2 from, Vec2 to, Color color) {
        return line(from, to, color, 1);
    }

    /**
     * Draws a line.
     *
     * @param from the start
     * @param to the end
     * @param color the color
     * @param ticks how long it stays, at least 1
     * @return this drawing
     */
    DebugDraw line(Vec2 from, Vec2 to, Color color, int ticks);

    /**
     * Draws a rectangle outline for one tick.
     *
     * @param rect the rectangle
     * @param color the color
     * @return this drawing
     */
    default DebugDraw rect(Rect rect, Color color) {
        return rect(rect, color, 1);
    }

    /**
     * Draws a rectangle outline.
     *
     * @param rect the rectangle
     * @param color the color
     * @param ticks how long it stays, at least 1
     * @return this drawing
     */
    DebugDraw rect(Rect rect, Color color, int ticks);

    /**
     * Draws a circle outline for one tick.
     *
     * @param center the center
     * @param radius the radius
     * @param color the color
     * @return this drawing
     */
    default DebugDraw circle(Vec2 center, float radius, Color color) {
        return circle(center, radius, color, 1);
    }

    /**
     * Draws a circle outline.
     *
     * @param center the center
     * @param radius the radius
     * @param color the color
     * @param ticks how long it stays, at least 1
     * @return this drawing
     */
    DebugDraw circle(Vec2 center, float radius, Color color, int ticks);

    /**
     * Draws an arrow for one tick.
     *
     * @param from the tail
     * @param to the head
     * @param color the color
     * @return this drawing
     */
    default DebugDraw arrow(Vec2 from, Vec2 to, Color color) {
        return arrow(from, to, color, 1);
    }

    /**
     * Draws an arrow.
     *
     * @param from the tail
     * @param to the head
     * @param color the color
     * @param ticks how long it stays, at least 1
     * @return this drawing
     */
    DebugDraw arrow(Vec2 from, Vec2 to, Color color, int ticks);

    /**
     * Draws text for one tick, centred on a point, at a fixed screen size.
     *
     * @param at the point
     * @param text the text
     * @param color the color
     * @return this drawing
     */
    default DebugDraw text(Vec2 at, String text, Color color) {
        return text(at, text, color, 1);
    }

    /**
     * Draws text centred on a point, at a fixed screen size.
     *
     * @param at the point
     * @param text the text
     * @param color the color
     * @param ticks how long it stays, at least 1
     * @return this drawing
     */
    DebugDraw text(Vec2 at, String text, Color color, int ticks);

    /** Removes every shape at once. */
    void clear();
}
