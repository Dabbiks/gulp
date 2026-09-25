package dev.gulp.api.render;

import dev.gulp.api.anim.Interpolators;
import dev.gulp.api.anim.Property;

/**
 * Blurs the picture, for example behind a pause menu.
 *
 * <pre>{@code
 * Blur blur = world.postEffects().add(new Blur().radius(0f));
 * Tweens.to(blur, Blur.RADIUS, 6f, 0.3f).realtime().start();
 * }</pre>
 */
public final class Blur extends PostEffect {

    /** The blur radius, for tweens. */
    public static final Property<Blur, Float> RADIUS = Property.of(Blur::radius, Blur::radius, Interpolators.FLOAT);

    private float radius = 4f;

    /** Creates a blur. */
    public Blur() {}

    /**
     * Returns the radius.
     *
     * @return screen pixels
     */
    public float radius() {
        return radius;
    }

    /**
     * Sets the radius.
     *
     * @param value screen pixels, {@code 0} for none
     * @return this effect
     */
    public Blur radius(float value) {
        radius = Math.max(0f, value);
        return this;
    }
}
