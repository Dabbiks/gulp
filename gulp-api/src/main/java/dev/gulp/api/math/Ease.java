package dev.gulp.api.math;

/**
 * Standard easing curves: {@link #LINEAR} and ten families (SINE, QUAD, CUBIC, QUART, QUINT, EXPO, CIRC, BACK,
 * ELASTIC, BOUNCE), each as IN, OUT, IN_OUT and OUT_IN.
 *
 * <pre>{@code
 * Tweens.to(coin, Props.Y, -1f, 0.25f).ease(Ease.OUT_QUAD);
 * Interpolation custom = Ease.custom(t -> t * t * t);
 * Interpolation css = Ease.cubicBezier(0.42f, 0f, 0.58f, 1f);
 * }</pre>
 */
public enum Ease implements Interpolation {
    /** No easing. */
    LINEAR(Family.LINEAR, Variant.IN),
    /** Sine, in. */
    IN_SINE(Family.SINE, Variant.IN),
    /** Sine, out. */
    OUT_SINE(Family.SINE, Variant.OUT),
    /** Sine, in-out. */
    IN_OUT_SINE(Family.SINE, Variant.IN_OUT),
    /** Sine, out-in. */
    OUT_IN_SINE(Family.SINE, Variant.OUT_IN),
    /** Quadratic, in. */
    IN_QUAD(Family.QUAD, Variant.IN),
    /** Quadratic, out. */
    OUT_QUAD(Family.QUAD, Variant.OUT),
    /** Quadratic, in-out. */
    IN_OUT_QUAD(Family.QUAD, Variant.IN_OUT),
    /** Quadratic, out-in. */
    OUT_IN_QUAD(Family.QUAD, Variant.OUT_IN),
    /** Cubic, in. */
    IN_CUBIC(Family.CUBIC, Variant.IN),
    /** Cubic, out. */
    OUT_CUBIC(Family.CUBIC, Variant.OUT),
    /** Cubic, in-out. */
    IN_OUT_CUBIC(Family.CUBIC, Variant.IN_OUT),
    /** Cubic, out-in. */
    OUT_IN_CUBIC(Family.CUBIC, Variant.OUT_IN),
    /** Quartic, in. */
    IN_QUART(Family.QUART, Variant.IN),
    /** Quartic, out. */
    OUT_QUART(Family.QUART, Variant.OUT),
    /** Quartic, in-out. */
    IN_OUT_QUART(Family.QUART, Variant.IN_OUT),
    /** Quartic, out-in. */
    OUT_IN_QUART(Family.QUART, Variant.OUT_IN),
    /** Quintic, in. */
    IN_QUINT(Family.QUINT, Variant.IN),
    /** Quintic, out. */
    OUT_QUINT(Family.QUINT, Variant.OUT),
    /** Quintic, in-out. */
    IN_OUT_QUINT(Family.QUINT, Variant.IN_OUT),
    /** Quintic, out-in. */
    OUT_IN_QUINT(Family.QUINT, Variant.OUT_IN),
    /** Exponential, in. */
    IN_EXPO(Family.EXPO, Variant.IN),
    /** Exponential, out. */
    OUT_EXPO(Family.EXPO, Variant.OUT),
    /** Exponential, in-out. */
    IN_OUT_EXPO(Family.EXPO, Variant.IN_OUT),
    /** Exponential, out-in. */
    OUT_IN_EXPO(Family.EXPO, Variant.OUT_IN),
    /** Circular, in. */
    IN_CIRC(Family.CIRC, Variant.IN),
    /** Circular, out. */
    OUT_CIRC(Family.CIRC, Variant.OUT),
    /** Circular, in-out. */
    IN_OUT_CIRC(Family.CIRC, Variant.IN_OUT),
    /** Circular, out-in. */
    OUT_IN_CIRC(Family.CIRC, Variant.OUT_IN),
    /** Back (overshoots), in. */
    IN_BACK(Family.BACK, Variant.IN),
    /** Back (overshoots), out. */
    OUT_BACK(Family.BACK, Variant.OUT),
    /** Back (overshoots), in-out. */
    IN_OUT_BACK(Family.BACK, Variant.IN_OUT),
    /** Back (overshoots), out-in. */
    OUT_IN_BACK(Family.BACK, Variant.OUT_IN),
    /** Elastic, in. */
    IN_ELASTIC(Family.ELASTIC, Variant.IN),
    /** Elastic, out. */
    OUT_ELASTIC(Family.ELASTIC, Variant.OUT),
    /** Elastic, in-out. */
    IN_OUT_ELASTIC(Family.ELASTIC, Variant.IN_OUT),
    /** Elastic, out-in. */
    OUT_IN_ELASTIC(Family.ELASTIC, Variant.OUT_IN),
    /** Bounce, in. */
    IN_BOUNCE(Family.BOUNCE, Variant.IN),
    /** Bounce, out. */
    OUT_BOUNCE(Family.BOUNCE, Variant.OUT),
    /** Bounce, in-out. */
    IN_OUT_BOUNCE(Family.BOUNCE, Variant.IN_OUT),
    /** Bounce, out-in. */
    OUT_IN_BOUNCE(Family.BOUNCE, Variant.OUT_IN);

