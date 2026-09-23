package dev.gulp.api.ui;

import dev.gulp.api.registry.Keyed;

/**
 * Transition between screens or worlds, such as a fade. Registered in {@code Registries}; the full definition arrives in roadmap stage 9.
 *
 * <pre>{@code
 * Transition fade = registries().get(Registries.TRANSITION).getOrThrow(Key.parse("gulp:fade"));
 * }</pre>
 */
public interface Transition extends Keyed {}
