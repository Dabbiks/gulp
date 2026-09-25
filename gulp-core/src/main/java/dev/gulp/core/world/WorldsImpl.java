package dev.gulp.core.world;

import dev.gulp.api.Engine;
import dev.gulp.api.Logger;
import dev.gulp.api.Owner;
import dev.gulp.api.entity.EntityClickEvent;
import dev.gulp.api.entity.EntityHoverEnterEvent;
import dev.gulp.api.entity.EntityHoverExitEvent;
import dev.gulp.api.entity.EntityScreenEnterEvent;
import dev.gulp.api.entity.EntityScreenExitEvent;
import dev.gulp.api.entity.component.Interactable;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.Cursor;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.input.MouseButtonPressEvent;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.render.RenderLayer;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.api.ui.Transition;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldLoadEvent;
import dev.gulp.api.world.WorldSettings;
import dev.gulp.api.world.WorldSource;
import dev.gulp.api.world.WorldSwitchEvent;
import dev.gulp.api.world.WorldUnloadEvent;
import dev.gulp.api.world.Worlds;
import dev.gulp.core.CoreContext;
import dev.gulp.core.MainQueue;
import dev.gulp.core.asset.AssetsImpl;
import dev.gulp.core.audio.AudioImpl;
import dev.gulp.core.event.EventBus;
import dev.gulp.core.graphics.CameraImpl;
import dev.gulp.core.graphics.DisplayImpl;
import dev.gulp.core.graphics.DrawImpl;
import dev.gulp.core.graphics.PostEffectsImpl;
import dev.gulp.core.graphics.WorldView;
import dev.gulp.core.input.InputImpl;
import dev.gulp.core.scheduler.PromiseImpl;
import dev.gulp.core.scheduler.SchedulerImpl;
import dev.gulp.platform.PlatformExecutor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * {@link Worlds}: loaded and registered worlds, the active world, transitions, ticking, camera updates, chunk streaming,
 * screen visibility and pointer interaction. It is also the renderer's {@link WorldView}.
 */
public final class WorldsImpl implements Worlds, WorldView {

    final Engine engine;
    final CoreContext context;
    final EventBus events;
    final SchedulerImpl scheduler;
    final AssetsImpl assets;
    final AudioImpl audio;
    final DisplayImpl display;
    final InputImpl input;
    final PlatformExecutor executor;
    final MainQueue mainQueue;
    final Logger logger;
    private final Supplier<PromiseImpl<World>> promises;
    private final Map<String, WorldImpl> loaded = new LinkedHashMap<>();
    private final Map<String, WorldSource> registered = new LinkedHashMap<>();
    private final Map<String, Promise<World>> loading = new LinkedHashMap<>();
    private final WorldRenderer renderer;
    private final List<float[]> clicks = new ArrayList<>();
    private @Nullable WorldImpl active;
    private int nextRuntimeId = 1;
    private boolean inTick;
    private @Nullable EntityImpl hovered;

    private @Nullable Transition transition;
    private float transitionTime;
    private boolean entering;
    private @Nullable String transitionTarget;
    private @Nullable PromiseImpl<World> transitionPromise;
    private @Nullable World transitionWorld;

    /**
     * Creates the worlds.
     *
     * @param engine the engine
     * @param context main-thread checks and owners
     * @param events the event bus
     * @param scheduler the scheduler
     * @param assets assets, for maps and images
     * @param audio audio, for sounds at positions
     * @param display the display, whose camera becomes the active world's
     * @param input input, for pointer interaction
     * @param executor runs chunk generation off the tick
     * @param mainQueue brings results back to the main thread
     * @param logger receives errors
     * @param promises creates promises completed on the main thread
     */
    public WorldsImpl(
            Engine engine,
            CoreContext context,
            EventBus events,
            SchedulerImpl scheduler,
            AssetsImpl assets,
            AudioImpl audio,
            DisplayImpl display,
            InputImpl input,
            PlatformExecutor executor,
            MainQueue mainQueue,
            Logger logger,
            Supplier<PromiseImpl<World>> promises) {
        this.engine = engine;
        this.context = context;
        this.events = events;
        this.scheduler = scheduler;
        this.assets = assets;
        this.audio = audio;
        this.display = display;
        this.input = input;
        this.executor = executor;
        this.mainQueue = mainQueue;
        this.logger = logger;
        this.promises = promises;
        this.renderer = new WorldRenderer(assets);
    }

