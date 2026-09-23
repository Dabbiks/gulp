package dev.gulp.api.audio;

import dev.gulp.api.registry.Keyed;

/**
 * Sound definition: file, variants, pitch range and instance limit. Registered in {@code Registries}; the full definition arrives in roadmap stage 5.
 *
 * <pre>{@code
 * Sound pickup = registries().get(Registries.SOUND).getOrThrow(key("pickup"));
 * }</pre>
 */
public interface Sound extends Keyed {}
