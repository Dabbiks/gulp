package dev.gulp.api.entity;

import dev.gulp.api.event.Event;
import dev.gulp.api.event.TargetedEvent;

/**
 * Base of events about one entity. Subscribe to all of them with {@code on(...)} or to one entity's with {@link
 * Entity#on}.
 *
 * <pre>{@code
 * boss.on(EntityRemoveEvent.class, e -> ui().open(new VictoryScreen()));
 * }</pre>
 */
public abstract class EntityEvent extends Event implements TargetedEvent {

    private final Entity entity;

    /**
     * Creates the event.
     *
     * @param entity the entity
     */
    protected EntityEvent(Entity entity) {
        this.entity = entity;
    }

    /**
     * Returns the entity.
     *
     * @return the entity
     */
    public final Entity entity() {
        return entity;
    }

    @Override
    public final Object target() {
        return entity;
    }
}