    /**
     * Starts listening for clicks on entities.
     *
     * @param owner the engine owner
     */
    public void start(Owner owner) {
        events.on(
                MouseButtonPressEvent.class,
                dev.gulp.api.event.EventPriority.MONITOR,
                false,
                e -> {
                    if (!e.isConsumedByUi()) {
                        clicks.add(new float[] {e.button().index(), e.x(), e.y()});
                    }
                },
                owner);
    }

    int nextRuntimeId() {
        return nextRuntimeId++;
    }

    boolean inTick() {
        return inTick;
    }

    void releaseEntity(EntityImpl entity) {
        events.release(entity);
        events.unregisterAll(entity.owner);
        scheduler.cancelAll(entity.owner);
        if (hovered == entity) {
            hovered = null;
        }
    }

    // ------------------------------------------------------------------ Worlds

    @Override
    public World create(String name, WorldSettings settings) {
        context.checkMainThread("Worlds.create");
        if (loaded.containsKey(name)) {
            throw new IllegalArgumentException("A world named '" + name + "' is already loaded");
        }
        WorldImpl world = new WorldImpl(this, name, settings);
        loaded.put(name, world);
        if (events.hasListeners(WorldLoadEvent.class)) {
            events.call(new WorldLoadEvent(world));
        }
        return world;
    }

    @Override
    public Promise<World> load(String name, WorldSource source) {
        context.checkMainThread("Worlds.load");
        WorldImpl existing = loaded.get(name);
        PromiseImpl<World> promise = promises.get();
        if (existing != null) {
            promise.complete(existing);
            return promise;
        }
        Promise<World> inProgress = loading.get(name);
        if (inProgress != null) {
            return inProgress;
        }
        loading.put(name, promise);
        WorldSettings settings = source.settingsValue();
        WorldImpl world = new WorldImpl(this, name, settings);
        Promise<Void> content;
        switch (source.kind()) {
            case GENERATOR -> {
                world.tileMap.setGenerator(java.util.Objects.requireNonNull(source.generatorOrNull()));
                content = done();
            }
            case TILED -> content = MapLoaders.tiled(this, world, source);
            case LDTK -> content = MapLoaders.ldtk(this, world, source);
            default -> content = done();
        }
        content.thenSync(ignored -> {
            loading.remove(name);
            loaded.put(name, world);
            var onLoad = source.onLoadOrNull();
            if (onLoad != null) {
                onLoad.accept(world);
            }
            if (events.hasListeners(WorldLoadEvent.class)) {
                events.call(new WorldLoadEvent(world));
            }
            promise.complete(world);
        });
        content.onFailure(error -> {
            loading.remove(name);
            logger.error("World " + name + " failed to load", error);
            promise.fail(error);
        });
        return promise;
    }

    private Promise<Void> done() {
        PromiseImpl<Void> promise = assets.newPromise();
        promise.complete(null);
        return promise;
    }

    @Override
    public void register(String name, WorldSource source) {
        registered.put(name, source);
    }

    @Override
    public @Nullable World get(String name) {
        return loaded.get(name);
    }

    @Override
    public List<World> all() {
        return List.copyOf(loaded.values());
    }

    @Override
    public @Nullable World active() {
        return active;
    }

    /** A promise that failed at once; the caller sees the failure through its own handlers, without a log entry. */
    private Promise<World> failed(Throwable error) {
        PromiseImpl<World> promise = promises.get();
        promise.onFailure(ignored -> {});
        promise.fail(error);
        return promise;
    }

    private Promise<World> obtain(String name) {
        WorldImpl world = loaded.get(name);
        if (world != null) {
            PromiseImpl<World> promise = promises.get();
            promise.complete(world);
            return promise;
        }
        Promise<World> inProgress = loading.get(name);
        if (inProgress != null) {
            return inProgress;
        }
        WorldSource source = registered.get(name);
        if (source == null) {
            return failed(new IllegalArgumentException(
                    "No world named '" + name + "' is loaded or registered; known: " + registered.keySet()));
        }
        return load(name, source);
    }