    private enum Family {
        LINEAR,
        SINE,
        QUAD,
        CUBIC,
        QUART,
        QUINT,
        EXPO,
        CIRC,
        BACK,
        ELASTIC,
        BOUNCE
    }

    private enum Variant {
        IN,
        OUT,
        IN_OUT,
        OUT_IN
    }

    private final Family family;
    private final Variant variant;

    Ease(Family family, Variant variant) {
        this.family = family;
        this.variant = variant;
    }

    @Override
    public float apply(float t) {
        if (t <= 0f) {
            return 0f;
        }
        if (t >= 1f) {
            return 1f;
        }
        return switch (variant) {
            case IN -> in(family, t);
            case OUT -> 1f - in(family, 1f - t);
            case IN_OUT -> t < 0.5f ? in(family, t * 2f) / 2f : 1f - in(family, 2f - t * 2f) / 2f;
            case OUT_IN -> t < 0.5f ? (1f - in(family, 1f - t * 2f)) / 2f : 0.5f + in(family, t * 2f - 1f) / 2f;
        };
    }

    private static float in(Family family, float t) {
        return switch (family) {
            case LINEAR -> t;
            case SINE -> 1f - (float) Math.cos(t * Math.PI / 2);
            case QUAD -> t * t;
            case CUBIC -> t * t * t;
            case QUART -> t * t * t * t;
            case QUINT -> t * t * t * t * t;
            case EXPO -> t == 0f ? 0f : (float) Math.pow(2, 10 * t - 10);
            case CIRC -> 1f - (float) Math.sqrt(1 - t * t);
            case BACK -> {
                float c = 1.70158f;
                yield (c + 1f) * t * t * t - c * t * t;
            }
            case ELASTIC ->
                t == 0f || t == 1f
                        ? t
                        : -(float) (Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * (2 * Math.PI / 3)));
            case BOUNCE -> 1f - bounceOut(1f - t);
        };
    }

    private static float bounceOut(float t) {
        float n = 7.5625f;
        float d = 2.75f;
        if (t < 1f / d) {
            return n * t * t;
        } else if (t < 2f / d) {
            t -= 1.5f / d;
            return n * t * t + 0.75f;
        } else if (t < 2.5f / d) {
            t -= 2.25f / d;
            return n * t * t + 0.9375f;
        }
        t -= 2.625f / d;
        return n * t * t + 0.984375f;
    }

    /**
     * Wraps a function as an interpolation.
     *
     * @param function maps progress to a value
     * @return the interpolation
     */
    public static Interpolation custom(Interpolation function) {
        return function;
    }

    /**
     * CSS-style cubic Bezier timing curve through {@code (0,0)}, {@code (x1,y1)}, {@code (x2,y2)}, {@code (1,1)}.
     *
     * @param x1 first control X, {@code 0..1}
     * @param y1 first control Y
     * @param x2 second control X, {@code 0..1}
     * @param y2 second control Y
     * @return the interpolation
     * @throws IllegalArgumentException if an X is outside {@code 0..1}
     */
    public static Interpolation cubicBezier(float x1, float y1, float x2, float y2) {
        if (x1 < 0f || x1 > 1f || x2 < 0f || x2 > 1f) {
            throw new IllegalArgumentException("Control point X must be in 0..1");
        }
        return t -> {
            if (t <= 0f) {
                return 0f;
            }
            if (t >= 1f) {
                return 1f;
            }
            float lo = 0f;
            float hi = 1f;
            float u = t;
            for (int i = 0; i < 40; i++) {
                float x = bezier(u, x1, x2);
                if (Math.abs(x - t) < 1e-6f) {
                    break;
                }
                if (x < t) {
                    lo = u;
                } else {
                    hi = u;
                }
                u = (lo + hi) / 2f;
            }
            return bezier(u, y1, y2);
        };
    }

    private static float bezier(float u, float p1, float p2) {
        float v = 1f - u;
        return 3f * v * v * u * p1 + 3f * v * u * u * p2 + u * u * u;
    }
}
