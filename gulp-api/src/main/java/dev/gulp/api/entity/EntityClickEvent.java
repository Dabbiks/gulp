package dev.gulp.api.entity;

import dev.gulp.api.input.MouseButton;

/**
 * An entity with {@code Interactable} was clicked.
 *
 * <pre>{@code
 * chest.on(EntityClickEvent.class, e -> openChest(e.entity()));
 * }</pre>
 */
public final class EntityClickEvent extends EntityEvent {

    private final MouseButton button;
    private final float x;
    private final float y;

    /**
     * Creates the event.
     *
     * @param entity the entity
     * @param button the button
     * @param x world position of the pointer
     * @param y world position of the pointer
     */
    public EntityClickEvent(Entity entity, MouseButton button, float x, float y) {
        super(entity);
        this.button = button;
        this.x = x;
        this.y = y;
    }

    /**
     * Returns the button.
     *
     * @return the button
     */
    public MouseButton button() {
        return button;
    }

    /**
     * Returns world position of the pointer.
     *
     * @return world position of the pointer
     */
    public float x() {
        return x;
    }

    /**
     * Returns world position of the pointer.
     *
     * @return world position of the pointer
     */
    public float y() {
        return y;
    }
}
