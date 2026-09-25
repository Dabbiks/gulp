package dev.gulp.api.render;

import dev.gulp.api.anim.Interpolators;
import dev.gulp.api.anim.Property;

/**
 * An old television look: a curved screen, scanlines and darker corners.
 *
 * <pre>{@code
 * display().postEffects().add(new Crt().curvature(0.08f).scanlines(0.35f));
 * }</pre>
 */
public final class Crt extends PostEffect {

    /** The screen curvature, for tweens. */
    public static final Property<Crt, Float> CURVATURE =
            Property.of(Crt::curvature, Crt::curvature, Interpolators.FLOAT);

    /** The scanline strength, for tweens. */
    public static final Property<Crt, Float> SCANLINES =
            Property.of(Crt::scanlines, Crt::scanlines, Interpolators.FLOAT);

    private float curvature = 0.1f;
    private float scanlines = 0.3f;

    /** Creates the effect. */
    public Crt() {}

    /**
     * Returns how curved the screen looks.
     *
     * @return {@code 0} flat, around {@code 0.1} typical
     */
    public float curvature() {
        return curvature;
    }

    /**
     * Sets how curved the screen looks.
     *
     * @param value the curvature
     * @return this effect
     */
    public Crt curvature(float value) {
        curvature = Math.max(0f, value);
        return this;
    }

    /**
     * Returns how dark the scanlines are.
     *
     * @return {@code 0..1}
     */
    public float scanlines() {
        return scanlines;
    }

    /**
     * Sets how dark the scanlines are.
     *
     * @param value {@code 0..1}
     * @return this effect
     */
    public Crt scanlines(float value) {
        scanlines = value;
        return this;
    }
}
