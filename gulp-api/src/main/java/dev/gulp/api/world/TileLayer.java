package dev.gulp.api.world;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.math.Vec2;
import org.jspecify.annotations.Nullable;

/**
 * One layer of a tile map: tiles on a grid, drawn in z-order with other layers.
 *
 * <pre>{@code
 * TileLayer ground = world.tileMap().layer("ground");
 * ground.set(3, 5, STONE);
 * TileLayer decoration = world.tileMap().addLayer("decoration", 10);
 * decoration.setParallax(new Vec2(1.2f, 1f));
 * }</pre>
 */
public interface TileLayer {

    /**
     * Returns the name.
     *
     * @return the name
     */
    String name();

    /**
     * Returns the map.
     *
     * @return the map
     */
    TileMap map();

    /**
     * Returns the draw order among tile layers: lower is drawn first.
     *
     * @return the z-order
     */
    int zOrder();

    /**
     * Returns whether the layer is drawn.
     *
     * @return {@code true} by default
     */
    boolean isVisible();

    /**
     * Shows or hides the layer.
     *
     * @param visible whether to draw
     */
    void setVisible(boolean visible);

    /**
     * Returns whether the shapes of this layer's tiles collide (used by physics and navigation, stage 7).
     *
     * @return {@code true} by default
     */
    boolean isCollision();

    /**
     * Chooses whether the layer's tiles collide.
     *
     * @param collision whether to collide
     */
    void setCollision(boolean collision);

    /**
     * Returns the parallax factor relative to the camera.
     *
     * @return {@code (1, 1)} by default
     */
    Vec2 parallax();

    /**
     * Changes the parallax factor.
     *
     * @param factor the factor
     */
    void setParallax(Vec2 factor);

    /**
     * Returns the tint.
     *
     * @return the tint, white by default
     */
    Color tint();

    /**
     * Changes the tint.
     *
     * @param tint the colour
     */
    void setTint(Color tint);

    /**
     * Returns the render layer the tiles are drawn in.
     *
     * @return the render layer name, {@code tiles} by default
     */
    String renderLayer();

    /**
     * Moves the tiles to another render layer, for example {@code foreground} for tiles in front of entities.
     *
     * @param name the render layer name
     */
    void setRenderLayer(String name);

    /**
     * Returns a tile.
     *
     * @param x column
     * @param y row
     * @return the tile type, or {@code null} for an empty cell or a chunk that is not loaded
     */
    @Nullable TileType get(int x, int y);

    /**
     * Places a tile; fires {@link TileChangeEvent}.
     *
     * @param x column
     * @param y row
     * @param type the tile type, or {@code null} to clear the cell
     * @return {@code false} if the change was cancelled
     */
    boolean set(int x, int y, @Nullable TileType type);
}
