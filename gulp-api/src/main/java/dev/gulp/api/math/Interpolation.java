package dev.gulp.api.math;

/**
 * Maps progress {@code t} in {@code 0..1} to an eased value; {@code apply(0) = 0} and {@code apply(1) = 1} for all
 * built-in curves (some overshoot in between).
 *
 * <pre>{@code
 * Interpolation ease = Ease.OUT_QUAD;
 * float x = Mathf.lerp(startX, endX, ease.apply(t));
 * float y = Mathf.lerp(startY, endY, Ease.cubicBezier(0.25f, 0.1f, 0.25f, 1f).apply(t));
 * }</pre>
 */
@FunctionalInterface
public interface Interpolation {

    /**
     * Eases progress.
     *
     * @param t progress, {@code 0..1}
     * @return the eased value
     */
    float apply(float t);

    /**
     * Interpolates between two values.
     *
     * @param from value at progress 0
     * @param to value at progress 1
     * @param t progress, {@code 0..1}
     * @return the eased value between {@code from} and {@code to}
     */
    default float apply(float from, float to, float t) {
        return from + (to - from) * apply(t);
    }
}
