package dev.gulp.api.math;

/**
 * Capsule: all points within a radius of a segment, a common character collision shape.
 *
 * <pre>{@code
 * Capsule body = new Capsule(new Segment(feet.add(0, -0.4f), feet.add(0, -1.2f)), 0.4f);
 * }</pre>
 *
 * @param spine the central segment
 * @param radius the radius, not negative
 */
public record Capsule(Segment spine, float radius) {

    /**
     * Validates the radius.
     *
     * @throws IllegalArgumentException if the radius is negative
     */
    public Capsule {
        if (radius < 0f) {
            throw new IllegalArgumentException("Radius must not be negative: " + radius);
        }
    }

    /**
     * Returns whether a point is inside.
     *
     * @param point the point
     * @return {@code true} if contained
     */
    public boolean contains(Vec2 point) {
        return spine.distanceTo(point) <= radius;
    }

    /**
     * Bounding rectangle.
     *
     * @return the bounds
     */
    public Rect bounds() {
        return Rect.fromCorners(spine.a(), spine.b()).expand(radius);
    }
}
