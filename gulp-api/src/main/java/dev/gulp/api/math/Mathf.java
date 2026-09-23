package dev.gulp.api.math;

/**
 * Float math helpers for games. Angles are in degrees unless the name says otherwise.
 *
 * <pre>{@code
 * float hp = Mathf.clamp(hp - damage, 0, maxHp);
 * float t = Mathf.inverseLerp(minSpeed, maxSpeed, speed);
 * cameraX = Mathf.damp(cameraX, targetX, 8f, dt);     // frame-rate independent smoothing
 * }</pre>
 */
public final class Mathf {

    /** Pi. */
    public static final float PI = (float) Math.PI;
    /** Two pi. */
    public static final float TAU = (float) (Math.PI * 2);
    /** Default tolerance of {@link #isZero(float)} and {@link #nearlyEqual(float, float)}. */
    public static final float EPSILON = 1e-6f;

    private static final float DEG_TO_RAD = (float) (Math.PI / 180);
    private static final float RAD_TO_DEG = (float) (180 / Math.PI);

    private Mathf() {}

    /**
     * Limits a value to a range.
     *
     * @param value the value
     * @param min lower bound
     * @param max upper bound
     * @return the clamped value
     */
    public static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Limits a value to a range.
     *
     * @param value the value
     * @param min lower bound
     * @param max upper bound
     * @return the clamped value
     */
    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /**
     * Linear interpolation.
     *
     * @param from value at {@code t = 0}
     * @param to value at {@code t = 1}
     * @param t the factor, not clamped
     * @return the interpolated value
     */
    public static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /**
     * The factor that {@link #lerp} would need to produce a value.
     *
     * @param from value at {@code t = 0}
     * @param to value at {@code t = 1}
     * @param value the value
     * @return the factor, {@code 0} if {@code from == to}
     */
    public static float inverseLerp(float from, float to, float value) {
        return from == to ? 0f : (value - from) / (to - from);
    }

    /**
     * Maps a value from one range to another.
     *
     * @param value the value
     * @param fromMin start of the source range
     * @param fromMax end of the source range
     * @param toMin start of the target range
     * @param toMax end of the target range
     * @return the mapped value, not clamped
     */
    public static float remap(float value, float fromMin, float fromMax, float toMin, float toMax) {
        return lerp(toMin, toMax, inverseLerp(fromMin, fromMax, value));
    }

