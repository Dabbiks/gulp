package dev.gulp.api.entity.component;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.DamageType;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityDamageEvent;
import dev.gulp.api.entity.EntityDeathEvent;
import dev.gulp.api.entity.EntityHealEvent;
import org.jspecify.annotations.Nullable;

/**
 * Hit points with damage, healing, invulnerability after a hit and death. Damage goes through the cancellable {@link
 * EntityDamageEvent}; reaching zero fires {@link EntityDeathEvent} once.
 *
 * <pre>{@code
 * EntityType.builder(key("slime")).component(() -> new Health(10).invulnerableTicks(20)).build();
 * slime.get(Health.class).damage(3, DamageType.GENERIC, player);
 * }</pre>
 */
public final class Health extends Component {

    private float max;
    private float current;
    private int invulnerableTicks;
    private long lastHitTick = Long.MIN_VALUE / 2;

    /**
     * Creates full health.
     *
     * @param max the maximum and starting health
     */
    public Health(float max) {
        if (!(max > 0f)) {
            throw new IllegalArgumentException("Maximum health must be positive: " + max);
        }
        this.max = max;
        this.current = max;
    }

    /**
     * Returns the maximum health.
     *
     * @return the maximum
     */
    public float max() {
        return max;
    }

    /**
     * Changes the maximum health; the current health is capped to it.
     *
     * @param value the new maximum
     * @return this component
     */
    public Health setMax(float value) {
        if (!(value > 0f)) {
            throw new IllegalArgumentException("Maximum health must be positive: " + value);
        }
        max = value;
        current = Math.min(current, max);
        return this;
    }

    /**
     * Returns the current health.
     *
     * @return {@code 0..max}
     */
    public float current() {
        return current;
    }

    /**
     * Sets the current health without events, for loading or cheats.
     *
     * @param value clamped to {@code 0..max}
     * @return this component
     */
    public Health setCurrent(float value) {
        current = Math.max(0f, Math.min(max, value));
        return this;
    }

    /**
     * Returns the invulnerability time after a hit.
     *
     * @return ticks
     */
    public int invulnerableTicks() {
        return invulnerableTicks;
    }

    /**
     * Ignores further damage for a while after each hit.
     *
     * @param value ticks, {@code 0} by default
     * @return this component
     */
    public Health invulnerableTicks(int value) {
        invulnerableTicks = Math.max(0, value);
        return this;
    }

    /**
     * Returns whether damage is currently ignored after a recent hit.
     *
     * @return {@code true} while invulnerable
     */
    public boolean isInvulnerable() {
        return isAttached() && entity().isSpawned() && world().ticks() - lastHitTick < invulnerableTicks;
    }

    /**
     * Returns whether the health is zero.
     *
     * @return {@code true} when dead
     */
    public boolean isDead() {
        return current <= 0f;
    }

    /**
     * Deals damage, unless dead, invulnerable or cancelled by a handler.
     *
     * @param amount the damage
     * @param type the kind of damage
     * @param source who caused it, or {@code null}
     * @return the damage actually taken
     */
    public float damage(float amount, DamageType type, @Nullable Entity source) {
        if (isDead() || isInvulnerable() || !(amount > 0f)) {
            return 0f;
        }
        Entity self = entity();
        float dealt = amount;
        if (engine().events().hasListeners(EntityDamageEvent.class)) {
            EntityDamageEvent event = engine().events().call(new EntityDamageEvent(self, amount, type, source));
            if (event.isCancelled()) {
                return 0f;
            }
            dealt = event.amount();
        }
        dealt = Math.min(dealt, current);
        if (dealt <= 0f) {
            return 0f;
        }
        current -= dealt;
        lastHitTick = self.isSpawned() ? world().ticks() : lastHitTick;
        if (current <= 0f) {
            current = 0f;
            engine().events().call(new EntityDeathEvent(self, type, source));
        }
        return dealt;
    }

    /**
     * Restores health, unless dead or cancelled by a handler.
     *
     * @param amount the health to restore
     * @return the health actually restored
     */
    public float heal(float amount) {
        if (isDead() || !(amount > 0f)) {
            return 0f;
        }
        float healed = amount;
        if (engine().events().hasListeners(EntityHealEvent.class)) {
            EntityHealEvent event = engine().events().call(new EntityHealEvent(entity(), amount));
            if (event.isCancelled()) {
                return 0f;
            }
            healed = event.amount();
        }
        healed = Math.min(healed, max - current);
        current += healed;
        return healed;
    }

    /**
     * Brings a dead entity back with some health, without events.
     *
     * @param value the new health, capped to the maximum
     * @return this component
     */
    public Health revive(float value) {
        current = Math.max(0f, Math.min(max, value));
        lastHitTick = Long.MIN_VALUE / 2;
        return this;
    }
}
