package dev.gulp.api.entity;

import dev.gulp.api.event.Cancellable;

/**
 * An entity is about to enter its world. Cancelling it discards the entity.
 *
 * <pre>{@code
 * on(EntitySpawnEvent.class, e -> {
 *     if (e.entity().tags().has("enemy") && peaceful) { e.setCancelled(true); }
 * });
 * }</pre>
 */
public final class EntitySpawnEvent extends EntityEvent implements Cancellable {

    /**
     * Creates the event.
     *
     * @param entity the entity, configured but not yet in the world
     */
    public EntitySpawnEvent(Entity entity) {
        super(entity);
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
