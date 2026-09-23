package dev.gulp.api.math;

import java.util.List;

/**
 * Uniform Catmull-Rom spline that passes through every point; the first and last points are used as end tangents.
 *
 * <pre>{@code
 * Curve patrol = CatmullRom.through(List.of(a, b, c, d));
 * enemy.moveTo(patrol.atDistance(walked));
 * }</pre>
 */
public final class CatmullRom extends Curve {

    private final List<Vec2> points;

    private CatmullRom(List<Vec2> points) {
        this.points = points;
    }

    /**
     * Creates the spline.
     *
     * @param points at least two points
     * @return the curve through all points
     * @throws IllegalArgumentException if fewer than two points are given
     */
    public static CatmullRom through(List<Vec2> points) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("A spline needs at least two points");
        }
        return new CatmullRom(List.copyOf(points));
    }

    @Override
    public Vec2 at(float t) {
        int segments = points.size() - 1;
        float scaled = Mathf.clamp(t, 0f, 1f) * segments;
        int i = Math.min((int) scaled, segments - 1);
        float u = scaled - i;
        Vec2 p0 = points.get(Math.max(i - 1, 0));
        Vec2 p1 = points.get(i);
        Vec2 p2 = points.get(i + 1);
        Vec2 p3 = points.get(Math.min(i + 2, segments));
        return new Vec2(blend(p0.x(), p1.x(), p2.x(), p3.x(), u), blend(p0.y(), p1.y(), p2.y(), p3.y(), u));
    }

    private static float blend(float p0, float p1, float p2, float p3, float u) {
        float u2 = u * u;
        float u3 = u2 * u;
        return 0.5f
                * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
    }
}