    /**
     * Smooth Hermite step between two edges.
     *
     * @param edge0 where the result is 0
     * @param edge1 where the result is 1
     * @param value the value
     * @return {@code 0..1}
     */
    public static float smoothStep(float edge0, float edge1, float value) {
        float t = clamp(inverseLerp(edge0, edge1, value), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * Moves a value towards a target by at most a step.
     *
     * @param current the value
     * @param target the target
     * @param maxDelta the largest change, non-negative
     * @return the new value, never overshooting
     */
    public static float moveToward(float current, float target, float maxDelta) {
        if (Math.abs(target - current) <= maxDelta) {
            return target;
        }
        return current + Math.signum(target - current) * maxDelta;
    }

    /**
     * Moves a value towards a target by a fraction of the remaining distance.
     *
     * @param current the value
     * @param target the target
     * @param fraction how much of the distance to cover, {@code 0..1}
     * @return the new value
     */
    public static float approach(float current, float target, float fraction) {
        return current + (target - current) * clamp(fraction, 0f, 1f);
    }

    /**
     * Wraps a value into {@code [min, max)}.
     *
     * @param value the value
     * @param min start of the range
     * @param max end of the range, exclusive
     * @return the wrapped value
     */
    public static float wrap(float value, float min, float max) {
        float range = max - min;
        if (range <= 0f) {
            return min;
        }
        float result = (value - min) % range;
        return (result < 0f ? result + range : result) + min;
    }

    /**
     * Wraps an integer into {@code [min, max)}.
     *
     * @param value the value
     * @param min start of the range
     * @param max end of the range, exclusive
     * @return the wrapped value
     */
    public static int wrap(int value, int min, int max) {
        int range = max - min;
        if (range <= 0) {
            return min;
        }
        return Math.floorMod(value - min, range) + min;
    }

    /**
     * Bounces a value back and forth between 0 and a length.
     *
     * @param value the value, for example elapsed time
     * @param length the length, positive
     * @return {@code 0..length}
     */
    public static float pingPong(float value, float length) {
        float t = wrap(value, 0f, length * 2f);
        return length - Math.abs(t - length);
    }

    /**
     * Rounds to the nearest multiple of a step.
     *
     * @param value the value
     * @param step the step; {@code 0} returns the value unchanged
     * @return the snapped value
     */
    public static float snapped(float value, float step) {
        return step == 0f ? value : Math.round(value / step) * step;
    }

    /**
     * Returns the sign.
     *
     * @param value the value
     * @return {@code -1}, {@code 0} or {@code 1}
     */
    public static float sign(float value) {
        return Math.signum(value);
    }

    /**
     * Returns whether a value is within {@link #EPSILON} of zero.
     *
     * @param value the value
     * @return {@code true} if nearly zero
     */
    public static boolean isZero(float value) {
        return Math.abs(value) <= EPSILON;
    }

    /**
     * Compares with a relative and absolute tolerance.
     *
     * @param a first value
     * @param b second value
     * @return {@code true} if nearly equal
     */
    public static boolean nearlyEqual(float a, float b) {
        return nearlyEqual(a, b, EPSILON);
    }

    /**
     * Compares with a tolerance, relative for large values.
     *
     * @param a first value
     * @param b second value
     * @param epsilon the tolerance
     * @return {@code true} if nearly equal
     */
    public static boolean nearlyEqual(float a, float b, float epsilon) {
        float difference = Math.abs(a - b);
        return difference <= epsilon || difference <= epsilon * Math.max(Math.abs(a), Math.abs(b));
    }

    /**
     * Signed smallest difference between two angles.
     *
     * @param from the start angle in degrees
     * @param to the end angle in degrees
     * @return {@code -180..180}
     */
    public static float angleDifference(float from, float to) {
        float difference = wrap(to - from, -180f, 180f);
        return difference == -180f ? 180f : difference;
    }

    /**
     * Interpolates angles along the shortest way.
     *
     * @param from the start angle in degrees
     * @param to the end angle in degrees
     * @param t the factor
     * @return the interpolated angle
     */
    public static float lerpAngle(float from, float to, float t) {
        return from + angleDifference(from, to) * t;
    }

    /**
     * Converts degrees to radians.
     *
     * @param degrees the angle
     * @return radians
     */
    public static float degToRad(float degrees) {
        return degrees * DEG_TO_RAD;
    }

    /**
     * Converts radians to degrees.
     *
     * @param radians the angle
     * @return degrees
     */
    public static float radToDeg(float radians) {
        return radians * RAD_TO_DEG;
    }

    /**
     * Sine of an angle in degrees.
     *
     * @param degrees the angle
     * @return the sine
     */
    public static float sinDeg(float degrees) {
        return (float) Math.sin(degrees * DEG_TO_RAD);
    }

    /**
     * Cosine of an angle in degrees.
     *
     * @param degrees the angle
     * @return the cosine
     */
    public static float cosDeg(float degrees) {
        return (float) Math.cos(degrees * DEG_TO_RAD);
    }

    /**
     * Exponential smoothing that behaves the same at any frame rate.
     *
     * @param current the value
     * @param target the target
     * @param lambda how fast to converge, per second; higher is faster
     * @param dt elapsed seconds
     * @return the new value
     */
    public static float damp(float current, float target, float lambda, float dt) {
        return lerp(current, target, 1f - (float) Math.exp(-lambda * dt));
    }
}
