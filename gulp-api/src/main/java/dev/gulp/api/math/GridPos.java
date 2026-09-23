package dev.gulp.api.math;

import java.util.List;

/**
 * Integer cell coordinates in a grid.
 *
 * <pre>{@code
 * GridPos cell = grid.cellAt(mouseWorld);
 * for (GridPos n : cell.neighbors4()) highlight(n);
 * }</pre>
 *
 * @param x column
 * @param y row (grows downwards)
 */
public record GridPos(int x, int y) {

    /** The origin cell. */
    public static final GridPos ORIGIN = new GridPos(0, 0);

    /**
     * Offsets the cell.
     *
     * @param dx column offset
     * @param dy row offset
     * @return the new cell
     */
    public GridPos add(int dx, int dy) {
        return new GridPos(x + dx, y + dy);
    }

    /**
     * The four edge neighbours: right, down, left, up.
     *
     * @return the neighbours
     */
    public List<GridPos> neighbors4() {
        return List.of(add(1, 0), add(0, 1), add(-1, 0), add(0, -1));
    }

    /**
     * The eight edge and corner neighbours.
     *
     * @return the neighbours
     */
    public List<GridPos> neighbors8() {
        return List.of(add(1, 0), add(1, 1), add(0, 1), add(-1, 1), add(-1, 0), add(-1, -1), add(0, -1), add(1, -1));
    }

    /**
     * Manhattan distance.
     *
     * @param other the cell
     * @return {@code |dx| + |dy|}
     */
    public int manhattan(GridPos other) {
        return Math.abs(other.x - x) + Math.abs(other.y - y);
    }

    /**
     * Chebyshev distance: moves when diagonal steps cost 1.
     *
     * @param other the cell
     * @return {@code max(|dx|, |dy|)}
     */
    public int chebyshev(GridPos other) {
        return Math.max(Math.abs(other.x - x), Math.abs(other.y - y));
    }
}
