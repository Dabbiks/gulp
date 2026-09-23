package dev.gulp.api.entity;

import dev.gulp.api.registry.Keyed;

/**
 * Type of entity: size, components and tags. Registered in {@code Registries}; the full definition arrives in roadmap stage 6.
 *
 * <pre>{@code
 * EntityType player = registries().get(Registries.ENTITY_TYPE).getOrThrow(key("player"));
 * }</pre>
 */
public interface EntityType extends Keyed {}
