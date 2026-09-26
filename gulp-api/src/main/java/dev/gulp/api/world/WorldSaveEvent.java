package dev.gulp.api.world;

import dev.gulp.api.event.Event;

/**
 * A persistent world is about to be written into a save; the last moment to put values into {@code world.data()}.
 *
 * <pre>{@code
 * on(WorldSaveEvent.class, e -> e.world().data().set(key("time"), DataType.LONG, dayTime));
 * }</pre>
 */
public final class WorldSaveEvent extends Event {

    private final World world;

    /**
     * Creates the event.
     *
     * @param world the world
     */
    public WorldSaveEvent(World world) {
        this.world = world;
    }

    /**
     * Returns the world.
     *
     * @return the world being saved
     */
    public World world() {
        return world;
    }
}
