package dev.gulp.api.physics;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityEvent;
import dev.gulp.api.math.Vec2;

/**
 * A mover or body touched a floor after being in the air.
 *
 * <pre>{@code
 * player.on(EntityLandEvent.class, e -> {
 *     if (e.speed() > 18) { player.get(Health.class).damage(1, FALL, null); }
 * });
 * }</pre>
 */
public final class EntityLandEvent extends EntityEvent {

    private final float speed;
    private final Vec2 normal;

    /**
     * Creates the event.
     *
     * @param entity the entity that landed
     * @param speed how fast it was falling onto the floor, units per second
     * @param normal the floor normal
     */
    public EntityLandEvent(Entity entity, float speed, Vec2 normal) {
        super(entity);
        this.speed = speed;
        this.normal = normal;
    }

    /**
     * Returns the landing speed.
     *
     * @return units per second along the floor normal
     */
    public float speed() {
        return speed;
    }

    /**
     * Returns the floor normal.
     *
     * @return a unit vector
     */
    public Vec2 normal() {
        return normal;
    }
}
