package dev.gulp.core.world;

import dev.gulp.core.util.LongObjectMap;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Uniform grid over entity bounds. An entity is listed in every cell its bounds touch; moving within the same cells
 * costs nothing. Queries visit each entity once by stamping it.
 */
final class SpatialGrid {

    static final float CELL = 4f;

    private final LongObjectMap<ArrayList<EntityImpl>> cells = new LongObjectMap<>();
    private int stamp;

    static int cell(float coordinate) {
        return (int) Math.floor(coordinate / CELL);
    }

    void insert(EntityImpl entity) {
        float hw = entity.width / 2f;
        float hh = entity.height / 2f;
        int minX = cell(entity.x - hw);
        int minY = cell(entity.y - hh);
        int maxX = cell(entity.x + hw);
        int maxY = cell(entity.y + hh);
        for (int cy = minY; cy <= maxY; cy++) {
            for (int cx = minX; cx <= maxX; cx++) {
                long key = LongObjectMap.pack(cx, cy);
                ArrayList<EntityImpl> list = cells.get(key);
                if (list == null) {
                    list = new ArrayList<>(4);
                    cells.put(key, list);
                }
                list.add(entity);
            }
        }
        entity.cellMinX = minX;
        entity.cellMinY = minY;
        entity.cellMaxX = maxX;
        entity.cellMaxY = maxY;
        entity.inGrid = true;
    }

    void remove(EntityImpl entity) {
        if (!entity.inGrid) {
            return;
        }
        for (int cy = entity.cellMinY; cy <= entity.cellMaxY; cy++) {
            for (int cx = entity.cellMinX; cx <= entity.cellMaxX; cx++) {
                long key = LongObjectMap.pack(cx, cy);
                ArrayList<EntityImpl> list = cells.get(key);
                if (list != null) {
                    int index = list.indexOf(entity);
                    if (index >= 0) {
                        int last = list.size() - 1;
                        list.set(index, list.get(last));
                        list.remove(last);
                    }
                    if (list.isEmpty()) {
                        cells.remove(key);
                    }
                }
            }
        }
        entity.inGrid = false;
    }

    /** Updates the cells of an entity after it moved or resized. */
    void moved(EntityImpl entity) {
        if (!entity.inGrid) {
            return;
        }
        float hw = entity.width / 2f;
        float hh = entity.height / 2f;
        if (cell(entity.x - hw) == entity.cellMinX
                && cell(entity.y - hh) == entity.cellMinY
                && cell(entity.x + hw) == entity.cellMaxX
                && cell(entity.y + hh) == entity.cellMaxY) {
            return;
        }
        remove(entity);
        insert(entity);
    }

    /**
     * Visits every entity whose cells overlap a rectangle; the caller checks exact bounds.
     *
     * @param minX left
     * @param minY top
     * @param maxX right
     * @param maxY bottom
     * @param action receives each entity once
     */
    void query(float minX, float minY, float maxX, float maxY, Consumer<EntityImpl> action) {
        int stampNow = ++stamp;
        int cx0 = cell(minX);
        int cy0 = cell(minY);
        int cx1 = cell(maxX);
        int cy1 = cell(maxY);
        if ((long) (cx1 - cx0 + 1) * (cy1 - cy0 + 1) > cells.size() * 4L) {
            // A huge area: walk the occupied cells instead of the empty ones.
            cells.forEachValue(list -> visit(list, stampNow, minX, minY, maxX, maxY, action));
            return;
        }
        for (int cy = cy0; cy <= cy1; cy++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                ArrayList<EntityImpl> list = cells.get(LongObjectMap.pack(cx, cy));
                if (list != null) {
                    visit(list, stampNow, minX, minY, maxX, maxY, action);
                }
            }
        }
    }

    private static void visit(
            ArrayList<EntityImpl> list,
            int stampNow,
            float minX,
            float minY,
            float maxX,
            float maxY,
            Consumer<EntityImpl> action) {
        for (int i = 0; i < list.size(); i++) {
            EntityImpl entity = list.get(i);
            if (entity.gridStamp == stampNow) {
                continue;
            }
            entity.gridStamp = stampNow;
            float hw = entity.width / 2f;
            float hh = entity.height / 2f;
            if (entity.x + hw >= minX && entity.x - hw <= maxX && entity.y + hh >= minY && entity.y - hh <= maxY) {
                action.accept(entity);
            }
        }
    }

    int cellCount() {
        return cells.size();
    }

    void clear() {
        cells.clear();
    }
}
