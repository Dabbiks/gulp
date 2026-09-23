package dev.gulp.api.ui;

import dev.gulp.api.registry.Keyed;

/**
 * UI theme: styles for every widget. Registered in {@code Registries}; the full definition arrives in roadmap stage 9.
 *
 * <pre>{@code
 * Theme dark = registries().get(Registries.THEME).getOrThrow(Key.parse("gulp:dark"));
 * }</pre>
 */
public interface Theme extends Keyed {}
