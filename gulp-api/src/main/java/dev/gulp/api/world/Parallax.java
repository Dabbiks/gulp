package dev.gulp.api.world;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.graphics.Texture;
import java.util.List;

/**
 * Background images that scroll slower (or faster) than the world, drawn in the {@code background} render layer.
 *
 * <pre>{@code
 * world.parallax().layer(GameAssets.Textures.SKY, 0.1f).repeatX().autoScroll(-4, 0);
 * world.parallax().layer(GameAssets.Textures.HILLS, 0.5f).repeatX().offset(0, 6);
 * }</pre>
 */
public interface Parallax {

    /**
     * Adds a layer; the texture loads if needed. Layers are drawn in the order they were added.
     *
     * @param texture the image
     * @param factor how much it follows the camera: {@code 0} fixed, {@code 1} moves with the world
     * @return the layer
     */
    ParallaxLayer layer(AssetKey<Texture> texture, float factor);

    /**
     * Returns the layers.
     *
     * @return the layers, back to front
     */
    List<ParallaxLayer> layers();

    /** Removes every layer. */
    void clear();
}
