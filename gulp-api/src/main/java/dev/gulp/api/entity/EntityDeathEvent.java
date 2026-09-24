package dev.gulp.api.entity;

import org.jspecify.annotations.Nullable;

/**
 * The health of an entity reached zero. The entity stays in the world; the game decides what happens next.
 *
 * <pre>{@code
 * on(EntityDeathEvent.class, e -> {
 *     if (e.entity().tags().has("enemy")) { e.entity().remove(); score += 10; }
 * });
 * }</pre>
 */
public final class EntityDeathEvent extends EntityEvent {

    private final DamageType type;
    private final @Nullable Entity killer;

    /**
     * Creates the event.
     *
     * @param entity the entity that died
     * @param type the kind of the final damage
     * @param killer who dealt it, or {@code null}
     */
    public EntityDeathEvent(Entity entity, DamageType type, @Nullable Entity killer) {
        super(entity);
        this.type = type;
        this.killer = killer;
    }

    /**
     * Returns the kind of the final damage.
     *
     * @return the type
     */
    public DamageType type() {
        return type;
    }

    /**
     * Returns who dealt the final damage.
     *
     * @return the entity, or {@code null}
     */
    public @Nullable Entity killer() {
        return killer;
    }
}
