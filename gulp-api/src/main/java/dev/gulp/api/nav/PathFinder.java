package dev.gulp.api.nav;

import dev.gulp.api.math.Vec2;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import org.jspecify.annotations.Nullable;

/**
 * Path finding: A* on any {@link Graph}, and shortcuts to the grid searches of {@link NavGrid} (A* with 4 or 8
 * directions, Jump Point Search, flow fields).
 *
 * <pre>{@code
 * List<Room> route = PathFinder.find(dungeon, entrance, treasure);
 * Path walk = PathFinder.find(world.navGrid(), enemy.position(), player.position());
 * }</pre>
 */
public final class PathFinder {

    private PathFinder() {}

    /**
     * Finds the cheapest route between two nodes of a graph with A*.
     *
     * @param graph the graph
     * @param start the start node
     * @param goal the goal node
     * @param <N> the node type
     * @return the nodes from start to goal, or {@code null} if the goal cannot be reached
     */
    public static <N> @Nullable List<N> find(Graph<N> graph, N start, N goal) {
        return find(graph, start, goal, 100_000);
    }

    /**
     * Finds the cheapest route between two nodes of a graph with A*, expanding at most {@code maxNodes} nodes.
     *
     * @param graph the graph
     * @param start the start node
     * @param goal the goal node
     * @param maxNodes the most nodes to expand
     * @param <N> the node type
     * @return the nodes from start to goal, or {@code null} if not reached within the limit
     */
    public static <N> @Nullable List<N> find(Graph<N> graph, N start, N goal, int maxNodes) {
        Map<N, Record<N>> records = new HashMap<>();
        PriorityQueue<Record<N>> open = new PriorityQueue<>((a, b) -> Float.compare(a.estimate, b.estimate));
        Record<N> first = new Record<>(start, null, 0f, graph.estimate(start, goal));
        records.put(start, first);
        open.add(first);
        int expanded = 0;
        while (!open.isEmpty()) {
            Record<N> current = open.poll();
            if (current.closed) {
                continue;
            }
            if (current.node.equals(goal)) {
                List<N> route = new ArrayList<>();
                for (Record<N> r = current; r != null; r = r.parent) {
                    route.add(r.node);
                }
                Collections.reverse(route);
                return route;
            }
            current.closed = true;
            if (++expanded > maxNodes) {
                return null;
            }
            graph.neighbours(current.node, (neighbour, cost) -> {
                if (cost < 0f) {
                    throw new IllegalArgumentException("Graph edge costs must not be negative");
                }
                float g = current.cost + cost;
                Record<N> known = records.get(neighbour);
                if (known == null) {
                    Record<N> next = new Record<>(neighbour, current, g, g + graph.estimate(neighbour, goal));
                    records.put(neighbour, next);
                    open.add(next);
                } else if (!known.closed && g < known.cost) {
                    // Re-add with the better cost; the stale entry is skipped when polled because it is closed first.
                    Record<N> better = new Record<>(neighbour, current, g, g + graph.estimate(neighbour, goal));
                    known.closed = true;
                    records.put(neighbour, better);
                    open.add(better);
                }
            });
        }
        return null;
    }

    /**
     * Finds a path on a navigation grid with the default options.
     *
     * @param grid the grid
     * @param from start position, world units
     * @param to goal position
     * @return the path from the start cell centre to the goal, or {@code null} if unreachable
     */
    public static @Nullable Path find(NavGrid grid, Vec2 from, Vec2 to) {
        return grid.findPath(from, to, PathOptions.DEFAULT);
    }

    private static final class Record<N> {
        final N node;
        final @Nullable Record<N> parent;
        final float cost;
        final float estimate;
        boolean closed;

        Record(N node, @Nullable Record<N> parent, float cost, float estimate) {
            this.node = node;
            this.parent = parent;
            this.cost = cost;
            this.estimate = estimate;
        }
    }
}
