package dev.gulp.api.world;

import dev.gulp.api.data.DataContainer;

/**
 * Data of one tile, such as the contents of a chest. Kept only for tiles that need it and removed when the tile
 * changes.
 *
 * <pre>{@code
 * TileState chest = world.tileMap().state("objects", 4, 7);
 * chest.data().set(COINS, DataType.INT, 25);
 * }</pre>
 */
public interface TileState {

    /**
     * Returns the layer.
     *
     * @return the layer
     */
    TileLayer layer();

    /**
     * Returns the column.
     *
     * @return the tile x
     */
    int x();

    /**
     * Returns the row.
     *
     * @return the tile y
     */
    int y();

    /**
     * Returns the data.
     *
     * @return the data container
     */
    DataContainer data();
}
