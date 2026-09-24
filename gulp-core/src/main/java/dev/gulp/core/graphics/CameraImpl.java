package dev.gulp.core.graphics;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.Affine2;
import dev.gulp.api.math.Ease;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.render.Camera;
import org.jspecify.annotations.Nullable;

/**
 * {@link Camera} over a logical screen of a given size. World units are tiles; at zoom 1 one unit covers
 * {@code pixelsPerUnit} logical pixels. Screen coordinates have their origin at the top-left, Y down, like the world.
 *
 * <p>Following, pan and zoom animations and shaking advance in {@link #update}, called once per frame with the
 * interpolated position of the target; shaking only moves the picture, not {@link #position()}.
 */
public final class CameraImpl implements Camera {

    private static final float SHAKE_OFFSET = 0.06f;
    private static final float SHAKE_DEGREES = 3f;

    private final int pixelsPerUnit;
    private final Affine2 scratch = new Affine2();
    private final Affine2 screenTransform = new Affine2();
    private Vec2 position = Vec2.ZERO;
    private float x;
    private float y;
    private float zoom = 1f;
    private float rotation;
    private float logicalWidth = 1f;
    private float logicalHeight = 1f;
    private Rect viewport = new Rect(0, 0, 1, 1);

    private @Nullable Entity target;
    private float smoothing;
    private float deadZoneWidth;
    private float deadZoneHeight;
    private float lookAhead;
    private @Nullable Rect limits;
    private Vec2 offset = Vec2.ZERO;

    private float trauma;
    private float traumaDecay = 1f;
    private float shakeTime;
    private float shakeX;
    private float shakeY;
    private float shakeRotation;

    private boolean panning;
    private float panFromX;
    private float panFromY;
    private float panToX;
    private float panToY;
    private float panTime;
    private float panDuration;
    private Ease panEase = Ease.LINEAR;

    private boolean zooming;
    private float zoomFrom;
    private float zoomTarget;
    private float zoomTime;
    private float zoomDuration;
    private Ease zoomEase = Ease.LINEAR;

    /**
     * Creates a camera at the origin.
     *
     * @param pixelsPerUnit logical pixels per world unit at zoom 1
     */
    public CameraImpl(int pixelsPerUnit) {
        this.pixelsPerUnit = pixelsPerUnit;
    }

    /**
     * Sets the size of the logical screen area the camera draws into.
     *
     * @param width logical width
     * @param height logical height
     */
    public void resize(float width, float height) {
        this.logicalWidth = Math.max(1f, width);
        this.logicalHeight = Math.max(1f, height);
    }

    /**
     * Advances following, animations and shaking.
     *
     * @param seconds time since the last frame
     * @param targetX interpolated x of the target
     * @param targetY interpolated y of the target
     * @param velocityX target speed in units per second, for look-ahead
     * @param velocityY target speed in units per second
     */
    public void update(float seconds, float targetX, float targetY, float velocityX, float velocityY) {
        if (zooming) {
            zoomTime += seconds;
            float t = Math.min(1f, zoomTime / zoomDuration);
            zoom = zoomFrom + (zoomTarget - zoomFrom) * zoomEase.apply(t);
            zooming = t < 1f;
        }
        if (panning) {
            panTime += seconds;
            float t = Math.min(1f, panTime / panDuration);
            float e = panEase.apply(t);
            moveTo(panFromX + (panToX - panFromX) * e, panFromY + (panToY - panFromY) * e);
            panning = t < 1f;
        } else if (target != null) {
            float wantX = targetX + offset.x() + velocityX * lookAhead;
            float wantY = targetY + offset.y() + velocityY * lookAhead;
            float halfDeadX = deadZoneWidth / 2f;
            float halfDeadY = deadZoneHeight / 2f;
            float desiredX = x;
            float desiredY = y;
            if (wantX > x + halfDeadX) {
                desiredX = wantX - halfDeadX;
            } else if (wantX < x - halfDeadX) {
                desiredX = wantX + halfDeadX;
            }
            if (wantY > y + halfDeadY) {
                desiredY = wantY - halfDeadY;
            } else if (wantY < y - halfDeadY) {
                desiredY = wantY + halfDeadY;
            }
            if (smoothing > 0f) {
                float k = 1f - (float) Math.exp(-seconds * 4.6f / smoothing);
                moveTo(x + (desiredX - x) * k, y + (desiredY - y) * k);
            } else {
                moveTo(desiredX, desiredY);
            }
        }
        applyLimits();
        if (trauma > 0f) {
            shakeTime += seconds;
            trauma = Math.max(0f, trauma - traumaDecay * seconds);
            float strength = trauma * trauma;
            float viewWidth = logicalWidth / unitScale();
            shakeX = strength * SHAKE_OFFSET * viewWidth * wobble(shakeTime, 1.0f);
            shakeY = strength * SHAKE_OFFSET * viewWidth * wobble(shakeTime, 2.3f);
            shakeRotation = strength * SHAKE_DEGREES * wobble(shakeTime, 3.7f);
        } else {
            shakeX = 0f;
            shakeY = 0f;
            shakeRotation = 0f;
        }
    }

    /** Smooth pseudo-random motion in {@code -1..1}. */
    private static float wobble(float time, float seed) {
        return (float) (Math.sin(time * 37.0 + seed * 11.0) * 0.6 + Math.sin(time * 23.0 + seed * 5.0) * 0.4);
    }

    private void moveTo(float newX, float newY) {
        x = newX;
        y = newY;
        position = new Vec2(newX, newY);
    }

