package dev.gulp.api.math;

/**
 * Line segment between two points.
 *
 * <pre>{@code
 * Segment laser = new Segment(gun, gun.add(direction.scale(20)));
 * Vec2 hit = Intersect.segments(laser, wall);
 * }</pre>
 *
 * @param a start point
 * @param b end point
 */
public record Segment(Vec2 a, Vec2 b) {

    /**
     * Length.
     *
     * @return the length
     */
    public float length() {
        return a.distanceTo(b);
    }

    /**
     * Point at a fraction of the way from {@code a} to {@code b}.
     *
     * @param t the fraction
     * @return the point
     */
    public Vec2 pointAt(float t) {
        return a.lerp(b, t);
    }

    /**
     * Closest point of the segment to a point.
     *
     * @param point the point
     * @return the closest point
     */
    public Vec2 closestPoint(Vec2 point) {
        float dx = b.x() - a.x();
        float dy = b.y() - a.y();
        float lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0f) {
            return a;
        }
        float t = Mathf.clamp(((point.x() - a.x()) * dx + (point.y() - a.y()) * dy) / lengthSquared, 0f, 1f);
        return new Vec2(a.x() + dx * t, a.y() + dy * t);
    }

    /**
     * Distance from a point to the segment.
     *
     * @param point the point
     * @return the distance
     */
    public float distanceTo(Vec2 point) {
        return closestPoint(point).distanceTo(point);
    }
}
