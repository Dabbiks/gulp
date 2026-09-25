package dev.gulp.api.render;

/**
 * One step of a post-processing chain ({@link PostEffects}): it takes the rendered picture and draws it changed. The
 * parameters are mutable and each effect offers properties for tweens, such as {@link Vignette#INTENSITY}.
 *
 * <pre>{@code
 * Vignette vignette = world.postEffects().add(new Vignette().intensity(0.4f));
 * Tweens.to(vignette, Vignette.INTENSITY, 0.9f, 0.5f).start();
 * }</pre>
 */
public abstract sealed class PostEffect
        permits Bloom, Vignette, ColorGrade, Blur, Pixelate, ChromaticAberration, Crt, CustomEffect {

    private boolean enabled = true;

    PostEffect() {}

    /**
     * Returns whether the effect is applied.
     *
     * @return {@code true} unless disabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Turns the effect on or off without removing it from the chain.
     *
     * @param value whether to apply it
     */
    public void setEnabled(boolean value) {
        enabled = value;
    }
}
