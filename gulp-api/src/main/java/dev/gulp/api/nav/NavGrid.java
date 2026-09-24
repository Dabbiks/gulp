package dev.gulp.api.nav;

import dev.gulp.api.Owner;
import dev.gulp.api.event.Subscription;
import dev.gulp.api.math.GridPos;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.scheduler.Promise;
import org.jspecify.annotations.Nullable;

/**
 * The walkable cells of a world, one per tile, read from the tile map as it is: a cell is blocked when a collision
 * layer has a tile that is not {@linkplain dev.gulp.api.world.TileType#isPassable() passable} there, and costs the
 * highest {@linkplain dev.gulp.api.world.TileType#navCost() navigation cost} of its tiles. Dynamic obstacles block
 * cells until their owner goes away.
 *
 * <pre>{@code
 * NavGrid grid = world.navGrid();
 * Subscription crate = grid.block(crateEntity.bounds(), this);
 * grid.requestPath(enemy.position(), player.position(), PathOptions.DEFAULT).thenSync(path -> follow(path));
 * }</pre>
 */
public interface NavGrid {

    /**
     * Returns the cell containing a position.
     *
     * @param position world units
     * @return the cell
     */
    GridPos cellAt(Vec2 position);

    /**
     * Returns the centre of a cell.
     *
     * @param cell the cell
     * @return world units
     */
    Vec2 center(GridPos cell);

    /**
     * Returns whether a cell can be walked through.
     *
     * @param x cell column
     * @param y cell row
     * @return {@code true} if passable and not blocked
     */
    boolean isPassable(int x, int y);

    /**
     * Returns the cost of entering a cell.
     *
     * @param x cell column
     * @param y cell row
     * @return at least {@code 1}, or {@link Float#POSITIVE_INFINITY} when blocked
     */
    float cost(int x, int y);

    /**
     * Blocks the cells overlapping an area until the subscription is cancelled or the owner is disabled.
     *
     * @param area world units
     * @param owner the module or other owner
     * @return the subscription that frees the cells
     */
    Subscription block(Rect area, Owner owner);

    /**
     * Returns whether a straight walk between two points crosses only passable cells.
     *
     * @param from world units
     * @param to world units
     * @return {@code true} if the line is clear
     */
    boolean hasLineOfSight(Vec2 from, Vec2 to);

    /**
     * Finds a path at once with the default options.
     *
     * @param from start, world units
     * @param to goal
     * @return the path, starting at {@code from} and ending at {@code to}, or {@code null} if unreachable
     */
    @Nullable Path findPath(Vec2 from, Vec2 to);

    /**
     * Finds a path at once.
     *
     * @param from start, world units
     * @param to goal
     * @param options how to search
     * @return the path, or {@code null} if unreachable within the node limit
     */
    @Nullable Path findPath(Vec2 from, Vec2 to, PathOptions options);

    /**
     * Finds a path over the coming ticks with a per-tick budget, so many agents do not stall a frame.
     *
     * @param from start, world units
     * @param to goal
     * @param options how to search
     * @return completes with the path, or fails if the goal is unreachable
     */
    Promise<Path> requestPath(Vec2 from, Vec2 to, PathOptions options);

    /**
     * Computes the direction towards a target from every cell within a radius, for many units with the same goal.
     *
     * @param target the goal, world units
     * @param radius how far from the target the field reaches, in cells
     * @return the field
     */
    FlowField flowField(Vec2 target, int radius);

    /**
     * Returns a number that changes whenever tiles or blocks change, so agents know when to search again.
     *
     * @return the revision
     */
    long revision();
}
