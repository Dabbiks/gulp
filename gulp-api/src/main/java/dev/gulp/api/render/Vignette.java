package dev.gulp.api.render;

import dev.gulp.api.anim.Interpolators;
import dev.gulp.api.anim.Property;
import dev.gulp.api.graphics.Color;

/**
 * Darkens (or colours) the edges of the picture.
 *
 * <pre>{@code
 * Vignette hurt = display().postEffects().add(new Vignette().color(Color.RED).intensity(0f));
 * Tweens.to(hurt, Vignette.INTENSITY, 0.8f, 0.1f).yoyo().repeat(1).start();
 * }</pre>
 */
public final class Vignette extends PostEffect {

    /** The darkening strength, for tweens. */
    public static final Property<Vignette, Float> INTENSITY =
            Property.of(Vignette::intensity, Vignette::intensity, Interpolators.FLOAT);

    /** The clear area, for tweens. */
    public static final Property<Vignette, Float> RADIUS =
            Property.of(Vignette::radius, Vignette::radius, Interpolators.FLOAT);

    private float intensity = 0.5f;
    private float radius = 0.75f;
    private float softness = 0.45f;
    private Color color = Color.BLACK;

    /** Creates a vignette with default settings. */
    public Vignette() {}

    /**
     * Returns the strength.
     *
     * @return {@code 0..1}
     */
    public float intensity() {
        return intensity;
    }

    /**
     * Sets the strength.
     *
     * @param value {@code 0..1}
     * @return this effect
     */
    public Vignette intensity(float value) {
        intensity = value;
        return this;
    }

    /**
     * Returns the radius of the untouched middle.
     *
     * @return a fraction of the half diagonal
     */
    public float radius() {
        return radius;
    }

    /**
     * Sets the radius of the untouched middle.
     *
     * @param value a fraction of the half diagonal
     * @return this effect
     */
    public Vignette radius(float value) {
        radius = value;
        return this;
    }

    /**
     * Returns the width of the fade.
     *
     * @return a fraction of the half diagonal
     */
    public float softness() {
        return softness;
    }

    /**
     * Sets the width of the fade.
     *
     * @param value a fraction of the half diagonal
     * @return this effect
     */
    public Vignette softness(float value) {
        softness = value;
        return this;
    }

    /**
     * Returns the colour of the edges.
     *
     * @return black by default
     */
    public Color color() {
        return color;
    }

    /**
     * Sets the colour of the edges.
     *
     * @param value the colour
     * @return this effect
     */
    public Vignette color(Color value) {
        color = value;
        return this;
    }
}
