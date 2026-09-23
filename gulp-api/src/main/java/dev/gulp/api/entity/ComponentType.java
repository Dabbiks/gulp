package dev.gulp.api.entity;

import dev.gulp.api.registry.Keyed;

/**
 * Type of component, used for queries and serialisation. Registered in {@code Registries}; the full definition arrives in roadmap stage 6.
 *
 * <pre>{@code
 * ComponentType mover = registries().get(Registries.COMPONENT_TYPE).getOrThrow(Key.parse("gulp:mover"));
 * }</pre>
 */
public interface ComponentType extends Keyed {}
