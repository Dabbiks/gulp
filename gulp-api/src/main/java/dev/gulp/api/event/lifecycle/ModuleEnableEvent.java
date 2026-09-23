package dev.gulp.api.event.lifecycle;

import dev.gulp.api.event.Event;
import dev.gulp.api.module.GameModule;

/**
 * Fired after a module was enabled ({@code onEnable} completed).
 *
 * <pre>{@code
 * on(ModuleEnableEvent.class, e -> logger().info("Enabled " + e.moduleId()));
 * }</pre>
 */
public final class ModuleEnableEvent extends Event {

    private final String moduleId;
    private final GameModule module;

    /**
     * Creates the event; fired by the engine.
     *
     * @param moduleId the module id
     * @param module the module
     */
    public ModuleEnableEvent(String moduleId, GameModule module) {
        this.moduleId = moduleId;
        this.module = module;
    }

    /**
     * Returns the module id.
     *
     * @return the id
     */
    public String moduleId() {
        return moduleId;
    }

    /**
     * Returns the module.
     *
     * @return the module
     */
    public GameModule module() {
        return module;
    }
}