    @Override
    public Promise<World> switchTo(String name) {
        context.checkMainThread("Worlds.switchTo");
        PromiseImpl<World> result = promises.get();
        obtain(name)
                .thenSync(world -> {
                    activate((WorldImpl) world);
                    result.complete(world);
                })
                .onFailure(result::fail);
        return result;
    }

    @Override
    public Promise<World> switchTo(String name, Transition value) {
        context.checkMainThread("Worlds.switchTo");
        if (transition != null) {
            return failed(new IllegalStateException("A transition is already running"));
        }
        PromiseImpl<World> result = promises.get();
        transition = value;
        transitionTime = 0f;
        entering = false;
        transitionTarget = name;
        transitionPromise = result;
        transitionWorld = null;
        obtain(name).thenSync(world -> transitionWorld = world).onFailure(error -> {
            transition = null;
            result.fail(error);
        });
        return result;
    }

    private void activate(WorldImpl world) {
        WorldImpl previous = active;
        if (previous == world) {
            return;
        }
        active = world;
        display.setWorldCamera(world.mainCamera());
        if (hovered != null) {
            hovered = null;
        }
        if (events.hasListeners(WorldSwitchEvent.class)) {
            events.call(new WorldSwitchEvent(previous, world));
        }
    }

    @Override
    public void unload(String name) {
        context.checkMainThread("Worlds.unload");
        WorldImpl world = loaded.get(name);
        if (world == null) {
            return;
        }
        if (world == active) {
            throw new IllegalStateException("The active world cannot be unloaded; switch to another world first");
        }
        if (events.hasListeners(WorldUnloadEvent.class)) {
            events.call(new WorldUnloadEvent(world));
        }
        loaded.remove(name);
        world.clear();
    }

    @Override
    public boolean isTransitioning() {
        return transition != null;
    }

    /** Unloads everything when the engine stops. */
    public void dispose() {
        active = null;
        display.setWorldCamera(null);
        for (WorldImpl world : List.copyOf(loaded.values())) {
            world.clear();
        }
        loaded.clear();
    }

    // ------------------------------------------------------------------ ticking and frames

    /**
     * Runs a game tick of the active world and of worlds that tick when inactive.
     *
     * @param paused whether the game is paused (then only entities that ignore pause tick)
     */
    public void tick(boolean paused) {
        inTick = true;
        try {
            for (WorldImpl world : List.copyOf(loaded.values())) {
                if (world == active || (!paused && world.settings().tickWhenInactive())) {
                    world.tick(paused);
                }
            }
            WorldImpl current = active;
            if (current != null) {
                updateScreen(current);
                updatePointer(current);
            }
        } finally {
            inTick = false;
            clicks.clear();
        }
    }

    /**
     * Updates cameras, parallax scrolling, chunk streaming and transitions. Called once per frame.
     *
     * @param seconds time since the last frame
     * @param alpha interpolation between ticks
     */
    public void frame(float seconds, float alpha) {
        renderer.advance(seconds);
        WorldImpl world = active;
        if (world != null) {
            float tps = engine.targetTps();
            for (CameraImpl camera : world.cameras) {
                if (camera.target() instanceof EntityImpl target) {
                    camera.update(
                            seconds,
                            target.renderX(alpha),
                            target.renderY(alpha),
                            (target.x - target.prevX) * tps,
                            (target.y - target.prevY) * tps);
                } else {
                    camera.update(seconds, 0f, 0f, 0f, 0f);
                }
            }
            world.parallax.advance(seconds);
            world.particles.advance(engine.isPaused() ? 0f : seconds * engine.timeScale(), alpha);
            world.tileMap.stream(world.cameras);
        }
        Transition running = transition;
        if (running != null) {
            transitionTime += seconds;
            float length = Math.max(0.0001f, running.duration());
            if (!entering && transitionTime >= length && transitionWorld != null) {
                activate((WorldImpl) transitionWorld);
                entering = true;
                transitionTime = 0f;
                PromiseImpl<World> promise = transitionPromise;
                if (promise != null) {
                    promise.complete(transitionWorld);
                }
            } else if (entering && transitionTime >= length) {
                transition = null;
                transitionPromise = null;
                transitionWorld = null;
                transitionTarget = null;
            }
        }
    }

