package dev.gulp.api.math;

import java.util.List;

/**
 * Uniform cubic B-spline: a smooth curve guided by control points (it does not pass through them). The end points
 * are repeated so the curve starts and ends at the first and last control points.
 *
 * <pre>{@code
 * Curve smooth = BSpline.of(List.of(a, b, c, d, e));
 * }</pre>
 */
public final class BSpline extends Curve {

    private final List<Vec2> points;

    private BSpline(List<Vec2> points) {
        this.points = points;
    }

    /**
     * Creates the spline.
     *
     * @param controlPoints at least two points
     * @return the curve
     * @throws IllegalArgumentException if fewer than two points are given
     */
    public static BSpline of(List<Vec2> controlPoints) {
        if (controlPoints.size() < 2) {
            throw new IllegalArgumentException("A spline needs at least two control points");
        }
        return new BSpline(List.copyOf(controlPoints));
    }

    private Vec2 point(int index) {
        // Clamped ends: the first and last points count three times.
        return points.get(Mathf.clamp(index - 2, 0, points.size() - 1));
    }

    @Override
    public Vec2 at(float t) {
        int segments = points.size() + 1;
        float scaled = Mathf.clamp(t, 0f, 1f) * segments;
        int i = Math.min((int) scaled, segments - 1);
        float u = scaled - i;
        Vec2 p0 = point(i);
        Vec2 p1 = point(i + 1);
        Vec2 p2 = point(i + 2);
        Vec2 p3 = point(i + 3);
        float u2 = u * u;
        float u3 = u2 * u;
        float b0 = (1 - u) * (1 - u) * (1 - u) / 6f;
        float b1 = (3 * u3 - 6 * u2 + 4) / 6f;
        float b2 = (-3 * u3 + 3 * u2 + 3 * u + 1) / 6f;
        float b3 = u3 / 6f;
        return new Vec2(
                b0 * p0.x() + b1 * p1.x() + b2 * p2.x() + b3 * p3.x(),
                b0 * p0.y() + b1 * p1.y() + b2 * p2.y() + b3 * p3.y());
    }
}
