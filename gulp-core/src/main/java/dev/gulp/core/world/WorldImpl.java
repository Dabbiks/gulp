package dev.gulp.core.world;

import dev.gulp.api.Owner;
import dev.gulp.api.PauseMode;
import dev.gulp.api.audio.Playback;
import dev.gulp.api.audio.Sound;
import dev.gulp.api.data.DataContainer;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityQuery;
import dev.gulp.api.entity.EntityRemoveEvent;
import dev.gulp.api.entity.EntitySpawnEvent;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.event.Subscription;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Rng;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.render.Camera;
import dev.gulp.api.render.RenderLayer;
import dev.gulp.api.spi.ComponentAccess;
import dev.gulp.api.world.Chunk;
import dev.gulp.api.world.Location;
import dev.gulp.api.world.Parallax;
import dev.gulp.api.world.TileMap;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;
import dev.gulp.core.data.DataContainerImpl;
import dev.gulp.core.graphics.CameraImpl;
import dev.gulp.core.graphics.DisplayImpl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** {@link World}: entities with their indexes, component stores, the tile map, cameras, layers and parallax. */
final class WorldImpl implements World {

    final WorldsImpl worlds;
    private final String name;
    private final WorldSettings settings;
    final SpatialGrid grid = new SpatialGrid();
    final List<EntityImpl> entities = new ArrayList<>();
    private final Map<UUID, EntityImpl> byId = new HashMap<>();
    private final Map<String, EntityImpl> byName = new HashMap<>();
    private final Map<String, Set<EntityImpl>> byTag = new HashMap<>();
    private final Map<Class<?>, ComponentStore> storesByType = new IdentityHashMap<>();
    final List<ComponentStore> stores = new ArrayList<>();
    private final List<EntityImpl> removals = new ArrayList<>();
    private final List<PendingStoreRemoval> storeRemovals = new ArrayList<>();
    final TileMapImpl tileMap;
    final ParallaxImpl parallax;
    final List<CameraImpl> cameras = new ArrayList<>();
    final List<RenderLayer> layers = new ArrayList<>();
    private final Rng rng;
    private @Nullable DataContainer data;
    long ticks;
    private boolean ticking;
    boolean loaded = true;
    final List<EntityImpl> onScreen = new ArrayList<>();
    int screenStamp;

    WorldImpl(WorldsImpl worlds, String name, WorldSettings settings) {
        this.worlds = worlds;
        this.name = name;
        this.settings = settings;
        this.rng = new Rng(settings.seed());
        this.tileMap = new TileMapImpl(this, settings.orientation(), settings.tileSize(), settings.tileSize());
        this.parallax = new ParallaxImpl(this);
        CameraImpl main = new CameraImpl(worlds.display.pixelsPerUnit());
        cameras.add(main);
        for (int i = 0; i < DisplayImpl.WORLD_LAYERS.size(); i++) {
            layers.add(new RenderLayer(DisplayImpl.WORLD_LAYERS.get(i), i * 100, false));
        }
    }

    // ------------------------------------------------------------------ spawning and removal

    @Override
    public Entity spawn(EntityType type, float x, float y) {
        return spawn(type, x, y, e -> {});
    }

    @Override
    public Entity spawn(EntityType type, Vec2 position) {
        return spawn(type, position.x(), position.y(), e -> {});
    }

    @Override
    public Entity spawn(EntityType type, Location location, Consumer<Entity> configure) {
        if (location.world() != this) {
            throw new IllegalArgumentException(
                    "The location is in world " + location.world().name());
        }
        return spawn(type, location.x(), location.y(), configure);
    }

    @Override
    public Entity spawn(EntityType type, float x, float y, Consumer<Entity> configure) {
        worlds.context.checkMainThread("World.spawn");
        if (!loaded) {
            throw new IllegalStateException("World " + name + " is unloaded");
        }
        EntityImpl entity = new EntityImpl(this, type, UUID.randomUUID(), worlds.nextRuntimeId(), x, y);
        for (var factory : type.components()) {
            entity.add(factory.get());
        }
        configure.accept(entity);
        if (worlds.events.hasListeners(EntitySpawnEvent.class)
                && worlds.events.call(new EntitySpawnEvent(entity)).isCancelled()) {
            discard(entity);
            return entity;
        }
        insert(entity);
        for (int i = 0; i < entity.componentCount; i++) {
            ComponentAccess.spawn(entity.components[i]);
        }
        return entity;
    }

    /** Adds an entity to the indexes and stores. */
    void insert(EntityImpl entity) {
        String entityName = entity.name();
        if (entityName != null && byName.containsKey(entityName)) {
            throw new IllegalArgumentException("World " + name + " already has an entity named '" + entityName + "'");
        }
        entity.world = this;
        entity.spawned = true;
        entities.add(entity);
        byId.put(entity.id, entity);
        if (entityName != null) {
            byName.put(entityName, entity);
        }
        for (String tag : entity.tags.tags) {
            tagAdded(entity, tag);
        }
        grid.insert(entity);
        for (int i = 0; i < entity.componentCount; i++) {
            storeAdd(entity, entity.components[i]);
        }
        entity.rememberPrevious();
    }

