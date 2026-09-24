package dev.gulp.api.world;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.graphics.Texture;

/**
 * A texture cut into equal tiles, numbered left to right, top to bottom, from 0.
 *
 * <pre>{@code
 * TileSet terrain = TileSet.of(GameAssets.Textures.TERRAIN, 16, 16);
 * TileType grass = TileType.builder(key("grass")).tileSet(terrain, 3).build();
 * }</pre>
 *
 * @param texture the image
 * @param tileWidth tile width in pixels
 * @param tileHeight tile height in pixels
 * @param margin pixels around the whole image
 * @param spacing pixels between tiles
 */
public record TileSet(AssetKey<Texture> texture, int tileWidth, int tileHeight, int margin, int spacing) {

    /**
     * Validates the sizes.
     *
     * @param texture the image
     * @param tileWidth tile width
     * @param tileHeight tile height
     * @param margin margin
     * @param spacing spacing
     */
    public TileSet {
        if (tileWidth <= 0 || tileHeight <= 0 || margin < 0 || spacing < 0) {
            throw new IllegalArgumentException("Invalid tile set layout " + tileWidth + "x" + tileHeight + ", margin "
                    + margin + ", spacing " + spacing);
        }
    }

    /**
     * Returns a tile set without margin and spacing.
     *
     * @param texture the image
     * @param tileWidth tile width in pixels
     * @param tileHeight tile height in pixels
     * @return the tile set
     */
    public static TileSet of(AssetKey<Texture> texture, int tileWidth, int tileHeight) {
        return new TileSet(texture, tileWidth, tileHeight, 0, 0);
    }

    /**
     * Returns the pixel position of a tile.
     *
     * @param index the tile number
     * @param textureWidth width of the image in pixels
     * @return {@code {x, y}} of the top-left corner
     */
    public int[] origin(int index, int textureWidth) {
        int columns = Math.max(1, (textureWidth - 2 * margin + spacing) / (tileWidth + spacing));
        int column = index % columns;
        int row = index / columns;
        return new int[] {margin + column * (tileWidth + spacing), margin + row * (tileHeight + spacing)};
    }
}
