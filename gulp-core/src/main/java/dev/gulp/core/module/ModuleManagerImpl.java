package dev.gulp.core.module;

import dev.gulp.api.Game;
import dev.gulp.api.Logger;
import dev.gulp.api.Owner;
import dev.gulp.api.data.Config;
import dev.gulp.api.data.DataContainer;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.event.Events;
import dev.gulp.api.event.lifecycle.ModuleDisableEvent;
import dev.gulp.api.event.lifecycle.ModuleEnableEvent;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleManager;
import dev.gulp.api.module.ModuleState;
import dev.gulp.api.spi.ModuleDescriptor;
import dev.gulp.core.data.ConfigImpl;
import dev.gulp.core.data.DataContainerImpl;
import dev.gulp.platform.PlatformModules;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * {@link ModuleManager}: validates declared modules, orders them by dependencies and drives their lifecycle. A module
 * owns what it registers; {@code cleanup} removes it after {@code onDisable} or a failed {@code onEnable}.
 */
public final class ModuleManagerImpl implements ModuleManager {

    /** Per-module state. */
    private static final class Entry {
        final GameModule module;
        final ModuleDescriptor descriptor;
        final Logger logger;
        final ConfigImpl config;
        final DataContainer data = new DataContainerImpl(JsonObject.EMPTY);
        ModuleState state = ModuleState.LOADED;
        boolean enabling;
        boolean loadFailed;
        final List<String> missing = new ArrayList<>();

        Entry(GameModule module, ModuleDescriptor descriptor, Logger logger, ConfigImpl config) {
            this.module = module;
            this.descriptor = descriptor;
            this.logger = logger;
            this.config = config;
        }
    }

    private final Game game;
    private final Logger gameLogger;
    private final Config gameConfig;
    private final Logger engineLogger;
    private final Events events;
    private final Consumer<Owner> cleanup;
    private final Map<String, Entry> byId = new LinkedHashMap<>();
    private final IdentityHashMap<GameModule, Entry> byInstance = new IdentityHashMap<>();

    /**
     * Declares and validates the modules.
     *
     * @param game the game
     * @param gameLogger the game logger
     * @param gameConfig the game config
     * @param engineLogger the engine logger, for lifecycle messages
     * @param declared modules from {@code GameSettings.modules(...)}
     * @param generated source of module descriptors
     * @param loggers creates a logger for a module id
     * @param configs creates the config of a module from its id and the module
     * @param events fires enable and disable events
     * @param cleanup removes everything an owner registered
     * @throws IllegalStateException if a module has no descriptor, ids are duplicated, or dependencies form a cycle
     */
    public ModuleManagerImpl(
            Game game,
            Logger gameLogger,
            Config gameConfig,
            Logger engineLogger,
            List<GameModule> declared,
            PlatformModules generated,
            Function<String, Logger> loggers,
            BiFunction<String, GameModule, ConfigImpl> configs,
            Events events,
            Consumer<Owner> cleanup) {
        this.game = game;
        this.gameLogger = gameLogger;
        this.gameConfig = gameConfig;
        this.engineLogger = engineLogger;
        this.events = events;
        this.cleanup = cleanup;

        Map<String, Entry> declaredById = new LinkedHashMap<>();
        for (GameModule module : declared) {
            ModuleDescriptor descriptor = generated.lookup(ModuleDescriptor.class, module.getClass());
            if (descriptor == null) {
                throw new IllegalStateException("Module " + module.getClass().getName()
                        + " has no generated descriptor. Annotate it with @ModuleInfo and add gulp-processor as an"
                        + " annotation processor.");
            }
            Entry existing = declaredById.get(descriptor.id());
            if (existing != null) {
                throw new IllegalStateException("Duplicate module id '" + descriptor.id() + "': "
                        + existing.module.getClass().getName() + " and "
                        + module.getClass().getName());
            }
            Entry entry = new Entry(
                    module, descriptor, loggers.apply(descriptor.id()), configs.apply(descriptor.id(), module));
            declaredById.put(descriptor.id(), entry);
            byInstance.put(module, entry);
        }

        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (Entry entry : declaredById.values()) {
            List<String> edges = new ArrayList<>();
            for (String dependency : entry.descriptor.dependsOn()) {
                if (declaredById.containsKey(dependency)) {
                    edges.add(dependency);
                } else {
                    entry.missing.add(dependency);
                }
            }
            for (String dependency : entry.descriptor.softDependsOn()) {
                if (declaredById.containsKey(dependency)) {
                    edges.add(dependency);
                }
            }
            graph.put(entry.descriptor.id(), edges);
        }
        for (String id : ModuleGraph.sort(graph)) {
            byId.put(id, declaredById.get(id));
        }
    }

    /**
     * Returns the configs of all modules, for loading before {@code onLoad}.
     *
     * @return the configs in load order
     */
    public List<ConfigImpl> configs() {
        List<ConfigImpl> configs = new ArrayList<>();
        for (Entry entry : byId.values()) {
            configs.add(entry.config);
        }
        return configs;
    }

    /** Calls {@code onLoad} of every module in load order. Modules with missing or failed dependencies are skipped. */
    public void loadAll() {
        for (Entry entry : byId.values()) {
            if (!entry.missing.isEmpty()) {
                entry.state = ModuleState.DISABLED;
                entry.loadFailed = true;
                entry.logger.error("Missing required module(s) " + entry.missing + "; module will stay disabled");
                continue;
            }
            Entry failedDependency = failedHardDependency(entry);
            if (failedDependency != null) {
                entry.state = ModuleState.DISABLED;
                entry.loadFailed = true;
                entry.logger.error("Required module '" + failedDependency.descriptor.id()
                        + "' failed to load; module will stay disabled");
                continue;
            }
            try {
                entry.module.onLoad();
                entry.state = ModuleState.LOADED;
            } catch (Throwable error) {
                entry.state = ModuleState.FAILED;
                entry.loadFailed = true;
                entry.logger.error("onLoad failed; the game continues without this module", error);
            }
        }
    }

