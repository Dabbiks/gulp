package dev.gulp.api.world;

import dev.gulp.api.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * The contents of a chunk being generated. Coordinates are local ({@code 0..Chunk.SIZE-1}); the chunk starts at tile
 * {@link #originX()}, {@link #originY()}. Generation runs outside the tick, so it must not touch the world; entities
 * asked for with {@link #spawn} appear when the chunk is added to the world.
 *
 * <pre>{@code
 * data.setTile("ground", x, y, height > 0.2f ? GRASS : WATER);
 * if (rng.chance(0.01f)) data.spawn(TREE, x + 0.5f, y + 0.5f);
 * }</pre>
 */
public interface ChunkData {

    /**
     * Returns the first tile column of the chunk.
     *
     * @return tile x
     */
    int originX();

    /**
     * Returns the first tile row of the chunk.
     *
     * @return tile y
     */
    int originY();

    /**
     * Places a tile; the layer is created if needed.
     *
     * @param layer the layer name
     * @param x local column
     * @param y local row
     * @param type the tile type, or {@code null} to leave the cell empty
     */
    void setTile(String layer, int x, int y, @Nullable TileType type);

    /**
     * Returns a tile placed earlier in this chunk.
     *
     * @param layer the layer name
     * @param x local column
     * @param y local row
     * @return the tile type, or {@code null}
     */
    @Nullable TileType tile(String layer, int x, int y);

    /**
     * Asks for an entity to be spawned with the chunk; it is removed when the chunk unloads.
     *
     * @param type the entity type
     * @param x local x in tiles, may be fractional
     * @param y local y in tiles
     */
    void spawn(EntityType type, float x, float y);
}
