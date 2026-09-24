package dev.gulp.api.physics;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.GridPos;
import dev.gulp.api.math.Vec2;
import org.jspecify.annotations.Nullable;

/**
 * A touch between a moving entity and something solid: another entity or a collision tile.
 *
 * <pre>{@code
 * for (Contact contact : mover.moveAndSlide(velocity)) {
 *     if (contact.entity() != null && contact.entity().tags().has("spikes")) { health.damage(1, SPIKES, null); }
 * }
 * }</pre>
 *
 * @param entity the other entity, or {@code null} for a tile
 * @param tile the tile, or {@code null} for an entity
 * @param point the contact point in world units
 * @param normal unit normal pointing from the other thing towards the moving entity
 */
public record Contact(@Nullable Entity entity, @Nullable GridPos tile, Vec2 point, Vec2 normal) {}
