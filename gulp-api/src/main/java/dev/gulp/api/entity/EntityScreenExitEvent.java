package dev.gulp.api.entity;

/**
 * An entity left the view of every camera of the active world.
 *
 * <pre>{@code
 * bullet.on(EntityScreenExitEvent.class, e -> e.entity().remove());
 * }</pre>
 */
public final class EntityScreenExitEvent extends EntityEvent {

    /**
     * Creates the event.
     *
     * @param entity the entity
     */
    public EntityScreenExitEvent(Entity entity) {
        super(entity);
    }
}
