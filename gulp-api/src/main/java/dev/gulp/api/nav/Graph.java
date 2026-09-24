package dev.gulp.api.nav;

/**
 * Any graph to search with {@link PathFinder#find(Graph, Object, Object)}: rooms of a dungeon, waypoints, stations.
 * Nodes need proper {@code equals} and {@code hashCode}.
 *
 * <pre>{@code
 * Graph<Room> rooms = new Graph<>() {
 *     public void neighbours(Room room, Edges<Room> out) { room.doors().forEach(d -> out.add(d.to(), d.length())); }
 *     public float estimate(Room from, Room to) { return from.center().distanceTo(to.center()); }
 * };
 * List<Room> route = PathFinder.find(rooms, hall, vault);
 * }</pre>
 *
 * @param <N> the node type
 */
public interface Graph<N> {

    /**
     * Receives the neighbours of a node.
     *
     * @param <N> the node type
     */
    @FunctionalInterface
    interface Edges<N> {
        /**
         * Adds an edge.
         *
         * @param neighbour the node the edge leads to
         * @param cost the cost of the edge, at least {@code 0}
         */
        void add(N neighbour, float cost);
    }

    /**
     * Lists the edges leaving a node.
     *
     * @param node the node
     * @param out receives each neighbour and edge cost
     */
    void neighbours(N node, Edges<N> out);

    /**
     * Estimates the cost from one node to another; it must never overestimate for the path to be the cheapest.
     *
     * @param from a node
     * @param to the goal
     * @return the estimate, {@code 0} to search like Dijkstra
     */
    float estimate(N from, N to);
}
