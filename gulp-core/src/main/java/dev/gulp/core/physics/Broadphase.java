package dev.gulp.core.physics;

import dev.gulp.core.util.LongObjectMap;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Uniform grid over proxy bounds. A proxy is listed in every cell its bounds touch; moving within the same cells costs
 * nothing, and queries visit each proxy once.
 */
final class Broadphase {

    static final float CELL = 4f;

    private final LongObjectMap<ArrayList<Proxy>> cells = new LongObjectMap<>();
    private int stamp;

    static int cell(float coordinate) {
        return (int) Math.floor(coordinate / CELL);
    }

    void insert(Proxy proxy) {
        int x0 = cell(proxy.minX);
        int y0 = cell(proxy.minY);
        int x1 = cell(proxy.maxX);
        int y1 = cell(proxy.maxY);
        for (int cy = y0; cy <= y1; cy++) {
            for (int cx = x0; cx <= x1; cx++) {
                long key = LongObjectMap.pack(cx, cy);
                ArrayList<Proxy> list = cells.get(key);
                if (list == null) {
                    list = new ArrayList<>(4);
                    cells.put(key, list);
                }
                list.add(proxy);
            }
        }
        proxy.cellMinX = x0;
        proxy.cellMinY = y0;
        proxy.cellMaxX = x1;
        proxy.cellMaxY = y1;
        proxy.inGrid = true;
    }

    void remove(Proxy proxy) {
        if (!proxy.inGrid) {
            return;
        }
        for (int cy = proxy.cellMinY; cy <= proxy.cellMaxY; cy++) {
            for (int cx = proxy.cellMinX; cx <= proxy.cellMaxX; cx++) {
                long key = LongObjectMap.pack(cx, cy);
                ArrayList<Proxy> list = cells.get(key);
                if (list != null) {
                    int index = list.indexOf(proxy);
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
        proxy.inGrid = false;
    }

    void moved(Proxy proxy) {
        if (proxy.inGrid
                && cell(proxy.minX) == proxy.cellMinX
                && cell(proxy.minY) == proxy.cellMinY
                && cell(proxy.maxX) == proxy.cellMaxX
                && cell(proxy.maxY) == proxy.cellMaxY) {
            return;
        }
        remove(proxy);
        insert(proxy);
    }

    /** Visits every proxy whose bounds overlap a rectangle. */
    void query(float minX, float minY, float maxX, float maxY, Consumer<Proxy> action) {
        int stampNow = ++stamp;
        int cx0 = cell(minX);
        int cy0 = cell(minY);
        int cx1 = cell(maxX);
        int cy1 = cell(maxY);
        if ((long) (cx1 - cx0 + 1) * (cy1 - cy0 + 1) > cells.size() * 4L) {
            cells.forEachValue(list -> visit(list, stampNow, minX, minY, maxX, maxY, action));
            return;
        }
        for (int cy = cy0; cy <= cy1; cy++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                ArrayList<Proxy> list = cells.get(LongObjectMap.pack(cx, cy));
                if (list != null) {
                    visit(list, stampNow, minX, minY, maxX, maxY, action);
                }
            }
        }
    }

    private static void visit(
            ArrayList<Proxy> list,
            int stampNow,
            float minX,
            float minY,
            float maxX,
            float maxY,
            Consumer<Proxy> action) {
        for (int i = 0; i < list.size(); i++) {
            Proxy proxy = list.get(i);
            if (proxy.stamp == stampNow) {
                continue;
            }
            proxy.stamp = stampNow;
            if (proxy.maxX >= minX && proxy.minX <= maxX && proxy.maxY >= minY && proxy.minY <= maxY) {
                action.accept(proxy);
            }
        }
    }

    void clear() {
        cells.clear();
    }
}