    private void applyLimits() {
        Rect area = limits;
        if (area == null) {
            return;
        }
        float halfWidth = logicalWidth / 2f / unitScale();
        float halfHeight = logicalHeight / 2f / unitScale();
        float limitedX = area.width() < halfWidth * 2
                ? area.x() + area.width() / 2f
                : Math.max(area.x() + halfWidth, Math.min(area.x() + area.width() - halfWidth, x));
        float limitedY = area.height() < halfHeight * 2
                ? area.y() + area.height() / 2f
                : Math.max(area.y() + halfHeight, Math.min(area.y() + area.height() - halfHeight, y));
        if (limitedX != x || limitedY != y) {
            moveTo(limitedX, limitedY);
        }
    }

    @Override
    public Vec2 position() {
        return position;
    }

    @Override
    public void setPosition(Vec2 value) {
        panning = false;
        moveTo(value.x(), value.y());
    }

    @Override
    public float zoom() {
        return zoom;
    }

    @Override
    public void setZoom(float value) {
        if (!(value > 0f) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("Zoom must be positive and finite: " + value);
        }
        zooming = false;
        this.zoom = value;
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
                .rotate(-(rotation + shakeRotation))
                .translate(-(x + shakeX) * parallaxX, -(y + shakeY) * parallaxY);
    }

    /**
     * Writes the transform from world units to logical screen coordinates of this camera's area into {@code out}.
     *
     * @param out the target
     * @return {@code out}
     */
    public Affine2 worldToScreen(Affine2 out) {
        return out.identity()
                .translate(logicalWidth / 2f, logicalHeight / 2f)
                .scale(unitScale(), unitScale())
                .rotate(-(rotation + shakeRotation))
                .translate(-(x + shakeX), -(y + shakeY));
    }

    /**
     * Writes the visible world area into {@code out} as min x, min y, max x, max y, without allocating.
     *
     * @param out four floats
     */
    public void viewBounds(float[] out) {
        Affine2 inverse = worldToScreen(scratch).invert();
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
        out[0] = minX;
        out[1] = minY;
        out[2] = maxX;
        out[3] = maxY;
    }

    /**
     * Converts a world point to this camera's logical screen area without allocating; call {@link #prepareScreen()}
     * once first.
     *
     * @param worldX world x
     * @param worldY world y
     * @return screen x
     */
    public float screenX(float worldX, float worldY) {
        return screenTransform.transformX(worldX, worldY);
    }

    /**
     * Converts a world point to this camera's logical screen area without allocating.
     *
     * @param worldX world x
     * @param worldY world y
     * @return screen y
     */
    public float screenY(float worldX, float worldY) {
        return screenTransform.transformY(worldX, worldY);
    }

    /** Caches the world-to-screen transform for {@link #screenX} and {@link #screenY}. */
    public void prepareScreen() {
        worldToScreen(screenTransform);
    }

    /**
     * Returns the logical width of the camera's area.
     *
     * @return logical points
     */
    public float logicalWidth() {
        return logicalWidth;
    }

    /**
     * Returns the logical height of the camera's area.
     *
     * @return logical points
     */
    public float logicalHeight() {
        return logicalHeight;
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

    @Override
    public Camera follow(@Nullable Entity entity) {
        this.target = entity;
        if (entity != null) {
            moveTo(entity.x() + offset.x(), entity.y() + offset.y());
        }
        return this;
    }

    @Override
    public @Nullable Entity target() {
        Entity current = target;
        if (current != null && current.isRemoved()) {
            target = null;
            return null;
        }
        return current;
    }

    @Override
    public Camera smoothing(float seconds) {
        this.smoothing = Math.max(0f, seconds);
        return this;
    }

    @Override
    public Camera deadZone(float width, float height) {
        this.deadZoneWidth = Math.max(0f, width);
        this.deadZoneHeight = Math.max(0f, height);
        return this;
    }

    @Override
    public Camera lookAhead(float seconds) {
        this.lookAhead = seconds;
        return this;
    }

    @Override
    public Camera limits(@Nullable Rect area) {
        this.limits = area;
        applyLimits();
        return this;
    }

    @Override
    public Camera offset(Vec2 value) {
        this.offset = value;
        return this;
    }

    @Override
    public void shake(float amount, float seconds) {
        trauma = Math.min(1f, trauma + Math.max(0f, amount));
        traumaDecay = seconds > 0f ? 1f / seconds : Float.POSITIVE_INFINITY;
    }

    /**
     * Returns the current trauma.
     *
     * @return {@code 0..1}
     */
    public float trauma() {
        return trauma;
    }

    @Override
    public void zoomTo(float value, float seconds, Ease ease) {
        if (!(value > 0f)) {
            throw new IllegalArgumentException("Zoom must be positive: " + value);
        }
        if (seconds <= 0f) {
            setZoom(value);
            return;
        }
        zooming = true;
        zoomFrom = zoom;
        zoomTarget = value;
        zoomTime = 0f;
        zoomDuration = seconds;
        zoomEase = ease;
    }

    @Override
    public void panTo(Vec2 value, float seconds, Ease ease) {
        if (seconds <= 0f) {
            setPosition(value);
            return;
        }
        panning = true;
        panFromX = x;
        panFromY = y;
        panToX = value.x();
        panToY = value.y();
        panTime = 0f;
        panDuration = seconds;
        panEase = ease;
    }

    /**
     * Returns whether a pan animation runs.
     *
     * @return {@code true} while panning
     */
    public boolean isPanning() {
        return panning;
    }

    @Override
    public Rect viewport() {
        return viewport;
    }

    @Override
    public void setViewport(Rect area) {
        if (area.width() <= 0f || area.height() <= 0f) {
            throw new IllegalArgumentException("Viewport must have a positive size: " + area);
        }
        this.viewport = area;
    }
}
