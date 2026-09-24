package dev.gulp.api.physics;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityEvent;

/**
 * An entity started overlapping a {@link Trigger}. {@link #entity()} is the trigger's entity.
 *
 * <pre>{@code
 * on(TriggerEnterEvent.class, e -> {
 *     if (e.entity().tags().has("coin") && e.other().tags().has("player")) { collect(e.entity()); }
 * });
 * }</pre>
 */
public final class TriggerEnterEvent extends EntityEvent {

    private final Trigger trigger;
    private final Entity other;

    /**
     * Creates the event.
     *
     * @param trigger the trigger
     * @param other the entity that entered
     */
    public TriggerEnterEvent(Trigger trigger, Entity other) {
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
     * Returns the entity that entered.
     *
     * @return the entity
     */
    public Entity other() {
        return other;
    }
}
