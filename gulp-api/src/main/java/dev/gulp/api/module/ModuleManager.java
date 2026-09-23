package dev.gulp.api.module;

import dev.gulp.api.Logger;
import dev.gulp.api.Owner;
import dev.gulp.api.data.Config;
import dev.gulp.api.data.DataContainer;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Manages the game's modules: lookup, state and enabling or disabling them while the game runs.
 *
 * <pre>{@code
 * modules().disable("combat");        // disables modules depending on "combat" first
 * modules().enable("combat");         // enables its dependencies first if needed
 * CombatModule combat = modules().get(CombatModule.class);
 * }</pre>
 */
public interface ModuleManager {

    /**
     * Returns a declared module by class.
     *
     * @param <M> the module type
     * @param type the module class
     * @return the module, or {@code null} if not declared
     */
    <M extends GameModule> @Nullable M get(Class<M> type);

    /**
     * Returns a declared module by id.
     *
     * @param id the module id
     * @return the module, or {@code null} if not declared
     */
    @Nullable GameModule get(String id);

    /**
     * Returns the ids of all declared modules in load order (dependencies first).
     *
     * @return the ids
     */
    List<String> ids();

    /**
     * Returns the id of a declared module.
     *
     * @param module the module
     * @return its id
     * @throws IllegalArgumentException if the module is not declared in this game
     */
    String idOf(GameModule module);

    /**
     * Returns the state of a module.
     *
     * @param id the module id
     * @return the state
     * @throws IllegalArgumentException if no such module is declared
     */
    ModuleState state(String id);

    /**
     * Returns whether a module is enabled.
     *
     * @param id the module id
     * @return {@code true} in state {@link ModuleState#ENABLED}; {@code false} also for undeclared ids
     */
    boolean isEnabled(String id);

    /**
     * Enables a module and, first, its disabled dependencies.
     *
     * @param id the module id
     * @return {@code true} if the module is enabled afterwards
     * @throws IllegalArgumentException if no such module is declared
     */
    boolean enable(String id);

    /**
     * Disables a module; modules that depend on it are disabled first.
     *
     * @param id the module id
     * @throws IllegalArgumentException if no such module is declared
     */
    void disable(String id);

    /**
     * Returns the logger of an owner.
     *
     * @param owner the game or a module
     * @return the logger named after the owner id
     */
    Logger logger(Owner owner);

    /**
     * Returns the configuration of an owner.
     *
     * @param owner the game or a module
     * @return the configuration
     */
    Config config(Owner owner);

    /**
     * Returns the persistent data of a module.
     *
     * @param module the module
     * @return its data container
     */
    DataContainer data(GameModule module);
}
