package dev.gulp.api.entity;

import dev.gulp.api.event.Cancellable;
import dev.gulp.api.world.Location;

/**
 * An entity is about to teleport. Cancelling it keeps the entity where it is.
 *
 * <pre>{@code
 * player.on(EntityTeleportEvent.class, e -> camera.shake(0.3f, 0.2f));
 * }</pre>
 */
public final class EntityTeleportEvent extends EntityEvent implements Cancellable {

    private final Location from;
    private final Location to;

    /**
     * Creates the event.
     *
     * @param entity the entity
     * @param from where the entity is
     * @param to where it goes
     */
    public EntityTeleportEvent(Entity entity, Location from, Location to) {
        super(entity);
        this.from = from;
        this.to = to;
    }

    /**
     * Returns where the entity is.
     *
     * @return where the entity is
     */
    public Location from() {
        return from;
    }

    /**
     * Returns where it goes.
     *
     * @return where it goes
     */
    public Location to() {
        return to;
    }

    private boolean cancelled;

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }
}
