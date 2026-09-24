package dev.gulp.core.physics;

import dev.gulp.api.event.Events;
import dev.gulp.api.world.TileType;

/** What the physics of a world needs from it: collision tiles, events and the tick length. */
public interface PhysicsHost {

    /** Receives one collision tile. */
    @FunctionalInterface
    interface TileVisitor {
        /**
         * Visits a tile.
         *
         * @param x column
         * @param y row
         * @param type the tile type, with a solid shape
         * @param flags Tiled-style flip flags in the top three bits
         */
        void visit(int x, int y, TileType type, int flags);
    }

    /**
     * Visits the solid tiles of collision layers in a cell range, inclusive.
     *
     * @param minX first column
     * @param minY first row
     * @param maxX last column
     * @param maxY last row
     * @param visitor receives each tile
     */
    void tiles(int minX, int minY, int maxX, int maxY, TileVisitor visitor);

    /**
     * Returns whether a cell holds a full, unflipped-equivalent solid collision tile; its faces towards neighbours are
     * internal.
     *
     * @param x column
     * @param y row
     * @return {@code true} for a full block
     */
    boolean isFullTile(int x, int y);

    /**
     * Returns the events of the engine.
     *
     * @return the events
     */
    Events events();

    /**
     * Returns the length of a tick.
     *
     * @return seconds
     */
    float tickSeconds();
}
