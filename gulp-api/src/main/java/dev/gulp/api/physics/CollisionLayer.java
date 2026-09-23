package dev.gulp.api.physics;

import dev.gulp.api.registry.Keyed;

/**
 * Collision layer used to filter contacts and queries. Registered in {@code Registries}; the full definition arrives in roadmap stage 7.
 *
 * <pre>{@code
 * CollisionLayer pickup = registries().get(Registries.COLLISION_LAYER).getOrThrow(key("pickup"));
 * }</pre>
 */
public interface CollisionLayer extends Keyed {}
