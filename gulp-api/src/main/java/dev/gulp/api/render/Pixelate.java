package dev.gulp.api.render;

import dev.gulp.api.anim.Interpolators;
import dev.gulp.api.anim.Property;

/**
 * Shows the picture in large blocks.
 *
 * <pre>{@code
 * Pixelate retro = display().postEffects().add(new Pixelate().size(4f));
 * }</pre>
 */
public final class Pixelate extends PostEffect {

    /** The block size, for tweens. */
    public static final Property<Pixelate, Float> SIZE =
            Property.of(Pixelate::size, Pixelate::size, Interpolators.FLOAT);

    private float size = 4f;

    /** Creates the effect. */
    public Pixelate() {}

    /**
     * Returns the block size.
     *
     * @return screen pixels
     */
    public float size() {
        return size;
    }

    /**
     * Sets the block size.
     *
     * @param value screen pixels, {@code 1} for none
     * @return this effect
     */
    public Pixelate size(float value) {
        size = Math.max(1f, value);
        return this;
    }
}
