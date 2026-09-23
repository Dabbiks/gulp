package dev.gulp.api.particle;

import dev.gulp.api.registry.Keyed;

/**
 * Particle effect definition. Registered in {@code Registries}; the full definition arrives in roadmap stage 8.
 *
 * <pre>{@code
 * ParticleEffect sparks = registries().get(Registries.PARTICLE_EFFECT).getOrThrow(key("sparks"));
 * }</pre>
 */
public interface ParticleEffect extends Keyed {}
