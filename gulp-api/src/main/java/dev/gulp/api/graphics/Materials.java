package dev.gulp.api.graphics;

/**
 * Ready-made materials with shaders built into the engine. Each reads its parameters from the effect colour of what is
 * drawn: {@code Draw.effect(color)}, or {@code SpriteComponent.setEffect(color)} for sprites, so every sprite can have
 * its own amount without its own material.
 *
 * <ul>
 *   <li>{@link #FLASH}: blends the sprite towards the effect colour by its alpha, for hit flashes.
 *   <li>{@link #OUTLINE}: draws an outline of the effect colour around opaque pixels; alpha {@code 1} is four texels.
 *   <li>{@link #DISSOLVE}: burns the sprite away as alpha goes from {@code 0} to {@code 1}, with edges of the colour.
 *   <li>{@link #GRAYSCALE}: removes colour by the effect alpha.
 *   <li>{@link #TINT}: replaces colour with the effect colour by its alpha, keeping shading and transparency.
 * </ul>
 *
 * <pre>{@code
 * slime.get(SpriteComponent.class).setMaterial(Materials.FLASH).setEffect(Color.WHITE.withAlpha(0.8f));
 * Tweens.flash(slime, Color.WHITE, 0.1f).start();
 * }</pre>
 */
public final class Materials {

    /** Hit flash towards the effect colour. */
    public static final Material FLASH = Material.builtIn("gulp:flash");

    /** Outline in the effect colour. */
    public static final Material OUTLINE = Material.builtIn("gulp:outline");

    /** Burning away with glowing edges. */
    public static final Material DISSOLVE = Material.builtIn("gulp:dissolve");

    /** Desaturation. */
    public static final Material GRAYSCALE = Material.builtIn("gulp:grayscale");

    /** Recolouring that keeps shading. */
    public static final Material TINT = Material.builtIn("gulp:tint");

    private Materials() {}
}
