package dev.gulp.api.world;

import dev.gulp.api.event.Event;

/**
 * A world is about to unload; its entities are still there.
 *
 * <pre>{@code
 * on(WorldUnloadEvent.class, e -> saveProgress(e.world()));
 * }</pre>
 */
public final class WorldUnloadEvent extends Event {

    private final World world;

    /**
     * Creates the event.
     *
     * @param world the world
     */
    public WorldUnloadEvent(World world) {
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
