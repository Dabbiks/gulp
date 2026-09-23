package dev.gulp.api.entity;

import dev.gulp.api.registry.Keyed;

/**
 * Kind of damage, such as fire or fall. Registered in {@code Registries}; the full definition arrives in roadmap stage 6.
 *
 * <pre>{@code
 * DamageType fire = registries().get(Registries.DAMAGE_TYPE).getOrThrow(key("fire"));
 * }</pre>
 */
public interface DamageType extends Keyed {}
