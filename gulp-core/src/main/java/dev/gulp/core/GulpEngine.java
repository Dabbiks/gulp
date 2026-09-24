package dev.gulp.core;

import dev.gulp.api.Engine;
import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.LogLevel;
import dev.gulp.api.Logger;
import dev.gulp.api.Owner;
import dev.gulp.api.Platform;
import dev.gulp.api.asset.AssetGroup;
import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.asset.Assets;
import dev.gulp.api.audio.Audio;
import dev.gulp.api.audio.AudioClip;
import dev.gulp.api.command.Commands;
import dev.gulp.api.data.Config;
import dev.gulp.api.data.Preferences;
import dev.gulp.api.event.Events;
import dev.gulp.api.event.lifecycle.FocusGainedEvent;
import dev.gulp.api.event.lifecycle.FocusLostEvent;
import dev.gulp.api.event.lifecycle.GameStartEvent;
import dev.gulp.api.event.lifecycle.GameStopEvent;
import dev.gulp.api.event.lifecycle.PauseEvent;
import dev.gulp.api.event.lifecycle.ResumeEvent;
import dev.gulp.api.event.lifecycle.TickEndEvent;
import dev.gulp.api.event.lifecycle.TickStartEvent;
import dev.gulp.api.event.lifecycle.WindowResizeEvent;
import dev.gulp.api.graphics.Graphics;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.i18n.Translations;
import dev.gulp.api.input.Input;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleManager;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.render.Display;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.api.scheduler.Scheduler;
import dev.gulp.api.service.Services;
import dev.gulp.api.spi.EngineBinding;
import dev.gulp.api.text.Font;
import dev.gulp.api.text.FontFamily;
import dev.gulp.api.ui.Transitions;
import dev.gulp.api.world.Worlds;
import dev.gulp.core.asset.AssetsImpl;
import dev.gulp.core.audio.AudioImpl;
import dev.gulp.core.command.BuiltinCommands;
import dev.gulp.core.command.CommandsImpl;
import dev.gulp.core.command.ConsoleImpl;
import dev.gulp.core.data.ConfigImpl;
import dev.gulp.core.data.PreferencesImpl;
import dev.gulp.core.event.EventBus;
import dev.gulp.core.graphics.DisplayImpl;
import dev.gulp.core.graphics.GraphicsImpl;
import dev.gulp.core.graphics.Renderer;
import dev.gulp.core.i18n.TranslationsImpl;
import dev.gulp.core.input.InputImpl;
import dev.gulp.core.log.LoggerImpl;
import dev.gulp.core.module.ModuleManagerImpl;
import dev.gulp.core.registry.RegistriesImpl;
import dev.gulp.core.scheduler.PromiseImpl;
import dev.gulp.core.scheduler.SchedulerImpl;
import dev.gulp.core.service.ServicesImpl;
import dev.gulp.core.text.TextSystem;
import dev.gulp.core.world.WorldsImpl;
import dev.gulp.platform.FrameHandler;
import dev.gulp.platform.PlatformBackend;
import dev.gulp.platform.PlatformModules;
import dev.gulp.platform.PlatformWindow;
import dev.gulp.platform.WindowConfig;
import dev.gulp.platform.WindowListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The engine: runs one game on one platform.
 *
 * <p>Startup: {@link #start()} binds the engine, reads the configs of the game and its modules (asynchronously, so it
 * works on the web), then on the first frame after they arrive calls {@code Game.onLoad}, the modules' {@code onLoad}
 * in dependency order, freezes the registries, calls {@code Game.onStart}, enables the default modules and fires
 * {@link GameStartEvent}.
 *
 * <p>Each frame runs real-time ticks and game ticks with a fixed step: an accumulator collects frame time (scaled by
 * the time scale for game ticks), at most {@value #MAX_CATCH_UP_TICKS} ticks run per frame, and the remainder gives the
 * interpolation {@link #alpha()} for rendering.
 *
 * <pre>{@code
 * GameSettings settings = GulpEngine.configure(game);
 * PlatformBackend backend = DesktopBackend.create(game.id(), GulpEngine.windowConfig(settings));
 * GulpEngine engine = new GulpEngine(game, settings, backend);
 * engine.start();            // blocks on desktop until the game stops
 * }</pre>
 */
public final class GulpEngine implements Engine, FrameHandler, CoreContext {

    /** Maximum game ticks run in one frame; the rest of the backlog is dropped. */
    public static final int MAX_CATCH_UP_TICKS = 5;

    /** Key of the built-in font. */
    public static final String DEFAULT_FONT = "gulp:fonts/default.msdf.json";

    /** Frame time longer than this (a debugger pause, a hidden tab) is clamped. */
    private static final long MAX_FRAME_NANOS = 250_000_000L;

    private enum Phase {
        CREATED,
        LOADING,
        RUNNING,
        STOPPED
    }

    private final Game game;
    private final GameSettings settings;
    private final PlatformBackend backend;
    private final boolean development;
    private final LogLevel logLevel;
    private final Map<String, Logger> loggers = new HashMap<>();
    private final Logger engineLogger;
    private final Owner engineOwner;
    private final MainQueue mainQueue = new MainQueue();
    private final PlatformModules generated;
    private final EventBus events;
    private final SchedulerImpl scheduler;
    private final RegistriesImpl registries;
    private final ServicesImpl services;
    private final CommandsImpl commands;
    private final ConsoleImpl console;
    private final ConfigImpl gameConfig;
    private final ModuleManagerImpl modules;
    private final Platform platform;
    private final GraphicsImpl graphics;
    private final DisplayImpl display;
    private final Renderer renderer;
    private final AssetsImpl assets;
    private final TranslationsImpl translations;
    private final TextSystem textSystem;
    private final PreferencesImpl preferences;
    private final InputImpl input;
    private final AudioImpl audio;
    private final WorldsImpl worlds;
    private final long tickNanos;

    private @Nullable Thread mainThread;
    private Phase phase = Phase.CREATED;
    private boolean gameLoaded;
    private boolean stopRequested;
    private @Nullable Throwable failure;
    private int pendingConfigs;
    private @Nullable Promise<Void> startup;
    private @Nullable Throwable startupError;
    private long firstFrameNanos;
    private long lastAudioNanos;

    private boolean paused;
    private float timeScale = 1f;
    private long lastFrameNanos;
    private long accumulator;
    private long realAccumulator;
    private long tick;
    private long realTick;
    private long frameCount;
    private float alpha;
    private long tpsWindowStart;
    private int tpsTicks;
    private float measuredTps;

    /**
     * Creates the engine and validates the game: id and module declarations. Nothing runs until {@link #start()}.
     *
     * @param game the game
     * @param settings the settings returned by {@link #configure(Game)}
     * @param backend the initialised platform
     * @throws IllegalStateException if the game id is invalid, a module lacks its generated descriptor, module ids
     *     repeat, or module dependencies form a cycle
     */
    public GulpEngine(Game game, GameSettings settings, PlatformBackend backend) {
        this.game = game;
        this.settings = settings;
        this.backend = backend;
        this.development = backend.info().isDevelopment();
        this.logLevel = LoggerImpl.configuredLevel();
        this.engineLogger = logger(Key.RESERVED);
        this.tickNanos = 1_000_000_000L / settings.ticksPerSecond();
        this.measuredTps = settings.ticksPerSecond();

        String id = game.id();
        if (!Key.isValidNamespace(id) || Key.RESERVED.equals(id)) {
            throw new IllegalStateException(
                    "Invalid game id '" + id + "': use [a-z0-9_.-]+, and not '" + Key.RESERVED + "'");
        }
        this.engineOwner = new EngineOwner();
        this.generated = backend.modules();
        this.platform = new PlatformView(backend);
        this.events = new EventBus(this, generated);
        this.scheduler = new SchedulerImpl(this, game, settings::ticksPerSecond, backend.executor(), mainQueue);
        this.registries = new RegistriesImpl(this);
        this.services = new ServicesImpl(this, events);
        this.commands = new CommandsImpl(this);
        Boolean consoleChoice = settings.developerConsole();
        boolean consoleAvailable = consoleChoice != null ? consoleChoice : development;
        this.console = new ConsoleImpl(commands, backend.console(), consoleAvailable);
        commands.setConsole(console);
        this.gameConfig = config("game", game);
        this.graphics = new GraphicsImpl(backend.gl(), backend.decoders(), this, mainQueue, game, engineLogger);
        this.display = new DisplayImpl(settings, () -> new PromiseImpl<>(game, this, mainQueue));
        this.renderer = new Renderer(graphics, display, events);
        this.assets = new AssetsImpl(
                game, this, mainQueue, backend.files(), graphics, backend.decoders(), events, engineLogger);
        String startLocale = settings.locale() != null
                ? settings.locale()
                : GameSettings.normalizeLocale(backend.info().systemLocale());
        this.translations = new TranslationsImpl(
                assets,
                List.of(id, Key.RESERVED),
                startLocale,
                settings.defaultLocale(),
                events,
                engineLogger,
                () -> new PromiseImpl<>(game, this, mainQueue));
        this.textSystem = new TextSystem(
                new TextSystem.Resolver() {
                    @Override
                    public @Nullable Font font(String key) {
                        return assets.getIfLoaded(AssetKey.font(key));
                    }

                    @Override
                    public @Nullable TextureRegion region(String key) {
                        return assets.findRegion(key);
                    }

                    @Override
                    public String translate(String key, Object[] arguments) {
                        return translations.tr(key, arguments);
                    }

                    @Override
                    public int translationRevision() {
                        return translations.revision();
                    }
                },
                engineLogger::warn);
        graphics.setTextSystem(textSystem);
        renderer.draw().setTextSystem(textSystem);
        assets.setReloadListener(textSystem::invalidate);
        assets.setPackFolders(development && !backend.info().isWeb());
        this.preferences = new PreferencesImpl(backend.files(), engineLogger);
        this.input = new InputImpl(
                backend.input(),
                backend.window(),
                events,
                preferences,
                graphics::readRegion,
                () -> new PromiseImpl<>(game, this, mainQueue));
        this.audio = new AudioImpl(
                backend.audio(),
                assets,
                events,
                preferences,
                () -> display.camera().position(),
                engineLogger);
        audio.registerLoaders(backend.decoders());
        input.setPointMapper(display);
        this.worlds = new WorldsImpl(
                this,
                this,
                events,
                scheduler,
                assets,
                audio,
                display,
                input,
                backend.executor(),
                mainQueue,
                engineLogger,
                () -> new PromiseImpl<>(game, this, mainQueue));
        renderer.setWorldView(worlds);
        // Size and camera bounds are valid before the first frame, so onLoad and onEnable can use them.
        PlatformWindow window = backend.window();
        display.update(
                Math.max(1, window.framebufferWidth()), Math.max(1, window.framebufferHeight()), window.contentScale());
        this.modules = new ModuleManagerImpl(
                game,
                logger(id),
                gameConfig,
                engineLogger,
                settings.modules(),
                generated,
                this::logger,
                (moduleId, module) -> config(moduleId, module),
                events,
                this::cleanup);
    }

    /**
     * Calls {@link Game#configure(GameSettings)} on fresh default settings.
     *
     * @param game the game
     * @return the settings chosen by the game
     */
    public static GameSettings configure(Game game) {
        GameSettings settings = new GameSettings();
        game.configure(settings);
        return settings;
    }

    /**
     * Derives the platform window parameters from game settings.
     *
     * @param settings the game settings
     * @return the window configuration
     */
    public static WindowConfig windowConfig(GameSettings settings) {
        return new WindowConfig(
                settings.title(),
                settings.windowWidth(),
                settings.windowHeight(),
                settings.isResizable(),
                settings.isFullscreen(),
                settings.isVsync());
    }

    private ConfigImpl config(String name, Owner owner) {
        return new ConfigImpl(
                name,
                game.id() + "/config/" + name + ".yml",
                "config/" + name + ".yml",
                backend.files(),
                this,
                events,
                owner,
                mainQueue);
    }

    private Logger logger(String name) {
        return loggers.computeIfAbsent(name, n -> new LoggerImpl(n, backend.log(), logLevel));
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Binds the engine, starts loading configs and hands frames to the platform loop. Blocks until the game stops on
     * desktop; returns at once on the web and in headless mode.
     *
     * @throws IllegalStateException if called twice or another engine runs
     */
    public void start() {
        if (phase != Phase.CREATED) {
            throw new IllegalStateException("Engine already started");
        }
        EngineBinding.bind(this);
        mainThread = Thread.currentThread();
        phase = Phase.LOADING;
        BuiltinCommands.register(this, commands, engineOwner);
        worlds.start(engineOwner);
        backend.window().setListener(new EngineWindowListener());
        if (console.isAvailable()) {
            backend.console().setInputListener(console::submit);
        }
        List<ConfigImpl> configs = new ArrayList<>();
        configs.add(gameConfig);
        configs.addAll(modules.configs());
        pendingConfigs = configs.size() + 3;
        for (ConfigImpl config : configs) {
            config.load(() -> pendingConfigs--);
        }
        assets.loadManifest(() -> pendingConfigs--);
        assets.loadResourcePacks(() -> pendingConfigs--);
        translations.load(() -> pendingConfigs--);
        pendingConfigs++;
        preferences.load(() -> {
            audio.loadSettings();
            pendingConfigs--;
        });
        // The built-in font loads with the configs; without it (tests without assets) text is skipped with a warning.
        pendingConfigs++;
        assets.load(AssetKey.font(DEFAULT_FONT))
                .thenSync(font -> {
                    textSystem.setDefaultFamily(FontFamily.of(font));
                    pendingConfigs--;
                })
                .onFailure(error -> {
                    engineLogger.warn(
                            "The default font is not available; text will not be drawn: " + error.getMessage());
                    pendingConfigs--;
                });
        if (development) {
            backend.files().watchAssets(this::assetChanged);
        }
        engineLogger.info("Starting " + game.id() + " on " + backend.name() + " with "
                + modules.ids().size() + " module(s)");
        backend.loop().run(this);
    }

    @Override
    public boolean frame(long nanoTime) {
        frameCount++;
        mainQueue.drain(error -> engineLogger.error("Unhandled exception in main-thread work", error));
        input.frame(nanoTime);
        if (phase == Phase.LOADING && pendingConfigs == 0 && !stopRequested) {
            try {
                if (!gameLoaded) {
                    loadGame();
                }
                Promise<Void> startupAssets = startup;
                if (startupAssets != null && startupAssets.isFailed()) {
                    Throwable error = startupError;
                    if (error != null) {
                        throw new IllegalStateException("The startup assets failed to load", error);
                    }
                } else if (startupAssets != null && startupAssets.isDone()) {
                    startGame(nanoTime);
                }
            } catch (Throwable error) {
                failure = error;
                stopRequested = true;
                engineLogger.error("The game failed to start", error);
                return false;
            }
        }
        if (phase == Phase.RUNNING && !stopRequested) {
            advance(nanoTime);
        }
        float audioSeconds = lastAudioNanos == 0L ? 0f : (nanoTime - lastAudioNanos) / 1e9f;
        lastAudioNanos = nanoTime;
        float frameSeconds = Math.max(0f, Math.min(audioSeconds, 0.25f));
        audio.update(frameSeconds, paused);
        if (phase == Phase.RUNNING) {
            worlds.frame(frameSeconds, alpha());
        }
        preferences.update(nanoTime);
        render(nanoTime);
        return !stopRequested && !backend.window().shouldClose();
    }

    private void loadGame() {
        gameLoaded = true;
        registries.asEngine(() -> {
            registries.register(Registries.TRANSITION, Transitions.fade(0.5f));
            registries.register(Registries.TRANSITION, Transitions.slide(dev.gulp.api.math.Vec2.LEFT, 0.5f));
            registries.register(Registries.TRANSITION, Transitions.circleWipe(0.6f));
            registries.register(Registries.TRANSITION, Transitions.pixelate(0.5f));
        });
        game.onLoad();
        modules.loadAll();
        registries.freeze();
        input.registerActions(registries.get(Registries.INPUT_ACTION).values());
        AssetGroup startupGroup = assets.startup();
        for (AssetKey<AudioClip> file :
                AudioImpl.filesOf(registries.get(Registries.SOUND).values())) {
            startupGroup.add(file);
        }
        // onStart waits for the startup group; the renderer shows the loading screen meanwhile.
        Promise<Void> startupAssets = assets.loadGroup(Assets.STARTUP);
        startupAssets.onFailure(error -> startupError = error);
        startup = startupAssets;
    }

    private void startGame(long nanoTime) {
        startup = null;
        game.onStart();
        phase = Phase.RUNNING;
        input.setRunning(true);
        modules.enableDefaults();
        lastFrameNanos = nanoTime;
        tpsWindowStart = nanoTime;
        events.call(new GameStartEvent());
    }

    private void advance(long nanoTime) {
        long delta = nanoTime - lastFrameNanos;
        lastFrameNanos = nanoTime;
        if (delta < 0) {
            delta = 0;
        } else if (delta > MAX_FRAME_NANOS) {
            delta = MAX_FRAME_NANOS;
        }

        realAccumulator += delta;
        int ran = 0;
        while (realAccumulator >= tickNanos && ran < MAX_CATCH_UP_TICKS) {
            realAccumulator -= tickNanos;
            realTick++;
            if (paused) {
                input.tick();
                worlds.tick(true);
            }
            scheduler.tickRealtime();
            ran++;
        }
        if (realAccumulator >= tickNanos) {
            realAccumulator %= tickNanos;
        }

        if (!paused) {
            accumulator += (long) (delta * (double) timeScale);
            ran = 0;
            while (accumulator >= tickNanos && ran < MAX_CATCH_UP_TICKS && !stopRequested && !paused) {
                accumulator -= tickNanos;
                gameTick();
                ran++;
            }
            if (accumulator >= tickNanos) {
                accumulator %= tickNanos;
            }
        }
        alpha = accumulator / (float) tickNanos;

        long window = nanoTime - tpsWindowStart;
        if (window >= 1_000_000_000L) {
            measuredTps = tpsTicks * 1e9f / window;
            tpsTicks = 0;
            tpsWindowStart = nanoTime;
        }
    }

    private void gameTick() {
        tick++;
        tpsTicks++;
        input.tick();
        if (events.hasListeners(TickStartEvent.class)) {
            events.call(new TickStartEvent(tick));
        }
        scheduler.tickGame();
        worlds.tick(false);
        if (events.hasListeners(TickEndEvent.class)) {
            events.call(new TickEndEvent(tick));
        }
    }

    private void render(long nanoTime) {
        PlatformWindow window = backend.window();
        if (phase == Phase.LOADING && startup != null) {
            renderer.showLoading(assets.loadingScreen(), assets.startup().progress());
        } else {
            renderer.hideLoading();
        }
        if (firstFrameNanos == 0L) {
            firstFrameNanos = nanoTime;
        }
        renderer.draw().setTime((nanoTime - firstFrameNanos) / 1e9f);
        renderer.render(
                window.framebufferWidth(),
                window.framebufferHeight(),
                window.contentScale(),
                settings.clearColor(),
                alpha(),
                phase == Phase.RUNNING && !stopRequested,
                nanoTime);
    }

    @Override
    public void exit() {
        if (phase == Phase.STOPPED) {
            return;
        }
        try {
            if (phase == Phase.RUNNING) {
                events.call(new GameStopEvent());
            }
            modules.disableAll();
            cleanup(game);
            if (gameLoaded) {
                try {
                    game.onStop();
                } catch (Throwable error) {
                    engineLogger.error("Game.onStop failed", error);
                }
            }
            cleanup(engineOwner);
        } finally {
            input.setRunning(false);
            input.dispose();
            worlds.dispose();
            audio.dispose();
            preferences.flush();
            assets.clear();
            try {
                renderer.dispose();
                graphics.disposeAll();
            } catch (Throwable error) {
                engineLogger.error("Freeing GPU resources failed", error);
            }
            phase = Phase.STOPPED;
            backend.window().setListener(null);
            backend.console().setInputListener(null);
            backend.files().watchAssets(null);
            EngineBinding.unbind(this);
            engineLogger.info("Stopped " + game.id());
        }
    }

    /**
     * Removes every listener, task, command and service registered by an owner.
     *
     * @param owner the owner being disabled
     */
    void cleanup(Owner owner) {
        // Services first: their unregister events are still visible to every listener, including the owner's.
        services.unregisterAll(owner);
        commands.unregisterAll(owner);
        scheduler.cancelAll(owner);
        events.unregisterAll(owner);
    }

    // ------------------------------------------------------------------ CoreContext

    @Override
    public boolean acceptsRegistrations(Owner owner) {
        if (phase == Phase.STOPPED || phase == Phase.CREATED) {
            return false;
        }
        if (owner == game || owner == engineOwner) {
            return true;
        }
        if (owner instanceof ScopedOwner scoped) {
            return scoped.isEnabled();
        }
        return owner instanceof GameModule module && modules.isActive(module);
    }

    @Override
    public Logger loggerOf(Owner owner) {
        if (owner instanceof ScopedOwner) {
            return engineLogger;
        }
        return owner == engineOwner ? engineLogger : modules.logger(owner);
    }

    @Override
    public void checkMainThread(String method) {
        Thread main = mainThread;
        if (development && main != null && Thread.currentThread() != main) {
            throw new IllegalStateException(method + " must be called on the main game thread, but was called from"
                    + " thread '" + Thread.currentThread().getName() + "'. Use scheduler().run(...) to get back to the"
                    + " main thread.");
        }
    }

    // ------------------------------------------------------------------ Engine

    @Override
    public Game game() {
        return game;
    }

    @Override
    public ModuleManager modules() {
        return modules;
    }

    @Override
    public Events events() {
        return events;
    }

    @Override
    public Scheduler scheduler() {
        return scheduler.api();
    }

    @Override
    public Registries registries() {
        return registries;
    }

    @Override
    public Services services() {
        return services;
    }

    @Override
    public Commands commands() {
        return commands;
    }

    @Override
    public Graphics graphics() {
        return graphics;
    }

    @Override
    public Display display() {
        return display;
    }

    @Override
    public Assets assets() {
        return assets;
    }

    @Override
    public Translations translations() {
        return translations;
    }

    @Override
    public Input input() {
        return input;
    }

    @Override
    public Audio audio() {
        return audio;
    }

    @Override
    public Preferences preferences() {
        return preferences;
    }

    @Override
    public Worlds worlds() {
        return worlds;
    }

    /** Hot reload: assets, configs and translations whose files changed. */
    private void assetChanged(String path) {
        assets.fileChanged(path);
        if (path.contains("/lang/")) {
            translations.reload();
        }
        for (ConfigImpl config : allConfigs()) {
            if (path.equals(config.assetPath())) {
                config.reload();
                engineLogger.info("Reloaded config " + config.name());
            }
        }
    }

    @Override
    public Platform platform() {
        return platform;
    }

    @Override
    public Logger logger() {
        return engineLogger;
    }

    @Override
    public long tick() {
        return tick;
    }

    @Override
    public float tps() {
        return measuredTps;
    }

    @Override
    public int targetTps() {
        return settings.ticksPerSecond();
    }

    @Override
    public float timeScale() {
        return timeScale;
    }

    @Override
    public void setTimeScale(float scale) {
        if (!(scale >= 0f && scale <= 10f)) {
            throw new IllegalArgumentException("Time scale must be between 0 and 10, got " + scale);
        }
        timeScale = scale;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public void pause() {
        checkMainThread("Engine.pause");
        if (!paused) {
            paused = true;
            events.call(new PauseEvent());
        }
    }

    @Override
    public void resume() {
        checkMainThread("Engine.resume");
        if (paused) {
            paused = false;
            events.call(new ResumeEvent());
        }
    }

    @Override
    public void stop() {
        stopRequested = true;
    }

    // ------------------------------------------------------------------ engine internals

    /**
     * Returns how far rendering is between the last two game ticks.
     *
     * @return interpolation factor in {@code [0, 1)}
     */
    public float alpha() {
        return alpha;
    }

    /**
     * Returns the number of real-time ticks since start.
     *
     * @return the real tick counter
     */
    public long realTick() {
        return realTick;
    }

    /**
     * Returns the number of frames rendered.
     *
     * @return the frame counter
     */
    public long frameCount() {
        return frameCount;
    }

    /**
     * Returns whether the game finished starting.
     *
     * @return {@code true} after {@link GameStartEvent} until the game stops
     */
    public boolean isRunning() {
        return phase == Phase.RUNNING;
    }

    /**
     * Returns whether the engine stopped.
     *
     * @return {@code true} after the game stopped
     */
    public boolean isStopped() {
        return phase == Phase.STOPPED;
    }

    /**
     * Returns the exception that stopped the game during startup.
     *
     * @return the failure, or {@code null}
     */
    public @Nullable Throwable failure() {
        return failure;
    }

    /**
     * Runs work on the main thread before the next frame. Thread-safe.
     *
     * @param work the work
     */
    public void post(Runnable work) {
        mainQueue.post(work);
    }

    /**
     * Returns the game config.
     *
     * @return the config
     */
    public Config gameConfig() {
        return gameConfig;
    }

    /**
     * Returns all configs: the game's first, then the modules' in load order.
     *
     * @return the configs
     */
    public List<ConfigImpl> allConfigs() {
        List<ConfigImpl> all = new ArrayList<>();
        all.add(gameConfig);
        all.addAll(modules.configs());
        return all;
    }

    /**
     * Returns the number of listeners, tasks and services owned, for tests and diagnostics.
     *
     * @param owner the owner
     * @return registered handlers plus live tasks
     */
    public int registrationsOf(Owner owner) {
        return events.countOwnedBy(owner) + scheduler.countOwnedBy(owner);
    }

    /** The engine itself as an owner of built-in commands. */
    private final class EngineOwner implements Owner {
        @Override
        public String id() {
            return Key.RESERVED;
        }

        @Override
        public boolean isEnabled() {
            return phase == Phase.LOADING || phase == Phase.RUNNING;
        }

        @Override
        public Logger logger() {
            return engineLogger;
        }

        @Override
        public Config config() {
            return gameConfig;
        }

        @Override
        public dev.gulp.api.Engine engine() {
            return GulpEngine.this;
        }
    }

    /** Turns window changes into lifecycle events. */
    private final class EngineWindowListener implements WindowListener {
        @Override
        public void resized(int width, int height, int framebufferWidth, int framebufferHeight) {
            if (phase == Phase.RUNNING) {
                events.call(new WindowResizeEvent(width, height));
            }
        }

        @Override
        public void focusChanged(boolean focused) {
            if (!focused) {
                input.releaseAll();
            }
            if (phase == Phase.RUNNING) {
                events.call(focused ? new FocusGainedEvent() : new FocusLostEvent());
            }
        }

        @Override
        public void contentScaleChanged(float scale) {}

        @Override
        public void closeRequested() {}
    }
}
