package dev.gulp.api.nav;

import dev.gulp.api.math.Vec2;

/**
 * Directions towards one target from every cell around it, computed once for any number of units.
 *
 * <pre>{@code
 * FlowField toBase = world.navGrid().flowField(base.position(), 40);
 * for (Entity zombie : zombies) {
 *     zombie.get(Mover.class).moveAndSlide(toBase.direction(zombie.position()).scale(2f));
 * }
 * }</pre>
 */
public interface FlowField {

    /**
     * Returns the target.
     *
     * @return world units
     */
    Vec2 target();

    /**
     * Returns which way to walk from a position.
     *
     * @param position world units
     * @return a unit vector, or {@link Vec2#ZERO} at the target or where it cannot be reached
     */
    Vec2 direction(Vec2 position);

    /**
     * Returns the path cost from a position to the target.
     *
     * @param position world units
     * @return the cost, or {@link Float#POSITIVE_INFINITY} if unreachable or outside the field
     */
    float distance(Vec2 position);

    /**
     * Returns whether the target can be reached from a position.
     *
     * @param position world units
     * @return {@code true} if reachable within the field
     */
    boolean isReachable(Vec2 position);
}
