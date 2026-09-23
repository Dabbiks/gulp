package dev.gulp.api.module;

/**
 * Lifecycle state of a module.
 *
 * <pre>{@code
 * if (modules().state("combat") == ModuleState.FAILED) showWarning();
 * }</pre>
 */
public enum ModuleState {
    /** {@code onLoad} completed; the module has not been enabled yet. */
    LOADED,
    /** {@code onEnable} completed; the module is running. */
    ENABLED,
    /** The module was disabled, or could not be enabled because a dependency is missing. */
    DISABLED,
    /** {@code onLoad} or {@code onEnable} threw an exception; the game keeps running without it. */
    FAILED
}
