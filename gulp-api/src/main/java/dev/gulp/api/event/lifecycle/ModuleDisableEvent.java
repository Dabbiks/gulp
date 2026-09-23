package dev.gulp.api.event.lifecycle;

import dev.gulp.api.event.Event;
import dev.gulp.api.module.GameModule;

/**
 * Fired after a module was disabled and its registrations were removed.
 *
 * <pre>{@code
 * on(ModuleDisableEvent.class, e -> hud.hideSection(e.moduleId()));
 * }</pre>
 */
public final class ModuleDisableEvent extends Event {

    private final String moduleId;
    private final GameModule module;

    /**
     * Creates the event; fired by the engine.
     *
     * @param moduleId the module id
     * @param module the module
     */
    public ModuleDisableEvent(String moduleId, GameModule module) {
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
