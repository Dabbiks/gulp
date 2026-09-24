package dev.gulp.api.entity;

/**
 * The pointer left an entity with {@code Interactable}.
 *
 * <pre>{@code
 * on(EntityHoverExitEvent.class, e -> input().setCursor(SystemCursor.ARROW));
 * }</pre>
 */
public final class EntityHoverExitEvent extends EntityEvent {

    /**
     * Creates the event.
     *
     * @param entity the entity
     */
    public EntityHoverExitEvent(Entity entity) {
        super(entity);
    }
}
