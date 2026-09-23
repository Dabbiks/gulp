package dev.gulp.api.math;

/**
 * A parametric curve with arc-length sampling, so objects can move along it at constant speed.
 *
 * <pre>{@code
 * Curve path = CatmullRom.through(List.of(a, b, c, d));
 * Vec2 p = path.at(0.5f);                       // by parameter
 * Vec2 q = path.atDistance(speed * elapsed);    // by distance along the curve
 * }</pre>
 */
public abstract class Curve {

    private static final int SAMPLES = 256;
    private float[] cumulative;

    /** Creates a curve. */
    protected Curve() {}

    /**
     * Point at a parameter.
     *
     * @param t the parameter, {@code 0..1} over the whole curve
     * @return the point
     */
    public abstract Vec2 at(float t);

    /**
     * Total length, approximated with {@value #SAMPLES} segments.
     *
     * @return the length
     */
    public final float length() {
        float[] table = table();
        return table[SAMPLES];
    }

    /**
     * Point at a distance along the curve from its start.
     *
     * @param distance the distance, clamped to {@code 0..length()}
     * @return the point
     */
    public final Vec2 atDistance(float distance) {
        return at(parameterAtDistance(distance));
    }

    /**
     * Point at a fraction of the curve length.
     *
     * @param fraction {@code 0..1} of the length
     * @return the point
     */
    public final Vec2 atFraction(float fraction) {
        return atDistance(fraction * length());
    }

    /**
     * Parameter at a distance along the curve.
     *
     * @param distance the distance
     * @return the parameter, {@code 0..1}
     */
    public final float parameterAtDistance(float distance) {
        float[] table = table();
        float d = Mathf.clamp(distance, 0f, table[SAMPLES]);
        int lo = 0;
        int hi = SAMPLES;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (table[mid] < d) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        float span = table[hi] - table[lo];
        float local = span == 0f ? 0f : (d - table[lo]) / span;
        return (lo + local) / SAMPLES;
    }

    private float[] table() {
        if (cumulative == null) {
            float[] table = new float[SAMPLES + 1];
            Vec2 previous = at(0f);
            for (int i = 1; i <= SAMPLES; i++) {
                Vec2 point = at(i / (float) SAMPLES);
                table[i] = table[i - 1] + previous.distanceTo(point);
                previous = point;
            }
            cumulative = table;
        }
        return cumulative;
    }
}
