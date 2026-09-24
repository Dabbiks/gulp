package dev.gulp.api.physics;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.GridPos;
import dev.gulp.api.math.Vec2;
import org.jspecify.annotations.Nullable;

/**
 * Where a ray or a cast shape first touched something.
 *
 * <pre>{@code
 * RayHit hit = world.physics().raycast(eye, player.position(), CollisionMask.of(CollisionLayer.TILES));
 * boolean seesPlayer = hit == null;
 * }</pre>
 *
 * @param entity the entity hit, or {@code null} for a tile
 * @param tile the tile hit, or {@code null} for an entity
 * @param point the point of contact
 * @param normal the surface normal at that point, facing the ray
 * @param fraction how far along the ray, {@code 0} at the start and {@code 1} at the end
 */
public record RayHit(@Nullable Entity entity, @Nullable GridPos tile, Vec2 point, Vec2 normal, float fraction) {}
