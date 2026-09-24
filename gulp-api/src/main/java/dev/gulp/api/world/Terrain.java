package dev.gulp.api.world;

import dev.gulp.api.registry.Key;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A set of tiles that join up automatically (autotiling). The tile placed in a cell depends on which neighbours belong
 * to the same terrain.
 *
 * <p>{@link Mode#SIXTEEN}: 16 tiles chosen by the four sides; index = north 1 + east 2 + south 4 + west 8.
 *
 * <p>{@link Mode#BLOB}: 47 tiles chosen by sides and corners. A corner counts only when both sides next to it are the
 * same terrain. Bits: north 1, north-east 2, east 4, south-east 8, south 16, south-west 32, west 64, north-west 128;
 * the 47 reachable masks sorted ascending give the tile indices ({@link #blobIndex(int)}).
 *
 * <pre>{@code
 * Terrain water = Terrain.sixteen(key("water"), waterTiles);   // 16 tile types in mask order
 * world.tileMap().paintTerrain("ground", 2, 2, 6, 4, water);
 * }</pre>
 */
public final class Terrain {

    /** How neighbours choose the tile. */
    public enum Mode {
        /** Four sides, 16 tiles. */
        SIXTEEN,
        /** Sides and corners, 47 tiles. */
        BLOB
    }

    private static final int[] BLOB_MASKS = blobMasks();
    private static final int[] BLOB_INDEX = new int[256];

    static {
        java.util.Arrays.fill(BLOB_INDEX, -1);
        for (int i = 0; i < BLOB_MASKS.length; i++) {
            BLOB_INDEX[BLOB_MASKS[i]] = i;
        }
    }

    private final Key key;
    private final Mode mode;
    private final List<TileType> tiles;

    private Terrain(Key key, Mode mode, List<TileType> tiles) {
        int expected = mode == Mode.SIXTEEN ? 16 : 47;
        if (tiles.size() != expected) {
            throw new IllegalArgumentException(
                    "Terrain " + key + " needs " + expected + " tiles for " + mode + ", got " + tiles.size());
        }
        this.key = key;
        this.mode = mode;
        this.tiles = Collections.unmodifiableList(new ArrayList<>(tiles));
    }

    /**
     * Returns a 16-tile terrain.
     *
     * @param key the key
     * @param tiles 16 tile types in mask order
     * @return the terrain
     */
    public static Terrain sixteen(Key key, List<TileType> tiles) {
        return new Terrain(key, Mode.SIXTEEN, tiles);
    }

    /**
     * Returns a 47-tile terrain.
     *
     * @param key the key
     * @param tiles 47 tile types in {@link #blobIndex(int)} order
     * @return the terrain
     */
    public static Terrain blob(Key key, List<TileType> tiles) {
        return new Terrain(key, Mode.BLOB, tiles);
    }

    private static int[] blobMasks() {
        List<Integer> masks = new ArrayList<>();
        for (int mask = 0; mask < 256; mask++) {
            if (normalize(mask) == mask) {
                masks.add(mask);
            }
        }
        return masks.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Clears corner bits whose two sides are not both set.
     *
     * @param mask the eight-neighbour mask
     * @return the normalised mask
     */
    public static int normalize(int mask) {
        int result = mask & (1 | 4 | 16 | 64);
        if ((mask & 2) != 0 && (mask & 1) != 0 && (mask & 4) != 0) {
            result |= 2;
        }
        if ((mask & 8) != 0 && (mask & 4) != 0 && (mask & 16) != 0) {
            result |= 8;
        }
        if ((mask & 32) != 0 && (mask & 16) != 0 && (mask & 64) != 0) {
            result |= 32;
        }
        if ((mask & 128) != 0 && (mask & 64) != 0 && (mask & 1) != 0) {
            result |= 128;
        }
        return result;
    }

    /**
     * Returns the tile index of an eight-neighbour mask in {@link Mode#BLOB}.
     *
     * @param mask the mask; unreachable corner bits are ignored
     * @return the index, {@code 0..46}
     */
    public static int blobIndex(int mask) {
        return BLOB_INDEX[normalize(mask & 0xFF)];
    }

    /**
     * Returns the key.
     *
     * @return the key
     */
    public Key key() {
        return key;
    }

    /**
     * Returns the mode.
     *
     * @return the mode
     */
    public Mode mode() {
        return mode;
    }

    /**
     * Returns the tiles.
     *
     * @return 16 or 47 tile types
     */
    public List<TileType> tiles() {
        return tiles;
    }

    /**
     * Returns whether a tile belongs to this terrain.
     *
     * @param type the tile type
     * @return {@code true} if it is one of the tiles
     */
    public boolean contains(@Nullable TileType type) {
        return type != null && tiles.contains(type);
    }

    /**
     * Returns the tile for an eight-neighbour mask (bits as in the class description).
     *
     * @param mask the mask
     * @return the tile type
     */
    public TileType tileFor(int mask) {
        if (mode == Mode.SIXTEEN) {
            int sides = ((mask & 1) != 0 ? 1 : 0)
                    | ((mask & 4) != 0 ? 2 : 0)
                    | ((mask & 16) != 0 ? 4 : 0)
                    | ((mask & 64) != 0 ? 8 : 0);
            return tiles.get(sides);
        }
        return tiles.get(blobIndex(mask));
    }

    @Override
    public String toString() {
        return "Terrain[" + key + ", " + mode + "]";
    }
}
