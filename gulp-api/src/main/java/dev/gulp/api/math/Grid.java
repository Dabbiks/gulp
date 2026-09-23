package dev.gulp.api.math;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Conversion between world coordinates and grid cells, plus grid algorithms: Bresenham lines and flood fill.
 *
 * <pre>{@code
 * Grid grid = new Grid(1f, 1f);                     // one cell per world unit (one tile)
 * GridPos cell = grid.cellAt(player.position());
 * List<GridPos> sight = Grid.line(cell, enemyCell);
 * List<GridPos> room = Grid.floodFill(cell, c -> !isWall(c), 1000);
 * }</pre>
 *
 * @param cellWidth width of a cell in world units, positive
 * @param cellHeight height of a cell in world units, positive
 */
public record Grid(float cellWidth, float cellHeight) {

    /**
     * Validates the cell size.
     *
     * @throws IllegalArgumentException if a size is not positive
     */
    public Grid {
        if (!(cellWidth > 0f) || !(cellHeight > 0f)) {
            throw new IllegalArgumentException("Cell size must be positive");
        }
    }

    /**
     * The cell containing a point.
     *
     * @param point world coordinates
     * @return the cell
     */
    public GridPos cellAt(Vec2 point) {
        return new GridPos((int) Math.floor(point.x() / cellWidth), (int) Math.floor(point.y() / cellHeight));
    }

    /**
     * Top-left corner of a cell.
     *
     * @param cell the cell
     * @return world coordinates
     */
    public Vec2 cellOrigin(GridPos cell) {
        return new Vec2(cell.x() * cellWidth, cell.y() * cellHeight);
    }

    /**
     * Center of a cell.
     *
     * @param cell the cell
     * @return world coordinates
     */
    public Vec2 cellCenter(GridPos cell) {
        return new Vec2((cell.x() + 0.5f) * cellWidth, (cell.y() + 0.5f) * cellHeight);
    }

    /**
     * Bounds of a cell.
     *
     * @param cell the cell
     * @return the rectangle
     */
    public Rect cellBounds(GridPos cell) {
        return new Rect(cell.x() * cellWidth, cell.y() * cellHeight, cellWidth, cellHeight);
    }

    /**
     * Cells on a line between two cells (Bresenham), both ends included.
     *
     * @param from start cell
     * @param to end cell
     * @return the cells in order
     */
    public static List<GridPos> line(GridPos from, GridPos to) {
        List<GridPos> cells = new ArrayList<>();
        int x = from.x();
        int y = from.y();
        int dx = Math.abs(to.x() - x);
        int dy = -Math.abs(to.y() - y);
        int sx = x < to.x() ? 1 : -1;
        int sy = y < to.y() ? 1 : -1;
        int error = dx + dy;
        while (true) {
            cells.add(new GridPos(x, y));
            if (x == to.x() && y == to.y()) {
                return cells;
            }
            int e2 = 2 * error;
            if (e2 >= dy) {
                error += dy;
                x += sx;
            }
            if (e2 <= dx) {
                error += dx;
                y += sy;
            }
        }
    }

    /**
     * Cells reachable from a start cell through 4-neighbours that pass a test.
     *
     * @param start the start cell; included if it passes the test
     * @param passable which cells can be entered
     * @param limit the largest number of cells to return
     * @return the reached cells, in breadth-first order
     */
    public static List<GridPos> floodFill(GridPos start, Predicate<GridPos> passable, int limit) {
        List<GridPos> result = new ArrayList<>();
        if (!passable.test(start)) {
            return result;
        }
        Set<GridPos> seen = new HashSet<>();
        ArrayDeque<GridPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && result.size() < limit) {
            GridPos cell = queue.removeFirst();
            result.add(cell);
            for (GridPos next : cell.neighbors4()) {
                if (seen.add(next) && passable.test(next)) {
                    queue.add(next);
                }
            }
        }
        return result;
    }
}
