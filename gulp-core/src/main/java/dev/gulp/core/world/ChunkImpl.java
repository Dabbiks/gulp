package dev.gulp.core.world;

import dev.gulp.api.world.Chunk;
import dev.gulp.api.world.World;
import dev.gulp.core.util.IntList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@link Chunk}: one {@code int[SIZE * SIZE]} per tile layer (tile id in the low bits, flip flags in the high bits), the
 * positions of ticking tiles, the entities spawned with it, and the render cache of each layer.
 */
final class ChunkImpl implements Chunk {

    static final int CELLS = SIZE * SIZE;
    static final int ID_MASK = 0x1FFFFFFF;
    static final int FLIP_H = 0x80000000;
    static final int FLIP_V = 0x40000000;
    static final int FLIP_D = 0x20000000;

    final TileMapImpl map;
    final int cx;
    final int cy;

    @Nullable int[][] cells = new int[4][];

    final IntList ticking = new IntList();
    final List<EntityImpl> spawned = new ArrayList<>();

    @Nullable Object[] renderCache = new Object[4];

    int version;
    boolean loaded = true;
    boolean modified;
    int lastNeeded;

    ChunkImpl(TileMapImpl map, int cx, int cy) {
        this.map = map;
        this.cx = cx;
        this.cy = cy;
    }

    int[] layer(int index) {
        if (index >= cells.length) {
            cells = Arrays.copyOf(cells, Math.max(index + 1, cells.length * 2));
            renderCache = Arrays.copyOf(renderCache, cells.length);
        }
        int[] data = cells[index];
        if (data == null) {
            data = new int[CELLS];
            cells[index] = data;
        }
        return data;
    }

    int get(int layerIndex, int localIndex) {
        if (layerIndex >= cells.length) {
            return 0;
        }
        int[] data = cells[layerIndex];
        return data == null ? 0 : data[localIndex];
    }

    @Nullable Object cache(int layerIndex) {
        return layerIndex < renderCache.length ? renderCache[layerIndex] : null;
    }

    void setCache(int layerIndex, Object value) {
        if (layerIndex >= renderCache.length) {
            renderCache = Arrays.copyOf(renderCache, layerIndex + 1);
        }
        renderCache[layerIndex] = value;
    }

    @Override
    public World world() {
        return map.world;
    }

    @Override
    public int x() {
        return cx;
    }

    @Override
    public int y() {
        return cy;
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public String toString() {
        return "Chunk[" + cx + ", " + cy + (modified ? ", modified" : "") + "]";
    }
}
