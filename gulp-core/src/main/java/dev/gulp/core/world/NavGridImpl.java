package dev.gulp.core.world;

import dev.gulp.api.Owner;
import dev.gulp.api.event.Subscription;
import dev.gulp.api.math.GridPos;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.nav.FlowField;
import dev.gulp.api.nav.NavGrid;
import dev.gulp.api.nav.Path;
import dev.gulp.api.nav.PathOptions;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.api.world.TileType;
import dev.gulp.core.scheduler.PromiseImpl;
import dev.gulp.core.util.LongObjectMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@link NavGrid} read straight from the tile map: a cell is blocked by impassable tiles of collision layers or by
 * dynamic blocks, and costs the highest navigation cost of its tiles. Requested paths are searched a little each tick
 * with a node budget shared by all requests.
 */
final class NavGridImpl implements NavGrid, GridSearch.Costs {

    /** Cells expanded per tick for all requested paths together. */
    static final int BUDGET_PER_TICK = 4_000;

    /** Beyond this straight-line distance in cells, {@code AUTO} searches with Jump Point Search. */
    static final int JUMP_POINT_DISTANCE = 48;

    private final WorldImpl world;
    private final LongObjectMap<int[]> blocked = new LongObjectMap<>();
    private final List<Block> blocks = new ArrayList<>();
    private final ArrayDeque<Request> requests = new ArrayDeque<>();
    private long blockRevision;

    NavGridImpl(WorldImpl world) {
        this.world = world;
    }

    private record Request(GridSearch search, Vec2 from, Vec2 to, PathOptions options, PromiseImpl<Path> promise) {}

    private final class Block implements Subscription {
        final int x0;
        final int y0;
        final int x1;
        final int y1;
        final Owner owner;
        boolean active = true;

        Block(Rect area, Owner owner) {
            x0 = (int) Math.floor(area.x());
            y0 = (int) Math.floor(area.y());
            x1 = (int) Math.ceil(area.x() + area.width()) - 1;
            y1 = (int) Math.ceil(area.y() + area.height()) - 1;
            this.owner = owner;
            mark(1);
        }

