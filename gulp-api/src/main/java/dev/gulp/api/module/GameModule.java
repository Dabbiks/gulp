package dev.gulp.api.module;

import dev.gulp.api.Engine;
import dev.gulp.api.Gulp;
import dev.gulp.api.Logger;
import dev.gulp.api.Owner;
import dev.gulp.api.data.Config;
import dev.gulp.api.data.DataContainer;

/**
 * A separate part of a game (combat, inventory, dialogue, HUD) with its own lifecycle. A module owns everything it
 * registers: listeners, tasks, commands and services are removed automatically when it is disabled.
 *
 * <pre>{@code
 * @ModuleInfo(id = "combat", dependsOn = {"world"}, softDependsOn = {"audio_fx"})
 * public final class CombatModule extends GameModule {
 *     @Override public void onLoad() { registries().register(Registries.DAMAGE_TYPE, FIRE); }
 *
 *     @Override public void onEnable() {
 *         listen(new DamageListener());
 *         every(20, this::regenerate);
 *     }
 *
 *     @Override public void onDisable() { data().set(key("kills"), DataType.INT, kills); }
 * }
 * }</pre>
 *
 * <p>Lifecycle: {@code onLoad} runs once at startup for every declared module (registries are frozen afterwards);
 * {@code onEnable} and {@code onDisable} may run many times as the module is enabled and disabled during the game.
 */
public abstract class GameModule implements Owner {

    /** Creates a module. Engine services are not available in the constructor. */
    protected GameModule() {}

    /** Registers content types. Called once at startup, in dependency order, before registries are frozen. */
    public void onLoad() {}

    /** Starts the module: registers listeners, tasks, commands and UI. Called in dependency order. */
    public void onEnable() {}

    /** Stops the module: saves state. Registrations are removed automatically afterwards. */
    public void onDisable() {}

    /**
     * Returns the module id from {@link ModuleInfo#id()}.
     *
     * @return the id
     */
    @Override
    public final String id() {
        return engine().modules().idOf(this);
    }

    /**
     * Returns the current state.
     *
     * @return the state
     */
    public final ModuleState state() {
        return engine().modules().state(id());
    }

    /**
     * Returns whether the module is enabled.
     *
     * @return {@code true} in state {@link ModuleState#ENABLED}
     */
    @Override
    public final boolean isEnabled() {
        return Gulp.isRunning() && state() == ModuleState.ENABLED;
    }

    /**
     * Returns the running engine.
     *
     * @return the engine
     */
    @Override
    public final Engine engine() {
        return Gulp.engine();
    }

    /**
     * Returns the module logger, named after the module id.
     *
     * @return the logger
     */
    @Override
    public final Logger logger() {
        return engine().modules().logger(this);
    }

    /**
     * Returns the module configuration from {@code config/<id>.yml}, with defaults from
     * {@code assets/<game id>/config/<id>.yml}.
     *
     * @return the configuration
     */
    @Override
    public final Config config() {
        return engine().modules().config(this);
    }

    /**
     * Returns data owned by this module; it is stored with the game save (stage 10).
     *
     * @return the module data
     */
    public final DataContainer data() {
        return engine().modules().data(this);
    }

    /**
     * Returns another module, usually one this module depends on.
     *
     * <pre>{@code
     * State<Integer> coins = require(HudModule.class).coins();
     * }</pre>
     *
     * @param <M> the module type
     * @param type the module class
     * @return the enabled module
     * @throws IllegalStateException if the module is not declared or not enabled
     */
    public final <M extends GameModule> M require(Class<M> type) {
        M module = engine().modules().get(type);
        if (module == null || !module.isEnabled()) {
            throw new IllegalStateException("Module " + type.getName() + " is required by " + id() + " but is "
                    + (module == null ? "not declared" : module.state()));
        }
        return module;
    }
}
