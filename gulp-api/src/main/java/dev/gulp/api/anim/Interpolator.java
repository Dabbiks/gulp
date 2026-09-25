package dev.gulp.api.anim;

/**
 * Blends two values of a type, for tweens and timelines. {@link #add} makes relative tweens ({@code Tweens.by}) work.
 *
 * <pre>{@code
 * Interpolator<Float> linear = (a, b, t) -> a + (b - a) * t;
 * }</pre>
 *
 * @param <V> the value type
 */
@FunctionalInterface
public interface Interpolator<V> {

    /**
     * Returns the value part of the way between two values.
     *
     * @param from the start
     * @param to the end
     * @param t progress, usually {@code 0..1} but eases may overshoot
     * @return the blended value
     */
    V interpolate(V from, V to, float t);

    /**
     * Adds a change to a value.
     *
     * @param value the value
     * @param delta the change
     * @return the sum
     * @throws UnsupportedOperationException for types without addition
     */
    default V add(V value, V delta) {
        throw new UnsupportedOperationException("This interpolator cannot add values; use Tweens.to instead of by");
    }
}
