package dev.gulp.api.physics;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityEvent;

/**
 * An entity stopped overlapping a {@link Trigger}, left the world or the trigger was disabled or removed. {@link
 * #entity()} is the trigger's entity.
 *
 * <pre>{@code
 * door.on(TriggerExitEvent.class, e -> closeDoor());
 * }</pre>
 */
public final class TriggerExitEvent extends EntityEvent {

    private final Trigger trigger;
    private final Entity other;

    /**
     * Creates the event.
     *
     * @param trigger the trigger
     * @param other the entity that left
     */
    public TriggerExitEvent(Trigger trigger, Entity other) {
        super(trigger.entity());
        this.trigger = trigger;
        this.other = other;
    }

    /**
     * Returns the trigger.
     *
     * @return the trigger
     */
    public Trigger trigger() {
        return trigger;
    }

    /**
     * Returns the entity that left.
     *
     * @return the entity
     */
    public Entity other() {
        return other;
    }
}
