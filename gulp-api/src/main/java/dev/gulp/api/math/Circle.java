package dev.gulp.api.math;

/**
 * Circle.
 *
 * <pre>{@code
 * Circle blast = new Circle(explosion, 3f);
 * if (blast.contains(player.position())) ...
 * }</pre>
 *
 * @param center the center
 * @param radius the radius, not negative
 */
public record Circle(Vec2 center, float radius) {

    /**
     * Validates the radius.
     *
     * @throws IllegalArgumentException if the radius is negative
     */
    public Circle {
        if (radius < 0f) {
            throw new IllegalArgumentException("Radius must not be negative: " + radius);
        }
    }

    /**
     * Returns whether a point is inside or on the edge.
     *
     * @param point the point
     * @return {@code true} if contained
     */
    public boolean contains(Vec2 point) {
        return center.distanceSquaredTo(point) <= radius * radius;
    }

    /**
     * Returns whether two circles overlap.
     *
     * @param other the circle
     * @return {@code true} if they overlap
     */
    public boolean overlaps(Circle other) {
        float r = radius + other.radius;
        return center.distanceSquaredTo(other.center) < r * r;
    }

    /**
     * Area.
     *
     * @return {@code pi * r^2}
     */
    public float area() {
        return Mathf.PI * radius * radius;
    }

    /**
     * Bounding rectangle.
     *
     * @return the bounds
     */
    public Rect bounds() {
        return Rect.centered(center, radius * 2f, radius * 2f);
    }
}
