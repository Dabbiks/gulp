package dev.gulp.api.math;

/**
 * Quadratic or cubic Bezier curve.
 *
 * <pre>{@code
 * Curve arc = Bezier.quadratic(start, control, end);
 * Curve s = Bezier.cubic(start, c1, c2, end);
 * }</pre>
 */
public final class Bezier extends Curve {

    private final Vec2 p0;
    private final Vec2 p1;
    private final Vec2 p2;
    private final Vec2 p3;
    private final boolean cubic;

    private Bezier(Vec2 p0, Vec2 p1, Vec2 p2, Vec2 p3, boolean cubic) {
        this.p0 = p0;
        this.p1 = p1;
        this.p2 = p2;
        this.p3 = p3;
        this.cubic = cubic;
    }

    /**
     * Quadratic curve.
     *
     * @param start start point
     * @param control control point
     * @param end end point
     * @return the curve
     */
    public static Bezier quadratic(Vec2 start, Vec2 control, Vec2 end) {
        return new Bezier(start, control, end, end, false);
    }

    /**
     * Cubic curve.
     *
     * @param start start point
     * @param control1 first control point
     * @param control2 second control point
     * @param end end point
     * @return the curve
     */
    public static Bezier cubic(Vec2 start, Vec2 control1, Vec2 control2, Vec2 end) {
        return new Bezier(start, control1, control2, end, true);
    }

    @Override
    public Vec2 at(float t) {
        float u = 1f - t;
        if (!cubic) {
            return new Vec2(
                    u * u * p0.x() + 2 * u * t * p1.x() + t * t * p2.x(),
                    u * u * p0.y() + 2 * u * t * p1.y() + t * t * p2.y());
        }
        float a = u * u * u;
        float b = 3 * u * u * t;
        float c = 3 * u * t * t;
        float d = t * t * t;
        return new Vec2(
                a * p0.x() + b * p1.x() + c * p2.x() + d * p3.x(), a * p0.y() + b * p1.y() + c * p2.y() + d * p3.y());
    }
}
