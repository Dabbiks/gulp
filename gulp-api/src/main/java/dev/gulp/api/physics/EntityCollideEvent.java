package dev.gulp.api.physics;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityEvent;
import dev.gulp.api.math.GridPos;
import dev.gulp.api.math.Vec2;
import org.jspecify.annotations.Nullable;

/**
 * An entity started touching another entity or a collision tile, reported by movers and bodies. A touch between two
 * entities is reported to each of them.
 *
 * <pre>{@code
 * on(EntityCollideEvent.class, e -> {
 *     if (e.impulse() > 5 && e.entity().has(Health.class)) { e.entity().get(Health.class).damage(1, FALL, null); }
 * });
 * }</pre>
 */
public final class EntityCollideEvent extends EntityEvent {

    private final @Nullable Entity other;
    private final @Nullable GridPos tile;
    private final Vec2 point;
    private final Vec2 normal;
    private final float impulse;

    /**
     * Creates the event.
     *
     * @param entity the entity told about the touch
     * @param other the other entity, or {@code null} for a tile
     * @param tile the tile, or {@code null}
     * @param point the contact point
     * @param normal unit normal from the other thing towards {@code entity}
     * @param impulse the normal impulse of the first contact step; {@code 0} for movers
     */
    public EntityCollideEvent(
            Entity entity, @Nullable Entity other, @Nullable GridPos tile, Vec2 point, Vec2 normal, float impulse) {
        super(entity);
        this.other = other;
        this.tile = tile;
        this.point = point;
        this.normal = normal;
        this.impulse = impulse;
    }

    /**
     * Returns the other entity.
     *
     * @return the entity, or {@code null} for a tile
     */
    public @Nullable Entity other() {
        return other;
    }

    /**
     * Returns the tile touched.
     *
     * @return the tile, or {@code null} for an entity
     */
    public @Nullable GridPos tile() {
        return tile;
    }

    /**
     * Returns the contact point.
     *
     * @return world units
     */
    public Vec2 point() {
        return point;
    }

    /**
     * Returns the contact normal.
     *
     * @return unit normal towards {@link #entity()}
     */
    public Vec2 normal() {
        return normal;
    }

    /**
     * Returns how hard the hit was.
     *
     * @return the normal impulse; {@code 0} for movers
     */
    public float impulse() {
        return impulse;
    }
}
