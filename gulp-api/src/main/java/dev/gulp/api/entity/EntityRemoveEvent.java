package dev.gulp.api.entity;

/**
 * An entity left its world, at the end of the tick in which it was removed.
 *
 * <pre>{@code
 * on(EntityRemoveEvent.class, e -> score += e.entity().tags().has("enemy") ? 10 : 0);
 * }</pre>
 */
public final class EntityRemoveEvent extends EntityEvent {

    /**
     * Creates the event.
     *
     * @param entity the entity
     */
    public EntityRemoveEvent(Entity entity) {
        super(entity);
    }
}
