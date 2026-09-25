package dev.gulp.api.render;

import dev.gulp.api.anim.Interpolators;
import dev.gulp.api.anim.Property;

/**
 * Makes bright parts glow: pixels brighter than a threshold are blurred and added back.
 *
 * <pre>{@code
 * Bloom bloom = world.postEffects().add(new Bloom().threshold(0.65f).intensity(1.2f));
 * }</pre>
 */
public final class Bloom extends PostEffect {

    /** The glow strength, for tweens. */
    public static final Property<Bloom, Float> INTENSITY =
            Property.of(Bloom::intensity, Bloom::intensity, Interpolators.FLOAT);

    /** The brightness threshold, for tweens. */
    public static final Property<Bloom, Float> THRESHOLD =
            Property.of(Bloom::threshold, Bloom::threshold, Interpolators.FLOAT);

    private float threshold = 0.7f;
    private float intensity = 1f;
    private float radius = 1f;

    /** Creates a bloom with default settings. */
    public Bloom() {}

    /**
     * Returns the brightness above which pixels glow.
     *
     * @return {@code 0..1}, {@code 0.7} by default
     */
    public float threshold() {
        return threshold;
    }

    /**
     * Sets the brightness above which pixels glow.
     *
     * @param value {@code 0..1}
     * @return this effect
     */
    public Bloom threshold(float value) {
        threshold = value;
        return this;
    }

    /**
     * Returns the glow strength.
     *
     * @return {@code 1} by default
     */
    public float intensity() {
        return intensity;
    }

    /**
     * Sets the glow strength.
     *
     * @param value the strength
     * @return this effect
     */
    public Bloom intensity(float value) {
        intensity = value;
        return this;
    }

    /**
     * Returns how wide the glow spreads.
     *
     * @return a scale, {@code 1} by default
     */
    public float radius() {
        return radius;
    }

    /**
     * Sets how wide the glow spreads.
     *
     * @param value a scale of the blur
     * @return this effect
     */
    public Bloom radius(float value) {
        radius = value;
        return this;
    }
}
