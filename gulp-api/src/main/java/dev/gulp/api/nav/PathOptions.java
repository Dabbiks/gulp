package dev.gulp.api.nav;

/**
 * How to search a {@link NavGrid}.
 *
 * <pre>{@code
 * Path path = grid.findPath(from, to, PathOptions.DEFAULT.withDiagonal(false));
 * }</pre>
 *
 * @param diagonal whether to move in 8 directions (without cutting corners) or only 4
 * @param smooth whether to remove unneeded turns by checking lines of sight
 * @param algorithm the search algorithm
 * @param maxNodes the most cells to expand before giving up
 */
public record PathOptions(boolean diagonal, boolean smooth, Algorithm algorithm, int maxNodes) {

    /** 8 directions, smoothed, automatic algorithm, up to 20 000 cells. */
    public static final PathOptions DEFAULT = new PathOptions(true, true, Algorithm.AUTO, 20_000);

    /** Search algorithms. */
    public enum Algorithm {
        /** A* with tile costs, or Jump Point Search for long searches on 8 directions. */
        AUTO,
        /** A* with tile costs. */
        A_STAR,
        /** Jump Point Search: much faster on large open grids; treats every passable cell as cost 1. */
        JUMP_POINT
    }

    /**
     * Validates the options.
     *
     * @param diagonal diagonal moves
     * @param smooth smoothing
     * @param algorithm algorithm
     * @param maxNodes node limit
     */
    public PathOptions {
        if (maxNodes <= 0) {
            throw new IllegalArgumentException("maxNodes must be positive");
        }
    }

    /**
     * Returns these options with another diagonal setting.
     *
     * @param value whether diagonal moves are allowed
     * @return new options
     */
    public PathOptions withDiagonal(boolean value) {
        return new PathOptions(value, smooth, algorithm, maxNodes);
    }

    /**
     * Returns these options with another smoothing setting.
     *
     * @param value whether to smooth
     * @return new options
     */
    public PathOptions withSmooth(boolean value) {
        return new PathOptions(diagonal, value, algorithm, maxNodes);
    }

    /**
     * Returns these options with another algorithm.
     *
     * @param value the algorithm
     * @return new options
     */
    public PathOptions withAlgorithm(Algorithm value) {
        return new PathOptions(diagonal, smooth, value, maxNodes);
    }

    /**
     * Returns these options with another node limit.
     *
     * @param value the most cells to expand
     * @return new options
     */
    public PathOptions withMaxNodes(int value) {
        return new PathOptions(diagonal, smooth, algorithm, value);
    }
}
