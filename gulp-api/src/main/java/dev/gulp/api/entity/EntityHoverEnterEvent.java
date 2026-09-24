package dev.gulp.api.entity;

/**
 * The pointer moved over an entity with {@code Interactable}.
 *
 * <pre>{@code
 * on(EntityHoverEnterEvent.class, e -> input().setCursor(SystemCursor.HAND));
 * }</pre>
 */
public final class EntityHoverEnterEvent extends EntityEvent {

    /**
     * Creates the event.
     *
     * @param entity the entity
     */
    public EntityHoverEnterEvent(Entity entity) {
        super(entity);
    }
}
