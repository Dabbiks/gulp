package dev.gulp.api.anim;

/**
 * Code driven by the eased time of a tween, for effects no property describes.
 *
 * <pre>{@code
 * Tweens.custom(ring, 0.5f, t -> ring.setRadius(1f + t * 3f)).ease(Ease.OUT_CUBIC).start();
 * }</pre>
 */
@FunctionalInterface
public interface TweenFunction {

    /**
     * Shows a point of the tween.
     *
     * @param t eased time, usually {@code 0..1}
     */
    void apply(float t);
}
