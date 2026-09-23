package dev.gulp.core.graphics;

import dev.gulp.api.math.Affine2;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.render.Camera;

/**
 * {@link Camera} over a logical screen of a given size. World units are tiles; at zoom 1 one unit covers
 * {@code pixelsPerUnit} logical pixels. Screen coordinates have their origin at the top-left, Y down, like the world.
 */
public final class CameraImpl implements Camera {

    private final int pixelsPerUnit;
    private Vec2 position = Vec2.ZERO;
    private float zoom = 1f;
    private float rotation;
    private float logicalWidth = 1f;
    private float logicalHeight = 1f;

    /**
     * Creates a camera at the origin.
     *
     * @param pixelsPerUnit logical pixels per world unit at zoom 1
     */
    public CameraImpl(int pixelsPerUnit) {
        this.pixelsPerUnit = pixelsPerUnit;
    }

    /**
     * Sets the size of the logical screen.
     *
     * @param width logical width
     * @param height logical height
     */
    public void resize(float width, float height) {
        this.logicalWidth = Math.max(1f, width);
        this.logicalHeight = Math.max(1f, height);
    }

    @Override
    public Vec2 position() {
        return position;
    }

    @Override
    public void setPosition(Vec2 position) {
        this.position = position;
    }

    @Override
    public float zoom() {
        return zoom;
    }

    @Override
    public void setZoom(float zoom) {
        if (!(zoom > 0f) || Float.isInfinite(zoom)) {
            throw new IllegalArgumentException("Zoom must be positive and finite: " + zoom);
        }
        this.zoom = zoom;
    }

    @Override
    public float rotation() {
        return rotation;
    }

    @Override
    public void setRotation(float degrees) {
        this.rotation = degrees;
    }

    /**
     * Logical pixels per world unit, including zoom.
     *
     * @return the scale
     */
    public float unitScale() {
        return pixelsPerUnit * zoom;
    }

    /**
     * Writes the projection from world units to clip space into {@code out}.
     *
     * @param out the target
     * @param parallaxX horizontal parallax factor of the layer
     * @param parallaxY vertical parallax factor of the layer
     * @return {@code out}
     */
    public Affine2 projection(Affine2 out, float parallaxX, float parallaxY) {
        float halfWidth = logicalWidth / 2f / unitScale();
        float halfHeight = logicalHeight / 2f / unitScale();
        return out.identity()
                .scale(1f / halfWidth, -1f / halfHeight)
                .rotate(-rotation)
                .translate(-position.x() * parallaxX, -position.y() * parallaxY);
    }

    /**
     * Writes the transform from world units to logical screen coordinates into {@code out}.
     *
     * @param out the target
     * @return {@code out}
     */
    public Affine2 worldToScreen(Affine2 out) {
        return out.identity()
                .translate(logicalWidth / 2f, logicalHeight / 2f)
                .scale(unitScale(), unitScale())
                .rotate(-rotation)
                .translate(-position.x(), -position.y());
    }

    @Override
    public Rect bounds() {
        Affine2 inverse = worldToScreen(new Affine2()).invert();
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float sx = (i & 1) == 0 ? 0f : logicalWidth;
            float sy = (i & 2) == 0 ? 0f : logicalHeight;
            float wx = inverse.transformX(sx, sy);
            float wy = inverse.transformY(sx, sy);
            minX = Math.min(minX, wx);
            minY = Math.min(minY, wy);
            maxX = Math.max(maxX, wx);
            maxY = Math.max(maxY, wy);
        }
        return new Rect(minX, minY, maxX - minX, maxY - minY);
    }

    @Override
    public Vec2 screenToWorld(Vec2 screen) {
        return worldToScreen(new Affine2()).invert().apply(screen);
    }

    @Override
    public Vec2 worldToScreen(Vec2 world) {
        return worldToScreen(new Affine2()).apply(world);
    }
}
