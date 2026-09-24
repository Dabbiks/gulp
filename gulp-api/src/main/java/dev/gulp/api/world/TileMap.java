package dev.gulp.api.world;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.GridPos;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The tiles of a world, in layers, stored in chunks of {@link Chunk#SIZE} × {@link Chunk#SIZE}. Tile {@code (0, 0)}
 * covers world units {@code 0..1} on both axes in the orthogonal layout.
 *
 * <pre>{@code
 * TileMap map = world.tileMap();
 * map.setTile("ground", 3, 5, STONE);
 * GridPos cell = map.worldToTile(input().mouseWorld(world.camera()));
 * map.paintTerrain("ground", 0, 0, 10, 4, WATER_TERRAIN);
 * }</pre>
 */
public interface TileMap {

    /**
     * Returns the layout.
     *
     * @return the orientation
     */
    TileOrientation orientation();

    /**
     * Returns the width of tile graphics.
     *
     * @return pixels
     */
    int tileWidth();

    /**
     * Returns the height of tile graphics; with {@link #tileWidth()} it gives the height of a tile in world units.
     *
     * @return pixels
     */
    int tileHeight();

    /**
     * Returns the layers in z-order.
     *
     * @return the layers
     */
    List<TileLayer> layers();

    /**
     * Returns a layer.
     *
     * @param name the name
     * @return the layer
     * @throws IllegalArgumentException if there is no such layer
     */
    TileLayer layer(String name);

    /**
     * Returns a layer if present.
     *
     * @param name the name
     * @return the layer, or {@code null}
     */
    @Nullable TileLayer findLayer(String name);

    /**
     * Adds a layer, or returns the existing one.
     *
     * @param name the name
     * @param zOrder draw order among tile layers
     * @return the layer
     */
    TileLayer addLayer(String name, int zOrder);

    /**
     * Returns a tile.
     *
     * @param layer the layer name
     * @param x column
     * @param y row
     * @return the tile type, or {@code null}
     */
    @Nullable TileType tile(String layer, int x, int y);

    /**
     * Places a tile, creating the layer if needed; fires {@link TileChangeEvent}.
     *
     * @param layer the layer name
     * @param x column
     * @param y row
     * @param type the tile type, or {@code null} to clear
     * @return {@code false} if the change was cancelled
     */
    boolean setTile(String layer, int x, int y, @Nullable TileType type);

    /**
     * Fills a rectangle of tiles.
     *
     * @param layer the layer name
     * @param x first column
     * @param y first row
     * @param width columns
     * @param height rows
     * @param type the tile type, or {@code null} to clear
     */
    void fill(String layer, int x, int y, int width, int height, @Nullable TileType type);

    /**
     * Removes every tile of a layer.
     *
     * @param layer the layer name
     */
    void clear(String layer);

    /**
     * Returns the cell under a world point.
     *
     * @param point world units
     * @return the tile coordinates
     */
    GridPos worldToTile(Vec2 point);

    /**
     * Returns the centre of a tile.
     *
     * @param x column
     * @param y row
     * @return world units
     */
    Vec2 tileToWorld(int x, int y);

    /**
     * Returns the area covered by tiles, in tile coordinates.
     *
     * @return the bounds, or an empty rectangle for an empty map
     */
    Rect bounds();

    /**
     * Returns the state of a tile, creating it if needed.
     *
     * @param layer the layer name
     * @param x column
     * @param y row
     * @return the state
     */
    TileState state(String layer, int x, int y);

    /**
     * Returns the state of a tile if it has one.
     *
     * @param layer the layer name
     * @param x column
     * @param y row
     * @return the state, or {@code null}
     */
    @Nullable TileState findState(String layer, int x, int y);

    /**
     * Runs the interaction handler of a tile.
     *
     * @param layer the layer name
     * @param x column
     * @param y row
     * @param who the entity interacting, or {@code null}
     * @return {@code true} if the tile had a handler
     */
    boolean interact(String layer, int x, int y, @Nullable Entity who);

    /**
     * Places a terrain tile and updates it and its neighbours to matching edge and corner pieces.
     *
     * @param layer the layer name
     * @param x column
     * @param y row
     * @param terrain the terrain, or {@code null} to clear the cell
     */
    void setTerrain(String layer, int x, int y, @Nullable Terrain terrain);

    /**
     * Fills a rectangle with a terrain and fixes the edges around it.
     *
     * @param layer the layer name
     * @param x first column
     * @param y first row
     * @param width columns
     * @param height rows
     * @param terrain the terrain
     */
    void paintTerrain(String layer, int x, int y, int width, int height, Terrain terrain);
}