        void mark(int delta) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    long key = LongObjectMap.pack(x, y);
                    int[] count = blocked.get(key);
                    if (count == null) {
                        count = new int[1];
                        blocked.put(key, count);
                    }
                    count[0] += delta;
                    if (count[0] <= 0) {
                        blocked.remove(key);
                    }
                }
            }
            blockRevision++;
        }

        @Override
        public void cancel() {
            if (active) {
                active = false;
                mark(-1);
                blocks.remove(this);
            }
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }

    /** Frees blocks of disabled owners and advances the requested searches. */
    void tick() {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            Block block = blocks.get(i);
            if (!block.owner.isEnabled()) {
                block.cancel();
            }
        }
        int budget = BUDGET_PER_TICK;
        while (budget > 0 && !requests.isEmpty()) {
            Request request = requests.peek();
            int before = budget;
            boolean done = request.search.step(Math.min(budget, 1_000));
            budget -= Math.min(before, 1_000);
            if (done) {
                requests.poll();
                Path path = finish(request.search, request.from, request.to, request.options);
                if (path != null) {
                    request.promise.complete(path);
                } else {
                    request.promise.fail(
                            new IllegalStateException("No path from " + request.from + " to " + request.to));
                }
            }
        }
    }

    void clear() {
        requests.clear();
        blocks.clear();
        blocked.clear();
    }

    // ------------------------------------------------------------------ cells

    @Override
    public GridPos cellAt(Vec2 position) {
        return new GridPos((int) Math.floor(position.x()), (int) Math.floor(position.y()));
    }

    @Override
    public Vec2 center(GridPos cell) {
        return new Vec2(cell.x() + 0.5f, cell.y() + 0.5f);
    }

    @Override
    public float cost(int x, int y) {
        if (blocked.get(LongObjectMap.pack(x, y)) != null) {
            return Float.POSITIVE_INFINITY;
        }
        TileMapImpl map = world.tileMap;
        float cost = 1f;
        List<TileMapImpl.TileLayerImpl> layers = map.layers;
        for (int i = 0; i < layers.size(); i++) {
            TileMapImpl.TileLayerImpl layer = layers.get(i);
            if (!layer.isCollision()) {
                continue;
            }
            TileType type = map.typeOf(map.cell(layer, x, y));
            if (type == null) {
                continue;
            }
            if (!type.isPassable()) {
                return Float.POSITIVE_INFINITY;
            }
            cost = Math.max(cost, type.navCost());
        }
        return cost;
    }

    @Override
    public boolean isPassable(int x, int y) {
        return cost(x, y) != Float.POSITIVE_INFINITY;
    }

    @Override
    public Subscription block(Rect area, Owner owner) {
        Block block = new Block(area, owner);
        blocks.add(block);
        return block;
    }

    @Override
    public boolean hasLineOfSight(Vec2 from, Vec2 to) {
        int x = (int) Math.floor(from.x());
        int y = (int) Math.floor(from.y());
        int endX = (int) Math.floor(to.x());
        int endY = (int) Math.floor(to.y());
        float dx = to.x() - from.x();
        float dy = to.y() - from.y();
        int stepX = dx > 0f ? 1 : dx < 0f ? -1 : 0;
        int stepY = dy > 0f ? 1 : dy < 0f ? -1 : 0;
        float tDeltaX = stepX != 0 ? Math.abs(1f / dx) : Float.MAX_VALUE;
        float tDeltaY = stepY != 0 ? Math.abs(1f / dy) : Float.MAX_VALUE;
        float tMaxX = stepX > 0 ? (x + 1 - from.x()) / dx : stepX < 0 ? (from.x() - x) / -dx : Float.MAX_VALUE;
        float tMaxY = stepY > 0 ? (y + 1 - from.y()) / dy : stepY < 0 ? (from.y() - y) / -dy : Float.MAX_VALUE;
        int guard = Math.abs(endX - x) + Math.abs(endY - y) + 2;
        for (int i = 0; i <= guard; i++) {
            if (!isPassable(x, y)) {
                return false;
            }
            if (x == endX && y == endY) {
                return true;
            }
            if (Math.abs(tMaxX - tMaxY) < 1e-6f) {
                // Through a corner: both side cells must be open.
                if (!isPassable(x + stepX, y) || !isPassable(x, y + stepY)) {
                    return false;
                }
                tMaxX += tDeltaX;
                tMaxY += tDeltaY;
                x += stepX;
                y += stepY;
            } else if (tMaxX < tMaxY) {
                tMaxX += tDeltaX;
                x += stepX;
            } else {
                tMaxY += tDeltaY;
                y += stepY;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ paths

    private GridSearch search(Vec2 from, Vec2 to, PathOptions options) {
        GridPos start = cellAt(from);
        GridPos goal = cellAt(to);
        boolean jump =
                switch (options.algorithm()) {
                    case JUMP_POINT -> true;
                    case A_STAR -> false;
                    case AUTO ->
                        Math.max(Math.abs(goal.x() - start.x()), Math.abs(goal.y() - start.y())) > JUMP_POINT_DISTANCE;
                };
        return new GridSearch(
                this, start.x(), start.y(), goal.x(), goal.y(), options.diagonal(), jump, options.maxNodes());
    }

    @Override
    public @Nullable Path findPath(Vec2 from, Vec2 to) {
        return findPath(from, to, PathOptions.DEFAULT);
    }

    @Override
    public @Nullable Path findPath(Vec2 from, Vec2 to, PathOptions options) {
        GridSearch search = search(from, to, options);
        while (!search.step(Integer.MAX_VALUE / 4)) {
            // runs to the end
        }
        return finish(search, from, to, options);
    }

    @Override
    public Promise<Path> requestPath(Vec2 from, Vec2 to, PathOptions options) {
        PromiseImpl<Path> promise = world.worlds.assets.newPromise();
        requests.add(new Request(search(from, to, options), from, to, options, promise));
        return promise;
    }

    private @Nullable Path finish(GridSearch search, Vec2 from, Vec2 to, PathOptions options) {
        List<GridPos> cells = search.cells();
        if (cells == null) {
            return null;
        }
        List<Vec2> points = new ArrayList<>();
        points.add(from);
        if (options.smooth()) {
            // Keep only the cells where the straight line from the last kept point would be blocked.
            Vec2 anchor = from;
            for (int i = 1; i < cells.size() - 1; i++) {
                Vec2 next = center(cells.get(i + 1));
                if (!hasLineOfSight(anchor, next)) {
                    Vec2 kept = center(cells.get(i));
                    points.add(kept);
                    anchor = kept;
                }
            }
            if (!hasLineOfSight(anchor, to) && cells.size() > 1) {
                points.add(center(cells.get(cells.size() - 1)));
            }
        } else {
            for (int i = 1; i < cells.size() - 1; i++) {
                points.add(center(cells.get(i)));
            }
        }
        if (!points.get(points.size() - 1).equals(to)) {
            points.add(to);
        }
        return Path.of(points);
    }

    @Override
    public FlowField flowField(Vec2 target, int radius) {
        return new Field(target, Math.max(1, radius));
    }

    @Override
    public long revision() {
        return world.tileMap.revision + blockRevision;
    }

    /** Distances from every cell within a square radius to the target, by Dijkstra over the grid. */
    private final class Field implements FlowField {
        private final Vec2 target;
        private final int originX;
        private final int originY;
        private final int size;
        private final float[] distance;

        Field(Vec2 target, int radius) {
            this.target = target;
            GridPos goal = cellAt(target);
            originX = goal.x() - radius;
            originY = goal.y() - radius;
            size = radius * 2 + 1;
            distance = new float[size * size];
            java.util.Arrays.fill(distance, Float.POSITIVE_INFINITY);
            java.util.PriorityQueue<long[]> open = new java.util.PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
            int goalIndex = index(goal.x(), goal.y());
            distance[goalIndex] = 0f;
            open.add(new long[] {0L, goalIndex});
            float sqrt2 = (float) Math.sqrt(2);
            while (!open.isEmpty()) {
                long[] entry = open.poll();
                int index = (int) entry[1];
                float d = distance[index];
                if (Float.intBitsToFloat((int) entry[0]) > d) {
                    // A shorter way to this cell was found after this entry was queued.
                    continue;
                }
                int cx = originX + index % size;
                int cy = originY + index / size;
                for (int dir = 0; dir < 8; dir++) {
                    int dx = dir < 4 ? (dir == 0 ? 1 : dir == 1 ? -1 : 0) : (dir < 6 ? 1 : -1);
                    int dy = dir < 4 ? (dir == 2 ? 1 : dir == 3 ? -1 : 0) : (dir % 2 == 0 ? 1 : -1);
                    int nx = cx + dx;
                    int ny = cy + dy;
                    int next = index(nx, ny);
                    if (next < 0) {
                        continue;
                    }
                    float step = cost(nx, ny);
                    if (step == Float.POSITIVE_INFINITY) {
                        continue;
                    }
                    if (dx != 0 && dy != 0) {
                        if (!isPassable(cx + dx, cy) || !isPassable(cx, cy + dy)) {
                            continue;
                        }
                        step *= sqrt2;
                    }
                    float nd = d + step;
                    if (nd < distance[next]) {
                        distance[next] = nd;
                        open.add(new long[] {Float.floatToRawIntBits(nd) & 0xFFFFFFFFL, next});
                    }
                }
            }
        }

        private int index(int x, int y) {
            int lx = x - originX;
            int ly = y - originY;
            if (lx < 0 || ly < 0 || lx >= size || ly >= size) {
                return -1;
            }
            return ly * size + lx;
        }

        @Override
        public Vec2 target() {
            return target;
        }

        @Override
        public Vec2 direction(Vec2 position) {
            GridPos cell = cellAt(position);
            int index = index(cell.x(), cell.y());
            if (index < 0 || distance[index] == Float.POSITIVE_INFINITY) {
                return Vec2.ZERO;
            }
            if (distance[index] == 0f) {
                return position.distanceTo(target) < 1e-3f ? Vec2.ZERO : position.directionTo(target);
            }
            float best = distance[index];
            int bestX = cell.x();
            int bestY = cell.y();
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    int next = index(cell.x() + dx, cell.y() + dy);
                    if (next < 0 || distance[next] >= best) {
                        continue;
                    }
                    if (dx != 0
                            && dy != 0
                            && (!isPassable(cell.x() + dx, cell.y()) || !isPassable(cell.x(), cell.y() + dy))) {
                        continue;
                    }
                    best = distance[next];
                    bestX = cell.x() + dx;
                    bestY = cell.y() + dy;
                }
            }
            Vec2 aim = new Vec2(bestX + 0.5f, bestY + 0.5f);
            return position.distanceTo(aim) < 1e-3f ? Vec2.ZERO : position.directionTo(aim);
        }

        @Override
        public float distance(Vec2 position) {
            GridPos cell = cellAt(position);
            int index = index(cell.x(), cell.y());
            return index < 0 ? Float.POSITIVE_INFINITY : distance[index];
        }

        @Override
        public boolean isReachable(Vec2 position) {
            return distance(position) != Float.POSITIVE_INFINITY;
        }
    }
}
