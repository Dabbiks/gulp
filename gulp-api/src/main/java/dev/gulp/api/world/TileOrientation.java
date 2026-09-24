package dev.gulp.api.world;

/**
 * How tiles are laid out. One tile is one world unit wide; its height in units follows the pixel size of the map tiles.
 *
 * <pre>{@code
 * WorldSettings iso = WorldSettings.DEFAULT.orientation(TileOrientation.ISOMETRIC);
 * }</pre>
 */
public enum TileOrientation {
    /** Square grid. */
    ORTHOGONAL,
    /** Diamond grid: tile x goes down-right, tile y goes down-left. */
    ISOMETRIC,
    /** Diamond tiles in rows; odd rows shift half a tile right. */
    ISOMETRIC_STAGGERED,
    /** Hexagons with a point up; odd rows shift half a tile right. */
    HEXAGONAL_POINTY,
    /** Hexagons with a flat top; odd columns shift half a tile down. */
    HEXAGONAL_FLAT
}
