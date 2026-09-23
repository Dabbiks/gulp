package dev.gulp.api.math;

/**
 * Axis-aligned rectangle; {@code (x, y)} is the top-left corner (Y points down).
 *
 * <pre>{@code
 * Rect screen = new Rect(0, 0, 640, 360);
 * if (screen.contains(mouse)) ...
 * Rect fitted = Rect.of(0, 0, 400, 300).fitInside(screen);   // largest same-aspect rect, centered
 * }</pre>
 *
 * @param x left edge
 * @param y top edge
 * @param width width, not negative
 * @param height height, not negative
 */
public record Rect(float x, float y, float width, float height) {

    /** The empty rectangle at the origin. */
    public static final Rect EMPTY = new Rect(0f, 0f, 0f, 0f);

    /**
     * Validates the size.
     *
     * @throws IllegalArgumentException if width or height is negative
     */
    public Rect {
        if (width < 0f || height < 0f) {
            throw new IllegalArgumentException("Rect size must not be negative: " + width + "x" + height);
        }
    }

    /**
     * Creates a rectangle.
     *
     * @param x left edge
     * @param y top edge
     * @param width width
     * @param height height
     * @return the rectangle
     */
    public static Rect of(float x, float y, float width, float height) {
        return new Rect(x, y, width, height);
    }

    /**
     * Creates a rectangle from two corners in any order.
     *
     * @param a one corner
     * @param b the opposite corner
     * @return the rectangle
     */
    public static Rect fromCorners(Vec2 a, Vec2 b) {
        float minX = Math.min(a.x(), b.x());
        float minY = Math.min(a.y(), b.y());
        return new Rect(minX, minY, Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y()));
    }

    /**
     * Creates a rectangle around a center.
     *
     * @param center the center
     * @param width width
     * @param height height
     * @return the rectangle
     */
    public static Rect centered(Vec2 center, float width, float height) {
        return new Rect(center.x() - width / 2f, center.y() - height / 2f, width, height);
    }

    /**
     * Right edge.
     *
     * @return {@code x + width}
     */
    public float right() {
        return x + width;
    }

    /**
     * Bottom edge.
     *
     * @return {@code y + height}
     */
    public float bottom() {
        return y + height;
    }

    /**
     * Center point.
     *
     * @return the center
     */
    public Vec2 center() {
        return new Vec2(x + width / 2f, y + height / 2f);
    }

    /**
     * Area.
     *
     * @return {@code width * height}
     */
    public float area() {
        return width * height;
    }

    /**
     * Returns whether a point is inside; the right and bottom edges are exclusive.
     *
     * @param px point X
     * @param py point Y
     * @return {@code true} if inside
     */
    public boolean contains(float px, float py) {
        return px >= x && py >= y && px < x + width && py < y + height;
    }

    /**
     * Returns whether a point is inside.
     *
     * @param point the point
     * @return {@code true} if inside
     */
    public boolean contains(Vec2 point) {
        return contains(point.x(), point.y());
    }

    /**
     * Returns whether another rectangle lies completely inside.
     *
     * @param other the rectangle
     * @return {@code true} if contained
     */
    public boolean contains(Rect other) {
        return other.x >= x && other.y >= y && other.right() <= right() && other.bottom() <= bottom();
    }

    /**
     * Returns whether the rectangles share area.
     *
     * @param other the rectangle
     * @return {@code true} if they overlap
     */
    public boolean overlaps(Rect other) {
        return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
    }

    /**
     * The shared area.
     *
     * @param other the rectangle
     * @return the intersection, or {@link #EMPTY} if they do not overlap
     */
    public Rect intersection(Rect other) {
        float left = Math.max(x, other.x);
        float top = Math.max(y, other.y);
        float right = Math.min(right(), other.right());
        float bottom = Math.min(bottom(), other.bottom());
        return right <= left || bottom <= top ? EMPTY : new Rect(left, top, right - left, bottom - top);
    }

    /**
     * The smallest rectangle containing both.
     *
     * @param other the rectangle
     * @return the union
     */
    public Rect union(Rect other) {
        float left = Math.min(x, other.x);
        float top = Math.min(y, other.y);
        return new Rect(left, top, Math.max(right(), other.right()) - left, Math.max(bottom(), other.bottom()) - top);
    }

    /**
     * Grows every side by an amount; negative shrinks (not below zero size).
     *
     * @param amount the margin
     * @return the new rectangle
     */
    public Rect expand(float amount) {
        float w = Math.max(0f, width + amount * 2f);
        float h = Math.max(0f, height + amount * 2f);
        return new Rect(x - (w - width) / 2f, y - (h - height) / 2f, w, h);
    }

    /**
     * Moves the rectangle.
     *
     * @param dx X offset
     * @param dy Y offset
     * @return the moved rectangle
     */
    public Rect translate(float dx, float dy) {
        return new Rect(x + dx, y + dy, width, height);
    }

    /**
     * Scales this rectangle to the largest size with the same aspect ratio that fits inside a container, centered.
     *
     * @param container the container
     * @return the fitted rectangle
     */
    public Rect fitInside(Rect container) {
        if (width == 0f || height == 0f) {
            return new Rect(container.center().x(), container.center().y(), 0f, 0f);
        }
        float scale = Math.min(container.width / width, container.height / height);
        return centered(container.center(), width * scale, height * scale);
    }

    /**
     * Scales this rectangle to the smallest size with the same aspect ratio that covers a container, centered.
     *
     * @param container the container
     * @return the fitted rectangle
     */
    public Rect fitOutside(Rect container) {
        if (width == 0f || height == 0f) {
            return new Rect(container.center().x(), container.center().y(), 0f, 0f);
        }
        float scale = Math.max(container.width / width, container.height / height);
        return centered(container.center(), width * scale, height * scale);
    }

    /**
     * Closest point inside the rectangle.
     *
     * @param point the point
     * @return the clamped point
     */
    public Vec2 clamp(Vec2 point) {
        return new Vec2(Mathf.clamp(point.x(), x, right()), Mathf.clamp(point.y(), y, bottom()));
    }
}
