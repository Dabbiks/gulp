package dev.gulp.api.anim;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.math.Vec2;

/**
 * Interpolators for common value types.
 *
 * <pre>{@code
 * Property<Enemy, Float> speed = Property.of(Enemy::speed, Enemy::setSpeed, Interpolators.FLOAT);
 * }</pre>
 */
public final class Interpolators {

    /** Linear for floats. */
    public static final Interpolator<Float> FLOAT = new Interpolator<>() {
        @Override
        public Float interpolate(Float from, Float to, float t) {
            return from + (to - from) * t;
        }

        @Override
        public Float add(Float value, Float delta) {
            return value + delta;
        }
    };

    /** Linear for integers, rounded. */
    public static final Interpolator<Integer> INT = new Interpolator<>() {
        @Override
        public Integer interpolate(Integer from, Integer to, float t) {
            return Math.round(from + (to - from) * t);
        }

        @Override
        public Integer add(Integer value, Integer delta) {
            return value + delta;
        }
    };

    /** Linear for vectors. */
    public static final Interpolator<Vec2> VEC2 = new Interpolator<>() {
        @Override
        public Vec2 interpolate(Vec2 from, Vec2 to, float t) {
            return new Vec2(from.x() + (to.x() - from.x()) * t, from.y() + (to.y() - from.y()) * t);
        }

        @Override
        public Vec2 add(Vec2 value, Vec2 delta) {
            return value.add(delta);
        }
    };

    /** Per channel for colours, alpha included. */
    public static final Interpolator<Color> COLOR = new Interpolator<>() {
        @Override
        public Color interpolate(Color from, Color to, float t) {
            return from.lerp(to, t);
        }

        @Override
        public Color add(Color value, Color delta) {
            return new Color(
                    clamp(value.r() + delta.r()),
                    clamp(value.g() + delta.g()),
                    clamp(value.b() + delta.b()),
                    clamp(value.a() + delta.a()));
        }
    };

    /** Angles in degrees, the short way round. */
    public static final Interpolator<Float> ANGLE = new Interpolator<>() {
        @Override
        public Float interpolate(Float from, Float to, float t) {
            float difference = ((to - from) % 360f + 540f) % 360f - 180f;
            return from + difference * t;
        }

        @Override
        public Float add(Float value, Float delta) {
            return value + delta;
        }
    };

    private Interpolators() {}

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
