package dev.gulp.api.render;

import dev.gulp.api.anim.Interpolators;
import dev.gulp.api.anim.Property;
import dev.gulp.api.graphics.Texture;
import org.jspecify.annotations.Nullable;

/**
 * Remaps colours through a lookup table (LUT): a 256 x 16 image of 16 slices, one per blue level, each 16 x 16 with red
 * across and green down. Start from an identity LUT, colour it in an image editor, and load it as a texture.
 *
 * <pre>{@code
 * world.postEffects().add(new ColorGrade(assets().get(GameAssets.Textures.LUT_SUNSET)).intensity(0.8f));
 * }</pre>
 */
public final class ColorGrade extends PostEffect {

    /** How much of the graded colour shows, for tweens. */
    public static final Property<ColorGrade, Float> INTENSITY =
            Property.of(ColorGrade::intensity, ColorGrade::intensity, Interpolators.FLOAT);

    private @Nullable Texture lut;
    private float intensity = 1f;

    /**
     * Creates a colour grade.
     *
     * @param lut the lookup table
     */
    public ColorGrade(Texture lut) {
        this.lut = lut;
    }

    /**
     * Returns the lookup table.
     *
     * @return the texture, or {@code null} after it was cleared
     */
    public @Nullable Texture lut() {
        return lut;
    }

    /**
     * Replaces the lookup table.
     *
     * @param value the texture, or {@code null} to show colours unchanged
     * @return this effect
     */
    public ColorGrade lut(@Nullable Texture value) {
        lut = value;
        return this;
    }

    /**
     * Returns how much of the graded colour shows.
     *
     * @return {@code 0..1}
     */
    public float intensity() {
        return intensity;
    }

    /**
     * Sets how much of the graded colour shows.
     *
     * @param value {@code 0..1}
     * @return this effect
     */
    public ColorGrade intensity(float value) {
        intensity = value;
        return this;
    }
}
