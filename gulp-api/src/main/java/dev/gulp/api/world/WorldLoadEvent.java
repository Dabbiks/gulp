package dev.gulp.api.world;

import dev.gulp.api.event.Event;

/**
 * A world finished loading.
 *
 * <pre>{@code
 * on(WorldLoadEvent.class, e -> logger().info("Loaded " + e.world().name()));
 * }</pre>
 */
public final class WorldLoadEvent extends Event {

    private final World world;

    /**
     * Creates the event.
     *
     * @param world the world
     */
    public WorldLoadEvent(World world) {
        super();
        this.world = world;
    }

    /**
     * Returns the world.
     *
     * @return the world
     */
    public World world() {
        return world;
    }
}
