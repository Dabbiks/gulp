package dev.gulp.api.render;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.Ease;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import org.jspecify.annotations.Nullable;

/**
 * The view into the world. {@link #position()} is the world point shown at the center of the screen; at zoom 1 one
 * world unit covers {@code pixelsPerUnit} base pixels ({@code GameSettings.pixelsPerUnit}). It can follow an entity with
 * smoothing, a dead zone and look-ahead, stay inside limits, shake, animate zoom and position, and draw into part of
 * the screen for split screen or a minimap.
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

    /**
     * Follows an entity every frame, using its interpolated position.
     *
     * @param target the entity, or {@code null} to stop following
     * @return this camera
     */
    Camera follow(@Nullable Entity target);

    /**
     * Returns the followed entity.
     *
     * @return the entity, or {@code null}
     */
    @Nullable Entity target();

    /**
     * Smooths following independently of the frame rate: after this time most of the distance is covered.
     *
     * @param seconds the smoothing time, {@code 0} to follow exactly
     * @return this camera
     */
    Camera smoothing(float seconds);

    /**
     * Lets the target move inside a box around the centre without moving the camera.
     *
     * @param width world units
     * @param height world units
     * @return this camera
     */
    Camera deadZone(float width, float height);

    /**
     * Leads the target in the direction it moves.
     *
     * @param seconds how far ahead, in seconds of the target's motion
     * @return this camera
     */
    Camera lookAhead(float seconds);

    /**
     * Keeps the view inside an area; a smaller area than the view is centred.
     *
     * @param area world units, or {@code null} for none
     * @return this camera
     */
    Camera limits(@Nullable Rect area);

    /**
     * Shifts the followed point, for example to show more of the ground ahead.
     *
     * @param value world units
     * @return this camera
     */
    Camera offset(Vec2 value);

    /**
     * Shakes the view. Trauma adds up to at most 1 and decays to 0 over the given time; the shake grows with the square
     * of the trauma.
     *
     * @param trauma how strong, {@code 0..1}
     * @param seconds how long the trauma takes to decay from 1
     */
    void shake(float trauma, float seconds);

    /**
     * Animates the zoom.
     *
     * @param zoom the target zoom
     * @param seconds the length
     * @param ease the easing
     */
    void zoomTo(float zoom, float seconds, Ease ease);

    /**
     * Animates the position; following pauses until it ends.
     *
     * @param position the target, world units
     * @param seconds the length
     * @param ease the easing
     */
    void panTo(Vec2 position, float seconds, Ease ease);

    /**
     * Returns the part of the screen this camera draws into.
     *
     * @return {@code 0..1} on both axes, the whole screen by default
     */
    Rect viewport();

    /**
     * Changes the part of the screen this camera draws into.
     *
     * @param area {@code 0..1} on both axes
     */
    void setViewport(Rect area);
}
