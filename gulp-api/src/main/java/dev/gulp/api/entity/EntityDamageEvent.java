package dev.gulp.api.entity;

import dev.gulp.api.event.Cancellable;
import org.jspecify.annotations.Nullable;

/**
 * An entity with {@link dev.gulp.api.entity.component.Health} is about to take damage. Handlers may change the amount
 * or cancel the damage.
 *
 * <pre>{@code
 * on(EntityDamageEvent.class, EventPriority.HIGH, e -> {
 *     if (e.type().equals(FIRE) && e.entity().tags().has("fireproof")) { e.setCancelled(true); }
 *     else if (e.entity().tags().has("armored")) { e.setAmount(e.amount() / 2); }
 * });
 * }</pre>
 */
public final class EntityDamageEvent extends EntityEvent implements Cancellable {

    private float amount;
    private final DamageType type;
    private final @Nullable Entity source;
    private boolean cancelled;

    /**
     * Creates the event.
     *
     * @param entity the entity taking damage
     * @param amount the damage
     * @param type the kind of damage
     * @param source who caused it, or {@code null}
     */
    public EntityDamageEvent(Entity entity, float amount, DamageType type, @Nullable Entity source) {
        super(entity);
        this.amount = amount;
        this.type = type;
        this.source = source;
    }

    /**
     * Returns the damage.
     *
     * @return the amount
     */
    public float amount() {
        return amount;
    }

    /**
     * Changes the damage.
     *
     * @param value the new amount, at least {@code 0}
     */
    public void setAmount(float value) {
        amount = Math.max(0f, value);
    }

    /**
     * Returns the kind of damage.
     *
     * @return the type
     */
    public DamageType type() {
        return type;
    }

    /**
     * Returns who caused the damage.
     *
     * @return the entity, or {@code null}
     */
    public @Nullable Entity source() {
        return source;
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
