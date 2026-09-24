package dev.gulp.api.world;

import dev.gulp.api.graphics.Color;

/**
 * One parallax background image. Sizes and offsets are in world units; by default the image is drawn at its pixel
 * size divided by the world tile size, with its top-left corner at the origin.
 *
 * <pre>{@code
 * world.parallax().layer(GameAssets.Textures.CLOUDS, 0.2f).repeatX().autoScroll(-1.5f, 0).tint(Color.WHITE.withAlpha(0.8f));
 * }</pre>
 */
public interface ParallaxLayer {

    /**
     * Sets the follow factor on both axes.
     *
     * @param x horizontal factor
     * @param y vertical factor
     * @return this layer
     */
    ParallaxLayer factor(float x, float y);

    /**
     * Repeats the image horizontally to fill the view.
     *
     * @return this layer
     */
    ParallaxLayer repeatX();

    /**
     * Repeats the image vertically to fill the view.
     *
     * @return this layer
     */
    ParallaxLayer repeatY();

    /**
     * Moves the image on its own.
     *
     * @param unitsPerSecondX horizontal speed
     * @param unitsPerSecondY vertical speed
     * @return this layer
     */
    ParallaxLayer autoScroll(float unitsPerSecondX, float unitsPerSecondY);

    /**
     * Places the image.
     *
     * @param x world units
     * @param y world units
     * @return this layer
     */
    ParallaxLayer offset(float x, float y);

    /**
     * Scales the image.
     *
     * @param factor the scale
     * @return this layer
     */
    ParallaxLayer scale(float factor);

    /**
     * Tints the image.
     *
     * @param color the colour
     * @return this layer
     */
    ParallaxLayer tint(Color color);

    /**
     * Draws the image in another render layer, for example {@code foreground} for fog in front of the world.
     *
     * @param name the render layer name
     * @return this layer
     */
    ParallaxLayer renderLayer(String name);

    /** Removes the layer. */
    void remove();
}
