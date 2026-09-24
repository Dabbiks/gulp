package dev.gulp.api.entity;

import dev.gulp.api.event.Cancellable;

/**
 * An entity with {@link dev.gulp.api.entity.component.Health} is about to heal. Handlers may change the amount or
 * cancel it.
 *
 * <pre>{@code
 * on(EntityHealEvent.class, e -> { if (poisoned.contains(e.entity())) e.setCancelled(true); });
 * }</pre>
 */
public final class EntityHealEvent extends EntityEvent implements Cancellable {

    private float amount;
    private boolean cancelled;

    /**
     * Creates the event.
     *
     * @param entity the entity healing
     * @param amount the health to restore
     */
    public EntityHealEvent(Entity entity, float amount) {
        super(entity);
        this.amount = amount;
    }

    /**
     * Returns the health to restore.
     *
     * @return the amount
     */
    public float amount() {
        return amount;
    }

    /**
     * Changes the health to restore.
     *
     * @param value the new amount, at least {@code 0}
     */
    public void setAmount(float value) {
        amount = Math.max(0f, value);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }
}