    /** Takes an entity out of the indexes and stores without running removal callbacks. */
    private void extract(EntityImpl entity) {
        entities.remove(entity);
        byId.remove(entity.id);
        String entityName = entity.name();
        if (entityName != null && byName.get(entityName) == entity) {
            byName.remove(entityName);
        }
        for (String tag : entity.tags.tags) {
            tagRemoved(entity, tag);
        }
        grid.remove(entity);
        for (int i = 0; i < entity.componentCount; i++) {
            int slot = entity.slots[i];
            if (slot >= 0) {
                storeRemoveNow(entity.components[i], slot);
                entity.slots[i] = -1;
            }
        }
        entity.spawned = false;
        if (entity.onScreen) {
            entity.onScreen = false;
            onScreen.remove(entity);
        }
    }

    void transfer(EntityImpl entity, WorldImpl target) {
        extract(entity);
        target.insert(entity);
    }

    private void discard(EntityImpl entity) {
        entity.removed = true;
        for (int i = entity.componentCount - 1; i >= 0; i--) {
            Component component = entity.components[i];
            ComponentAccess.remove(component);
            ComponentAccess.bind(component, null);
        }
    }

    void queueRemoval(EntityImpl entity) {
        removals.add(entity);
        if (!ticking && !worlds.inTick()) {
            flushRemovals();
        }
    }

    /** Removes entities marked during the tick. */
    void flushRemovals() {
        for (int i = 0; i < removals.size(); i++) {
            EntityImpl entity = removals.get(i);
            if (!entity.spawned) {
                continue;
            }
            for (int c = entity.componentCount - 1; c >= 0; c--) {
                try {
                    ComponentAccess.remove(entity.components[c]);
                } catch (RuntimeException error) {
                    worlds.logger.error("onRemove failed in " + entity, error);
                }
            }
            if (worlds.events.hasListeners(EntityRemoveEvent.class)) {
                worlds.events.call(new EntityRemoveEvent(entity));
            }
            entity.detach();
            extract(entity);
            for (int c = 0; c < entity.componentCount; c++) {
                ComponentAccess.bind(entity.components[c], null);
            }
            worlds.releaseEntity(entity);
        }
        removals.clear();
        for (PendingStoreRemoval pending : storeRemovals) {
            storeRemoveNow(pending.component, pending.slot);
        }
        storeRemovals.clear();
    }

    void rename(EntityImpl entity, @Nullable String oldName, @Nullable String newName) {
        if (newName != null) {
            EntityImpl other = byName.get(newName);
            if (other != null && other != entity) {
                throw new IllegalArgumentException("World " + name + " already has an entity named '" + newName + "'");
            }
        }
        if (oldName != null) {
            byName.remove(oldName);
        }
        if (newName != null) {
            byName.put(newName, entity);
        }
    }

    void tagAdded(EntityImpl entity, String tag) {
        byTag.computeIfAbsent(tag, t -> new LinkedHashSet<>()).add(entity);
    }

    void tagRemoved(EntityImpl entity, String tag) {
        Set<EntityImpl> set = byTag.get(tag);
        if (set != null) {
            set.remove(entity);
        }
    }

    Set<EntityImpl> tagged(String tag) {
        Set<EntityImpl> set = byTag.get(tag);
        return set != null ? set : Set.of();
    }

    // ------------------------------------------------------------------ stores

    void storeAdd(EntityImpl entity, Component component) {
        Class<?> type = component.getClass();
        ComponentStore store = storesByType.get(type);
        if (store == null) {
            store = new ComponentStore(type, component.tickOrder(), storesByType.size());
            storesByType.put(type, store);
            stores.add(store);
            stores.sort(
                    Comparator.comparingInt((ComponentStore s) -> s.tickOrder).thenComparingInt(s -> s.order));
        }
        int slot = store.add(component, entity);
        entity.setSlot(component, slot);
    }

    void storeRemove(Component component, int slot) {
        if (ticking) {
            storeRemovals.add(new PendingStoreRemoval(component, slot));
            // The slot keeps the detached component until the tick ends; ticking skips detached components.
        } else {
            storeRemoveNow(component, slot);
        }
    }

    private void storeRemoveNow(Component component, int slot) {
        ComponentStore store = storesByType.get(component.getClass());
        if (store == null) {
            return;
        }
        if (slot < store.size && store.items[slot] == component) {
            store.remove(slot);
            return;
        }
        // Other removals may have moved it since the slot was recorded.
        for (int i = 0; i < store.size; i++) {
            if (store.items[i] == component) {
                store.remove(i);
                return;
            }
        }
    }

    @Nullable ComponentStore store(Class<?> type) {
        return storesByType.get(type);
    }

