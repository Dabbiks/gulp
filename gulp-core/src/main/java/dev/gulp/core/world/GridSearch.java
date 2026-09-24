package dev.gulp.core.world;

import dev.gulp.api.math.GridPos;
import dev.gulp.core.util.LongObjectMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A grid path search that can run a little at a time: A* with cell costs (4 or 8 directions, no corner cutting), or
 * Jump Point Search with uniform costs. Jumps are loops, not recursion, and stop after {@link #MAX_JUMP} cells so that
 * open, endless worlds stay bounded.
 */
final class GridSearch {

    /** Cost of entering a cell; {@link Float#POSITIVE_INFINITY} when blocked. */
    interface Costs {
        float cost(int x, int y);
    }

    static final int MAX_JUMP = 128;

    /** Jump Point Search stays within this many cells around the start and goal; open, endless worlds need a box. */
    static final int JUMP_MARGIN = 64;

    private static final float SQRT2 = (float) Math.sqrt(2);

    private static final class Node {
        final int x;
        final int y;
        float g;
        float f;

        @Nullable Node parent;

        boolean closed;
        int heapIndex = -1;

        Node(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private final Costs costs;
    private final int goalX;
    private final int goalY;
    private final boolean diagonal;
    private final boolean jump;
    private final int maxNodes;
    private final int minX;
    private final int minY;
    private final int maxX;
    private final int maxY;
    private final LongObjectMap<Node> nodes = new LongObjectMap<>();
    private Node[] heap = new Node[64];
    private int heapSize;
    private int expanded;
    private boolean finished;
    private @Nullable Node found;

    GridSearch(
            Costs costs, int startX, int startY, int goalX, int goalY, boolean diagonal, boolean jump, int maxNodes) {
        this.costs = costs;
        this.goalX = goalX;
        this.goalY = goalY;
        this.diagonal = diagonal;
        this.jump = jump && diagonal;
        this.maxNodes = maxNodes;
        int margin = this.jump ? JUMP_MARGIN : Integer.MAX_VALUE / 4;
        minX = Math.min(startX, goalX) - margin;
        minY = Math.min(startY, goalY) - margin;
        maxX = Math.max(startX, goalX) + margin;
        maxY = Math.max(startY, goalY) + margin;
        Node start = node(startX, startY);
        start.g = 0f;
        start.f = heuristic(startX, startY);
        push(start);
        if (!passable(goalX, goalY)) {
            finished = true;
        }
    }

    boolean isFinished() {
        return finished;
    }

    /**
     * Expands up to {@code budget} cells.
     *
     * @return whether the search finished (found or failed)
     */
    boolean step(int budget) {
        int limit = expanded + budget;
        while (!finished && heapSize > 0 && expanded < limit) {
            Node current = pop();
            if (current.closed) {
                continue;
            }
            current.closed = true;
            expanded++;
            if (current.x == goalX && current.y == goalY) {
                found = current;
                finished = true;
                break;
            }
            if (expanded > maxNodes) {
                finished = true;
                break;
            }
            if (jump) {
                expandJumps(current);
            } else {
                expandNeighbours(current);
            }
        }
        if (heapSize == 0) {
            finished = true;
        }
        return finished;
    }

    /** Cells from start to goal, every cell included; {@code null} if not found. */
    @Nullable List<GridPos> cells() {
        Node end = found;
        if (end == null) {
            return null;
        }
        List<GridPos> points = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) {
            points.add(new GridPos(n.x, n.y));
        }
        Collections.reverse(points);
        if (!jump) {
            return points;
        }
        // Fill the straight and diagonal runs between jump points.
        List<GridPos> filled = new ArrayList<>();
        filled.add(points.get(0));
        for (int i = 1; i < points.size(); i++) {
            GridPos from = points.get(i - 1);
            GridPos to = points.get(i);
            int dx = Integer.signum(to.x() - from.x());
            int dy = Integer.signum(to.y() - from.y());
            int x = from.x();
            int y = from.y();
            while (x != to.x() || y != to.y()) {
                if (x != to.x()) {
                    x += dx;
                }
                if (y != to.y()) {
                    y += dy;
                }
                filled.add(new GridPos(x, y));
            }
        }
        return filled;
    }

    private boolean passable(int x, int y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && costs.cost(x, y) != Float.POSITIVE_INFINITY;
    }

    private float heuristic(int x, int y) {
        int dx = Math.abs(x - goalX);
        int dy = Math.abs(y - goalY);
        return diagonal ? (dx + dy) + (SQRT2 - 2f) * Math.min(dx, dy) : dx + dy;
    }

    private Node node(int x, int y) {
        long key = LongObjectMap.pack(x, y);
        Node n = nodes.get(key);
        if (n == null) {
            n = new Node(x, y);
            n.g = Float.POSITIVE_INFINITY;
            nodes.put(key, n);
        }
        return n;
    }

    private void relax(Node from, int x, int y, float stepCost) {
        Node next = node(x, y);
        if (next.closed) {
            return;
        }
        float g = from.g + stepCost;
        if (g < next.g) {
            next.g = g;
            next.f = g + heuristic(x, y);
            next.parent = from;
            if (next.heapIndex >= 0) {
                siftUp(next.heapIndex);
            } else {
                push(next);
            }
        }
    }

    private void expandNeighbours(Node current) {
        for (int dir = 0; dir < (diagonal ? 8 : 4); dir++) {
            int dx = DX[dir];
            int dy = DY[dir];
            int x = current.x + dx;
            int y = current.y + dy;
            float cost = costs.cost(x, y);
            if (cost == Float.POSITIVE_INFINITY) {
                continue;
            }
            if (dx != 0 && dy != 0) {
                if (!passable(current.x + dx, current.y) || !passable(current.x, current.y + dy)) {
                    continue;
                }
                cost *= SQRT2;
            }
            relax(current, x, y, cost);
        }
    }

    private static final int[] DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] DY = {0, 0, 1, -1, 1, -1, 1, -1};

    private int jumpX;
    private int jumpY;

    private void expandJumps(Node current) {
        for (int dir = 0; dir < 8; dir++) {
            int dx = DX[dir];
            int dy = DY[dir];
            if (dx != 0 && dy != 0) {
                if (!passable(current.x + dx, current.y) || !passable(current.x, current.y + dy)) {
                    continue;
                }
            }
            if (jumpFrom(current.x, current.y, dx, dy)) {
                int ddx = Math.abs(jumpX - current.x);
                int ddy = Math.abs(jumpY - current.y);
                float distance = Math.max(ddx, ddy) + (SQRT2 - 1f) * Math.min(ddx, ddy);
                relax(current, jumpX, jumpY, distance);
            }
        }
    }

    /** Jumps from a cell in a direction; the jump point lands in {@link #jumpX}, {@link #jumpY}. */
    private boolean jumpFrom(int x, int y, int dx, int dy) {
        if (dx != 0 && dy != 0) {
            int cx = x;
            int cy = y;
            for (int step = 0; step < MAX_JUMP; step++) {
                cx += dx;
                cy += dy;
                expanded++;
                if (!passable(cx, cy)) {
                    return false;
                }
                if ((cx == goalX && cy == goalY)
                        || straight(cx, cy, dx, 0)
                        || straight(cx, cy, 0, dy)
                        || step == MAX_JUMP - 1) {
                    jumpX = cx;
                    jumpY = cy;
                    return true;
                }
                if (!passable(cx + dx, cy) || !passable(cx, cy + dy)) {
                    return false;
                }
            }
            return false;
        }
        int cx = x;
        int cy = y;
        for (int step = 0; step < MAX_JUMP; step++) {
            cx += dx;
            cy += dy;
            expanded++;
            if (!passable(cx, cy)) {
                return false;
            }
            if ((cx == goalX && cy == goalY) || forced(cx, cy, dx, dy) || step == MAX_JUMP - 1) {
                jumpX = cx;
                jumpY = cy;
                return true;
            }
        }
        return false;
    }

    /** Whether a straight scan from a cell finds a jump point; used by diagonal jumps. */
    private boolean straight(int x, int y, int dx, int dy) {
        int cx = x;
        int cy = y;
        for (int step = 0; step < MAX_JUMP; step++) {
            cx += dx;
            cy += dy;
            expanded++;
            if (!passable(cx, cy)) {
                return false;
            }
            if ((cx == goalX && cy == goalY) || forced(cx, cy, dx, dy)) {
                return true;
            }
        }
        return false;
    }

    /** A straight move makes a side cell reachable that the previous cell could not reach diagonally. */
    private boolean forced(int x, int y, int dx, int dy) {
        if (dx != 0) {
            return (passable(x, y - 1) && !passable(x - dx, y - 1)) || (passable(x, y + 1) && !passable(x - dx, y + 1));
        }
        return (passable(x - 1, y) && !passable(x - 1, y - dy)) || (passable(x + 1, y) && !passable(x + 1, y - dy));
    }

    // ------------------------------------------------------------------ binary heap on f

    private void push(Node n) {
        if (heapSize == heap.length) {
            heap = java.util.Arrays.copyOf(heap, heap.length * 2);
        }
        heap[heapSize] = n;
        n.heapIndex = heapSize;
        heapSize++;
        siftUp(n.heapIndex);
    }

    private Node pop() {
        Node top = heap[0];
        heapSize--;
        top.heapIndex = -1;
        if (heapSize > 0) {
            heap[0] = heap[heapSize];
            heap[0].heapIndex = 0;
            siftDown(0);
        }
        heap[heapSize] = null;
        return top;
    }

    private void siftUp(int index) {
        Node n = heap[index];
        while (index > 0) {
            int parentIndex = (index - 1) >>> 1;
            Node parent = heap[parentIndex];
            if (parent.f <= n.f) {
                break;
            }
            heap[index] = parent;
            parent.heapIndex = index;
            index = parentIndex;
        }
        heap[index] = n;
        n.heapIndex = index;
    }

    private void siftDown(int index) {
        Node n = heap[index];
        while (true) {
            int left = index * 2 + 1;
            if (left >= heapSize) {
                break;
            }
            int right = left + 1;
            int smallest = right < heapSize && heap[right].f < heap[left].f ? right : left;
            if (heap[smallest].f >= n.f) {
                break;
            }
            heap[index] = heap[smallest];
            heap[index].heapIndex = index;
            index = smallest;
        }
        heap[index] = n;
        n.heapIndex = index;
    }
}
