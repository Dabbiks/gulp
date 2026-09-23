package dev.gulp.api.graphics;

/**
 * How drawn pixels combine with what is already on screen. Textures and colors are premultiplied by alpha inside the
 * engine, so {@link #NORMAL} and {@link #PREMULTIPLIED} behave the same; both names exist for readability.
 *
 * <pre>{@code
 * draw.material(Material.DEFAULT.withBlend(BlendMode.ADD));   // glowing particles
 * }</pre>
 */
public enum BlendMode {
    /** Standard alpha blending. */
    NORMAL,
    /** Adds colors: glow, fire, light. */
    ADD,
    /** Multiplies colors: shadows, tinting. */
    MULTIPLY,
    /** Inverse multiply: brightens. */
    SCREEN,
    /** Blending for sources that are already premultiplied. */
    PREMULTIPLIED,
    /** Overwrites the destination, including alpha. */
    REPLACE
}