    private @Nullable Entry failedHardDependency(Entry entry) {
        for (String dependency : entry.descriptor.dependsOn()) {
            Entry required = byId.get(dependency);
            if (required != null && required.loadFailed) {
                return required;
            }
        }
        return null;
    }

    /** Enables every module marked {@code enabledByDefault}, in load order. */
    public void enableDefaults() {
        for (Entry entry : byId.values()) {
            if (entry.descriptor.enabledByDefault() && entry.state == ModuleState.LOADED) {
                enable(entry, new ArrayList<>());
            }
        }
    }

    /** Disables every enabled module in reverse load order. */
    public void disableAll() {
        List<Entry> entries = new ArrayList<>(byId.values());
        for (int i = entries.size() - 1; i >= 0; i--) {
            disable(entries.get(i));
        }
    }

    /**
     * Returns whether a module accepts registrations: it is enabled or being enabled.
     *
     * @param module the module
     * @return {@code true} if active
     */
    public boolean isActive(GameModule module) {
        Entry entry = byInstance.get(module);
        return entry != null && (entry.state == ModuleState.ENABLED || entry.enabling);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <M extends GameModule> @Nullable M get(Class<M> type) {
        for (Entry entry : byId.values()) {
            if (entry.module.getClass() == type) {
                return (M) entry.module;
            }
        }
        for (Entry entry : byId.values()) {
            if (type.isInstance(entry.module)) {
                return type.cast(entry.module);
            }
        }
        return null;
    }

    @Override
    public @Nullable GameModule get(String id) {
        Entry entry = byId.get(id);
        return entry == null ? null : entry.module;
    }

    @Override
    public List<String> ids() {
        return List.copyOf(byId.keySet());
    }

    @Override
    public String idOf(GameModule module) {
        Entry entry = byInstance.get(module);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "Module " + module.getClass().getName() + " is not declared in GameSettings.modules(...)");
        }
        return entry.descriptor.id();
    }

    @Override
    public ModuleState state(String id) {
        return entry(id).state;
    }

    @Override
    public boolean isEnabled(String id) {
        Entry entry = byId.get(id);
        return entry != null && entry.state == ModuleState.ENABLED;
    }

    @Override
    public boolean enable(String id) {
        return enable(entry(id), new ArrayList<>());
    }

    @Override
    public void disable(String id) {
        disable(entry(id));
    }

    private boolean enable(Entry entry, List<String> chain) {
        if (entry.state == ModuleState.ENABLED) {
            return true;
        }
        String id = entry.descriptor.id();
        if (entry.loadFailed) {
            entry.logger.warn("Cannot enable '" + id + "': it did not load");
            return false;
        }
        if (chain.contains(id)) {
            return false;
        }
        chain.add(id);
        for (String dependency : entry.descriptor.dependsOn()) {
            Entry required = byId.get(dependency);
            // A failed dependency is not retried implicitly; only enable(id) on the module itself retries it.
            if (required == null || required.state == ModuleState.FAILED || !enable(required, chain)) {
                entry.logger.warn("Cannot enable '" + id + "': required module '" + dependency + "' is not available");
                if (entry.state != ModuleState.FAILED) {
                    entry.state = ModuleState.DISABLED;
                }
                return false;
            }
        }
        entry.enabling = true;
        try {
            entry.module.onEnable();
            entry.state = ModuleState.ENABLED;
        } catch (Throwable error) {
            entry.state = ModuleState.FAILED;
            entry.logger.error("onEnable failed; module disabled, the game continues", error);
            cleanup.accept(entry.module);
            return false;
        } finally {
            entry.enabling = false;
        }
        engineLogger.debug("Enabled module " + id);
        events.call(new ModuleEnableEvent(id, entry.module));
        return true;
    }

    private void disable(Entry entry) {
        if (entry.state != ModuleState.ENABLED) {
            return;
        }
        String id = entry.descriptor.id();
        List<Entry> entries = new ArrayList<>(byId.values());
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry dependent = entries.get(i);
            if (dependent.state == ModuleState.ENABLED
                    && dependent.descriptor.dependsOn().contains(id)) {
                disable(dependent);
            }
        }
        try {
            entry.module.onDisable();
        } catch (Throwable error) {
            entry.logger.error("onDisable failed", error);
        }
        cleanup.accept(entry.module);
        entry.state = ModuleState.DISABLED;
        engineLogger.debug("Disabled module " + id);
        events.call(new ModuleDisableEvent(id, entry.module));
    }

    private Entry entry(String id) {
        Entry entry = byId.get(id);
        if (entry == null) {
            throw new IllegalArgumentException("No module '" + id + "' is declared; known: " + byId.keySet());
        }
        return entry;
    }

    @Override
    public Logger logger(Owner owner) {
        if (owner == game) {
            return gameLogger;
        }
        if (owner instanceof GameModule module) {
            Entry entry = byInstance.get(module);
            if (entry != null) {
                return entry.logger;
            }
        }
        return engineLogger;
    }

    @Override
    public Config config(Owner owner) {
        if (owner == game) {
            return gameConfig;
        }
        if (owner instanceof GameModule module) {
            Entry entry = byInstance.get(module);
            if (entry != null) {
                return entry.config;
            }
        }
        throw new IllegalArgumentException("No config for " + owner.getClass().getName());
    }

    @Override
    public DataContainer data(GameModule module) {
        Entry entry = byInstance.get(module);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "Module " + module.getClass().getName() + " is not declared in GameSettings.modules(...)");
        }
        return entry.data;
    }
}