    // Reused by updateScreen every tick, so the visibility pass allocates nothing.
    private final float[] screenView = new float[4];
    private @Nullable WorldImpl screenWorld;
    private boolean screenEnter;
    private final Consumer<EntityImpl> markOnScreen = this::markOnScreen;

    private void markOnScreen(EntityImpl entity) {
        WorldImpl world = screenWorld;
        if (entity.removed || world == null) {
            return;
        }
        entity.screenStamp = world.screenStamp;
        if (!entity.onScreen) {
            entity.onScreen = true;
            world.onScreen.add(entity);
            if (screenEnter) {
                events.call(new EntityScreenEnterEvent(entity));
            }
        }
    }

    private void updateScreen(WorldImpl world) {
        boolean exit = events.hasListeners(EntityScreenExitEvent.class);
        screenWorld = world;
        screenEnter = events.hasListeners(EntityScreenEnterEvent.class);
        int stamp = ++world.screenStamp;
        List<CameraImpl> cameras = world.cameras;
        for (int i = 0; i < cameras.size(); i++) {
            cameras.get(i).viewBounds(screenView);
            world.grid.query(screenView[0], screenView[1], screenView[2], screenView[3], markOnScreen);
        }
        List<EntityImpl> visible = world.onScreen;
        for (int i = visible.size() - 1; i >= 0; i--) {
            EntityImpl entity = visible.get(i);
            if (entity.screenStamp != stamp) {
                entity.onScreen = false;
                visible.remove(i);
                if (exit && !entity.removed) {
                    events.call(new EntityScreenExitEvent(entity));
                }
            }
        }
    }

    private @Nullable Vec2 pointerWorld(WorldImpl world, float windowX, float windowY) {
        Vec2 logical = display.toLogical(windowX, windowY);
        float lw = display.width();
        float lh = display.height();
        for (int i = world.cameras.size() - 1; i >= 0; i--) {
            CameraImpl camera = world.cameras.get(i);
            Rect area = camera.viewport();
            float left = area.x() * lw;
            float top = area.y() * lh;
            if (logical.x() >= left
                    && logical.x() < left + area.width() * lw
                    && logical.y() >= top
                    && logical.y() < top + area.height() * lh) {
                return camera.screenToWorld(new Vec2(logical.x() - left, logical.y() - top));
            }
        }
        return null;
    }

    private @Nullable EntityImpl topInteractable(WorldImpl world, Vec2 point) {
        ComponentStore store = world.store(Interactable.class);
        if (store == null) {
            return null;
        }
        EntityImpl best = null;
        int bestLayer = Integer.MIN_VALUE;
        for (int i = 0; i < store.size; i++) {
            EntityImpl entity = store.owners[i];
            Interactable interactable = (Interactable) store.items[i];
            if (entity.removed || !entity.visible || !interactable.isEnabled() || !interactable.isAttached()) {
                continue;
            }
            Rect area = interactable.area();
            float left = area != null ? entity.x + area.x() : entity.x - entity.width / 2f;
            float top = area != null ? entity.y + area.y() : entity.y - entity.height / 2f;
            float width = area != null ? area.width() : entity.width;
            float height = area != null ? area.height() : entity.height;
            if (point.x() < left || point.x() > left + width || point.y() < top || point.y() > top + height) {
                continue;
            }
            int layer = layerOrder(world, entity.layer);
            if (best == null || layer > bestLayer || (layer == bestLayer && !WorldRendererOrder.before(entity, best))) {
                best = entity;
                bestLayer = layer;
            }
        }
        return best;
    }

    private static int layerOrder(WorldImpl world, String name) {
        for (RenderLayer layer : world.layers) {
            if (layer.name().equals(name)) {
                return layer.zOrder();
            }
        }
        return 0;
    }

