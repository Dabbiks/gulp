package dev.gulp.api.world;

import dev.gulp.api.math.Vec2;

/**
 * A point in a world.
 *
 * <pre>{@code
 * Location spawn = new Location(world, 4, 10);
 * player.teleport(spawn.add(0, -1));
 * float far = spawn.distance(enemy.position());
 * }</pre>
 *
 * @param world the world
 * @param x world units
 * @param y world units, down is positive
 */
public record Location(World world, float x, float y) {

    /**
     * Returns a location in the same world.
     *
     * @param point world units
     * @return the location
     */
    public Location at(Vec2 point) {
        return new Location(world, point.x(), point.y());
    }

    /**
     * Returns the position as a vector.
     *
     * @return world units
     */
    public Vec2 toVec2() {
        return new Vec2(x, y);
    }

    /**
     * Returns a location moved by an offset.
     *
     * @param dx horizontal offset
     * @param dy vertical offset
     * @return the location
     */
    public Location add(float dx, float dy) {
        return new Location(world, x + dx, y + dy);
    }

    /**
     * Returns a location moved by an offset.
     *
     * @param offset the offset
     * @return the location
     */
    public Location add(Vec2 offset) {
        return add(offset.x(), offset.y());
    }

    /**
     * Returns the distance to a point, ignoring worlds.
     *
     * @param point world units
     * @return the distance
     */
    public float distance(Vec2 point) {
        float dx = point.x() - x;
        float dy = point.y() - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Returns the distance to another location.
     *
     * @param other the location
     * @return the distance
     * @throws IllegalArgumentException if it is in another world
     */
    public float distance(Location other) {
        if (other.world != world) {
            throw new IllegalArgumentException("Locations are in different worlds");
        }
        return distance(other.toVec2());
    }

    @Override
    public String toString() {
        return "Location[" + world.name() + ", " + x + ", " + y + "]";
    }
}
