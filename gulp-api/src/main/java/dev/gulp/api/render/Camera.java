package dev.gulp.api.render;

import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;

/**
 * The view into the world. {@link #position()} is the world point shown at the center of the screen; at zoom 1 one
 * world unit covers {@code pixelsPerUnit} base pixels ({@code GameSettings.pixelsPerUnit}). Following, shaking and
 * multiple cameras arrive in stage 6.
 *
 * <pre>{@code
 * Camera camera = display().camera();
 * camera.setPosition(player.position());
 * camera.setZoom(2f);
 * Vec2 mouseInWorld = camera.screenToWorld(mouseOnScreen);
 * }</pre>
 */
public interface Camera {

    /**
     * World point at the center of the view.
     *
     * @return the position
     */
    Vec2 position();

    /**
     * Moves the camera.
     *
     * @param position the world point to center on
     */
    void setPosition(Vec2 position);

    /**
     * Zoom factor.
     *
     * @return {@code 1} by default; larger shows less of the world
     */
    float zoom();

    /**
     * Changes the zoom.
     *
     * @param zoom positive factor
     * @throws IllegalArgumentException if not positive
     */
    void setZoom(float zoom);

    /**
     * Rotation of the view.
     *
     * @return degrees
     */
    float rotation();

    /**
     * Rotates the view.
     *
     * @param degrees the angle
     */
    void setRotation(float degrees);

    /**
     * Visible world area (axis-aligned bounds when rotated).
     *
     * @return the area in world units
     */
    Rect bounds();

    /**
     * Converts logical screen coordinates to world coordinates.
     *
     * @param screen the screen point
     * @return the world point
     */
    Vec2 screenToWorld(Vec2 screen);

    /**
     * Converts world coordinates to logical screen coordinates.
     *
     * @param world the world point
     * @return the screen point
     */
    Vec2 worldToScreen(Vec2 world);
}