    private record PendingStoreRemoval(Component component, int slot) {}

    // ------------------------------------------------------------------ ticking

    /**
     * Runs one tick: tiles, then components by store, then removals.
     *
     * @param paused whether the game is paused; then only entities with {@link PauseMode#ALWAYS} tick
     */
    void tick(boolean paused) {
        ticking = true;
        try {
            if (!paused) {
                ticks++;
                for (int i = 0; i < entities.size(); i++) {
                    entities.get(i).rememberPrevious();
                }
                tileMap.tick(rng);
            }
            for (int s = 0; s < stores.size(); s++) {
                ComponentStore store = stores.get(s);
                int count = store.size;
                for (int i = 0; i < count && i < store.size; i++) {
                    Component component = store.items[i];
                    EntityImpl owner = store.owners[i];
                    if (!component.isEnabled() || owner.removed || !component.isAttached()) {
                        continue;
                    }
                    if (paused && owner.pauseMode != PauseMode.ALWAYS) {
                        continue;
                    }
                    try {
                        ComponentAccess.tick(component);
                    } catch (RuntimeException error) {
                        worlds.logger.error(
                                "onTick of " + component.getClass().getSimpleName() + " failed in " + owner, error);
                    }
                }
            }
        } finally {
            ticking = false;
        }
        flushRemovals();
    }

    // ------------------------------------------------------------------ World

    @Override
    public String name() {
        return name;
    }

    @Override
    public WorldSettings settings() {
        return settings;
    }

    @Override
    public List<Entity> entities() {
        return List.copyOf(entities);
    }

    @Override
    public int entityCount() {
        return entities.size();
    }

    @Override
    public @Nullable Entity entity(String entityName) {
        return byName.get(entityName);
    }

    @Override
    public @Nullable Entity entity(UUID id) {
        return byId.get(id);
    }

    @Override
    public EntityQuery query() {
        return new EntityQueryImpl(this);
    }

    @Override
    public List<Entity> entitiesNear(Vec2 center, float radius) {
        return query().near(center, radius).list();
    }

    @Override
    public List<Entity> entitiesIn(Rect area) {
        return query().in(area).list();
    }

    @Override
    public TileMap tileMap() {
        return tileMap;
    }

    @Override
    public Camera camera() {
        return cameras.get(0);
    }

    CameraImpl mainCamera() {
        return cameras.get(0);
    }

    @Override
    public List<Camera> cameras() {
        return List.copyOf(cameras);
    }

    @Override
    public Camera addCamera(Rect viewport) {
        CameraImpl camera = new CameraImpl(worlds.display.pixelsPerUnit());
        camera.setViewport(viewport);
        cameras.add(camera);
        return camera;
    }

    @Override
    public void removeCamera(Camera camera) {
        if (camera == cameras.get(0)) {
            throw new IllegalArgumentException("The main camera cannot be removed");
        }
        cameras.remove(camera);
    }

    @Override
    public List<RenderLayer> layers() {
        return Collections.unmodifiableList(layers);
    }

    @Override
    public RenderLayer addLayer(String layerName, int zOrder) {
        for (RenderLayer layer : layers) {
            if (layer.name().equals(layerName)) {
                return layer;
            }
        }
        RenderLayer layer = new RenderLayer(layerName, zOrder, false);
        layers.add(layer);
        layers.sort(Comparator.comparingInt(RenderLayer::zOrder));
        return layer;
    }

    @Override
    public Parallax parallax() {
        return parallax;
    }

    @Override
    public Rng rng() {
        return rng;
    }

    @Override
    public DataContainer data() {
        DataContainer current = data;
        if (current == null) {
            current = new DataContainerImpl(JsonObject.EMPTY);
            data = current;
        }
        return current;
    }

    @Override
    public long ticks() {
        return ticks;
    }

    @Override
    public Location location(float x, float y) {
        return new Location(this, x, y);
    }

    @Override
    public Playback playSound(Vec2 position, Sound sound) {
        return worlds.audio.playAt(sound, position.x(), position.y());
    }

    @Override
    public Subscription keepLoaded(Rect area, Owner owner) {
        return tileMap.keepLoaded(area, owner);
    }

    @Override
    public @Nullable Chunk chunk(int chunkX, int chunkY) {
        return tileMap.chunk(chunkX, chunkY);
    }

    @Override
    public List<Chunk> loadedChunks() {
        return tileMap.loadedChunks();
    }

    @Override
    public boolean isActive() {
        return worlds.active() == this;
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    /** Removes every entity at once, for unloading. */
    void clear() {
        for (EntityImpl entity : List.copyOf(entities)) {
            entity.removed = true;
            removals.add(entity);
        }
        boolean wasTicking = ticking;
        ticking = false;
        flushRemovals();
        ticking = wasTicking;
        grid.clear();
        tileMap.dispose();
        loaded = false;
    }

    @Override
    public String toString() {
        return "World[" + name + ", " + entities.size() + " entities]";
    }
}