    private void updatePointer(WorldImpl world) {
        ComponentStore store = world.store(Interactable.class);
        if (store == null || store.size == 0) {
            hovered = null;
            return;
        }
        Vec2 point = pointerWorld(world, input.mouseX(), input.mouseY());
        EntityImpl now = point == null ? null : topInteractable(world, point);
        EntityImpl before = hovered;
        if (before != now) {
            if (before != null) {
                Interactable interactable = before.component(Interactable.class);
                if (interactable != null) {
                    interactable.markHovered(false);
                    if (interactable.cursor() != null) {
                        input.setCursor(dev.gulp.api.input.SystemCursor.ARROW);
                    }
                }
                if (events.hasListeners(EntityHoverExitEvent.class)) {
                    events.call(new EntityHoverExitEvent(before));
                }
            }
            hovered = now;
            if (now != null) {
                Interactable interactable = now.component(Interactable.class);
                if (interactable != null) {
                    interactable.markHovered(true);
                    Cursor cursor = interactable.cursor();
                    if (cursor != null) {
                        input.setCursor(cursor);
                    }
                }
                if (events.hasListeners(EntityHoverEnterEvent.class)) {
                    events.call(new EntityHoverEnterEvent(now));
                }
            }
        }
        for (float[] click : clicks) {
            Vec2 at = pointerWorld(world, click[1], click[2]);
            if (at == null) {
                continue;
            }
            EntityImpl target = topInteractable(world, at);
            MouseButton button = MouseButton.ofIndex((int) click[0]);
            if (target != null && button != null && events.hasListeners(EntityClickEvent.class)) {
                events.call(new EntityClickEvent(target, button, at.x(), at.y()));
            }
        }
    }

    // ------------------------------------------------------------------ WorldView

    @Override
    public @Nullable List<RenderLayer> worldLayers() {
        WorldImpl world = active;
        return world == null ? null : world.layers;
    }

    @Override
    public List<CameraImpl> cameras() {
        WorldImpl world = active;
        return world == null ? List.of() : world.cameras;
    }

    @Override
    public void drawLayer(DrawImpl draw, RenderLayer layer, CameraImpl camera, float alpha) {
        WorldImpl world = active;
        if (world != null) {
            renderer.drawLayer(world, draw, layer, camera, alpha);
        }
    }

    @Override
    public void drawOverlay(DrawImpl draw, CameraImpl camera, float alpha) {
        WorldImpl world = active;
        if (world != null) {
            renderer.drawOverlay(world, draw, camera, alpha);
        }
    }

    @Override
    public boolean hasDebug() {
        WorldImpl world = active;
        return world != null && world.hasDebug();
    }

    @Override
    public void drawDebug(DrawImpl draw, CameraImpl camera, float worldPixel) {
        WorldImpl world = active;
        if (world != null) {
            renderer.drawDebug(world, draw, camera, worldPixel);
        }
    }

    @Override
    public @Nullable PostEffectsImpl worldPostEffects() {
        WorldImpl world = active;
        return world == null ? null : world.postEffects;
    }

    @Override
    public boolean lightingEnabled() {
        WorldImpl world = active;
        return world != null && world.lighting.isEnabled();
    }

    @Override
    public Color ambient() {
        WorldImpl world = active;
        return world == null ? Color.WHITE : world.lighting.ambient();
    }

    @Override
    public void drawLights(DrawImpl draw, CameraImpl camera) {
        WorldImpl world = active;
        if (world != null) {
            renderer.drawLights(world, draw, camera);
        }
    }

    @Override
    public @Nullable Transition transition() {
        return transition;
    }

    @Override
    public float coverage() {
        Transition running = transition;
        if (running == null) {
            return 0f;
        }
        float t = Math.min(1f, transitionTime / Math.max(0.0001f, running.duration()));
        return entering ? 1f - t : t;
    }

    @Override
    public boolean entering() {
        return entering;
    }

    /** Draw order shared with the renderer. */
    static final class WorldRendererOrder {
        private WorldRendererOrder() {}

        static boolean before(EntityImpl a, EntityImpl b) {
            if (a.zIndex != b.zIndex) {
                return a.zIndex < b.zIndex;
            }
            float ay = a.y + a.height / 2f;
            float by = b.y + b.height / 2f;
            if (ay != by) {
                return ay < by;
            }
            return a.runtimeId < b.runtimeId;
        }
    }
}
