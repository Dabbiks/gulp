package dev.gulp.api.render;

import dev.gulp.api.anim.Interpolators;
import dev.gulp.api.anim.Property;

/**
 * Splits the colour channels apart towards the edges, like a cheap lens; good for hits and explosions.
 *
 * <pre>{@code
 * ChromaticAberration hit = display().postEffects().add(new ChromaticAberration().amount(0f));
 * Tweens.to(hit, ChromaticAberration.AMOUNT, 4f, 0.08f).yoyo().repeat(1).start();
 * }</pre>
 */
public final class ChromaticAberration extends PostEffect {

    /** The channel offset, for tweens. */
    public static final Property<ChromaticAberration, Float> AMOUNT =
            Property.of(ChromaticAberration::amount, ChromaticAberration::amount, Interpolators.FLOAT);

    private float amount = 2f;

    /** Creates the effect. */
    public ChromaticAberration() {}

    /**
     * Returns the offset of the red and blue channels at the edges.
     *
     * @return screen pixels
     */
    public float amount() {
        return amount;
    }

    /**
     * Sets the offset of the red and blue channels at the edges.
     *
     * @param value screen pixels
     * @return this effect
     */
    public ChromaticAberration amount(float value) {
        amount = value;
        return this;
    }
}
