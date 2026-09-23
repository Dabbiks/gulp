package dev.gulp.api.input;

import dev.gulp.api.registry.Keyed;

/**
 * Input action with key, mouse and gamepad bindings. Registered in {@code Registries}; the full definition arrives in roadmap stage 5.
 *
 * <pre>{@code
 * InputAction jump = registries().get(Registries.INPUT_ACTION).getOrThrow(key("jump"));
 * }</pre>
 */
public interface InputAction extends Keyed {}
