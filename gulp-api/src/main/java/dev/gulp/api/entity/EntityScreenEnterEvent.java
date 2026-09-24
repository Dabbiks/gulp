package dev.gulp.api.entity;

/**
 * An entity came into the view of a camera of the active world.
 *
 * <pre>{@code
 * boss.on(EntityScreenEnterEvent.class, e -> audio().music().crossfadeTo(bossTheme, 1f));
 * }</pre>
 */
public final class EntityScreenEnterEvent extends EntityEvent {

    /**
     * Creates the event.
     *
     * @param entity the entity
     */
    public EntityScreenEnterEvent(Entity entity) {
        super(entity);
    }
}
