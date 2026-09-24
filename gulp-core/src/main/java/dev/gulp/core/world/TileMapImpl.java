package dev.gulp.core.world;

import dev.gulp.api.Owner;
import dev.gulp.api.data.DataContainer;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.event.Subscription;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.math.GridPos;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Rng;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.world.Chunk;
import dev.gulp.api.world.ChunkData;
import dev.gulp.api.world.ChunkGenerator;
import dev.gulp.api.world.ChunkLoadEvent;
import dev.gulp.api.world.ChunkUnloadEvent;
import dev.gulp.api.world.Terrain;
import dev.gulp.api.world.TileChangeEvent;
import dev.gulp.api.world.TileLayer;
import dev.gulp.api.world.TileMap;
import dev.gulp.api.world.TileOrientation;
import dev.gulp.api.world.TileState;
import dev.gulp.api.world.TileType;
import dev.gulp.core.data.DataContainerImpl;
import dev.gulp.core.graphics.CameraImpl;
import dev.gulp.core.util.IntList;
import dev.gulp.core.util.LongObjectMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * {@link TileMap}: tile ids in chunks, a palette of tile types, layers, tile states, terrains, ticking tiles and chunk
 * streaming for generated worlds.
 */
final class TileMapImpl implements TileMap {

    /** Chunks around each camera kept loaded beyond the visible ones. */
    static final int MARGIN_CHUNKS = 1;

    /** Frames a chunk stays loaded after it stopped being needed. */
    static final int UNLOAD_DELAY_FRAMES = 120;

    /** Generated chunks added to the world per frame, to spread the cost. */
    static final int APPLY_PER_FRAME = 4;

    final WorldImpl world;
    private TileOrientation orientation;
    private int tileWidth;
    private int tileHeight;
    final List<TileLayerImpl> layers = new ArrayList<>();
    private final Map<String, TileLayerImpl> byName = new HashMap<>();
    final LongObjectMap<ChunkImpl> chunks = new LongObjectMap<>();
    final List<ChunkImpl> loaded = new ArrayList<>();
    final List<TileType> palette = new ArrayList<>();
    private final Map<TileType, Integer> ids = new HashMap<>();
    private final Set<Terrain> terrains = new LinkedHashSet<>();
    private @Nullable ChunkGenerator generator;
    private final LongObjectMap<ChunkImpl> saved = new LongObjectMap<>();
    private final LongObjectMap<Boolean> pending = new LongObjectMap<>();
    private final List<GeneratedChunk> ready = new ArrayList<>();
    private final List<Ticket> tickets = new ArrayList<>();
    private int frame;
    /** Changes whenever a cell or the set of loaded chunks changes; navigation searches again when it does. */
    long revision;

    private int[] tickScratch = new int[64];
    private int minX = Integer.MAX_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;

    TileMapImpl(WorldImpl world, TileOrientation orientation, int tileWidth, int tileHeight) {
        this.world = world;
        this.orientation = orientation;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        palette.add(null);
    }

    void setOrientation(TileOrientation value) {
        this.orientation = value;
    }

    void setTileSize(int width, int height) {
        this.tileWidth = width;
        this.tileHeight = height;
    }

    void setGenerator(ChunkGenerator value) {
        this.generator = value;
    }

    boolean isGenerated() {
        return generator != null;
    }

    float unitHeight() {
        return tileHeight / (float) tileWidth;
    }

    // ------------------------------------------------------------------ palette and cells

    int idOf(@Nullable TileType type) {
        if (type == null) {
            return 0;
        }
        Integer id = ids.get(type);
        if (id == null) {
            id = palette.size();
            palette.add(type);
            ids.put(type, id);
        }
        return id;
    }

    @Nullable TileType typeOf(int cell) {
        int id = cell & ChunkImpl.ID_MASK;
        return id == 0 || id >= palette.size() ? null : palette.get(id);
    }

    static long chunkKey(int x, int y) {
        return LongObjectMap.pack(Math.floorDiv(x, Chunk.SIZE), Math.floorDiv(y, Chunk.SIZE));
    }

    static int localIndex(int x, int y) {
        return Math.floorMod(y, Chunk.SIZE) * Chunk.SIZE + Math.floorMod(x, Chunk.SIZE);
    }

    int cell(TileLayerImpl layer, int x, int y) {
        ChunkImpl chunk = chunks.get(chunkKey(x, y));
        return chunk == null ? 0 : chunk.get(layer.index, localIndex(x, y));
    }

    private ChunkImpl chunkForWrite(int x, int y) {
        long key = chunkKey(x, y);
        ChunkImpl chunk = chunks.get(key);
        if (chunk == null) {
            chunk = new ChunkImpl(this, LongObjectMap.unpackX(key), LongObjectMap.unpackY(key));
            addChunk(chunk);
        }
        return chunk;
    }

    private void addChunk(ChunkImpl chunk) {
        chunks.put(LongObjectMap.pack(chunk.cx, chunk.cy), chunk);
        loaded.add(chunk);
        chunk.lastNeeded = frame;
        revision++;
    }

    /**
     * Writes a cell without events; used by loaders and by {@link #setTile}.
     *
     * @return the previous cell value
     */
    int writeCell(TileLayerImpl layer, int x, int y, int value, boolean modifies) {
        ChunkImpl chunk = chunkForWrite(x, y);
        int[] data = chunk.layer(layer.index);
        int local = localIndex(x, y);
        int old = data[local];
        if (old == value) {
            return old;
        }
        data[local] = value;
        chunk.version++;
        revision++;
        if (modifies) {
            chunk.modified = true;
        }
        TileType oldType = typeOf(old);
        TileType newType = typeOf(value);
        int packed = (layer.index << 10) | local;
        if (oldType != null && oldType.ticks()) {
            int index = chunk.ticking.indexOf(packed);
            if (index >= 0) {
                chunk.ticking.removeSwap(index);
            }
        }
        if (newType != null && newType.ticks()) {
            chunk.ticking.add(packed);
        }
        if (oldType != newType) {
            layer.states.remove(LongObjectMap.pack(x, y));
            if (newType != null && newType.isStateful()) {
                state(layer, x, y);
            }
        }
        if (value != 0) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        return old;
    }

    boolean set(TileLayerImpl layer, int x, int y, @Nullable TileType type) {
        int oldCell = cell(layer, x, y);
        TileType old = typeOf(oldCell);
        if (old == type && (oldCell & ~ChunkImpl.ID_MASK) == 0) {
            return true;
        }
        if (world.worlds.events.hasListeners(TileChangeEvent.class)) {
            TileChangeEvent event = world.worlds.events.call(new TileChangeEvent(world, layer, x, y, old, type));
            if (event.isCancelled()) {
                return false;
            }
        }
        writeCell(layer, x, y, idOf(type), true);
        return true;
    }

    // ------------------------------------------------------------------ TileMap

    @Override
    public TileOrientation orientation() {
        return orientation;
    }

    @Override
    public int tileWidth() {
        return tileWidth;
    }

    @Override
    public int tileHeight() {
        return tileHeight;
    }

    @Override
    public List<TileLayer> layers() {
        return Collections.unmodifiableList(new ArrayList<>(layers));
    }

    @Override
    public TileLayer layer(String name) {
        TileLayer layer = byName.get(name);
        if (layer == null) {
            throw new IllegalArgumentException("No tile layer '" + name + "'; layers: " + byName.keySet());
        }
        return layer;
    }

    @Override
    public @Nullable TileLayer findLayer(String name) {
        return byName.get(name);
    }

    @Override
    public TileLayerImpl addLayer(String name, int zOrder) {
        TileLayerImpl existing = byName.get(name);
        if (existing != null) {
            return existing;
        }
        TileLayerImpl layer = new TileLayerImpl(this, name, zOrder, byName.size());
        byName.put(name, layer);
        layers.add(layer);
        layers.sort(Comparator.comparingInt(TileLayerImpl::zOrder));
        return layer;
    }

    TileLayerImpl layerOrCreate(String name) {
        TileLayerImpl layer = byName.get(name);
        return layer != null ? layer : addLayer(name, layers.size() * 10);
    }

    @Override
    public @Nullable TileType tile(String layer, int x, int y) {
        TileLayerImpl found = byName.get(layer);
        return found == null ? null : typeOf(cell(found, x, y));
    }

    @Override
    public boolean setTile(String layer, int x, int y, @Nullable TileType type) {
        world.worlds.context.checkMainThread("TileMap.setTile");
        return set(layerOrCreate(layer), x, y, type);
    }

    @Override
    public void fill(String layer, int x, int y, int width, int height, @Nullable TileType type) {
        TileLayerImpl target = layerOrCreate(layer);
        for (int ty = y; ty < y + height; ty++) {
            for (int tx = x; tx < x + width; tx++) {
                set(target, tx, ty, type);
            }
        }
    }

    @Override
    public void clear(String layer) {
        TileLayerImpl target = byName.get(layer);
        if (target == null) {
            return;
        }
        for (ChunkImpl chunk : loaded) {
            if (target.index < chunk.cells.length && chunk.cells[target.index] != null) {
                for (int i = 0; i < ChunkImpl.CELLS; i++) {
                    if (chunk.cells[target.index][i] != 0) {
                        int x = chunk.cx * Chunk.SIZE + i % Chunk.SIZE;
                        int y = chunk.cy * Chunk.SIZE + i / Chunk.SIZE;
                        set(target, x, y, null);
                    }
                }
            }
        }
    }

    @Override
    public GridPos worldToTile(Vec2 point) {
        float h = unitHeight();
        float px = point.x();
        float py = point.y();
        switch (orientation) {
            case ORTHOGONAL -> {
                return new GridPos((int) Math.floor(px), (int) Math.floor(py / h));
            }
            case ISOMETRIC -> {
                // Inverse of centre = ((x - y) / 2, (x + y) * h / 2) + (0.5, h / 2).
                float a = px - 0.5f;
                float b = (py - h / 2f) / h;
                return new GridPos(Math.round(a + b), Math.round(b - a));
            }
            default -> {
                // Staggered and hexagonal: start from the row or column guess and pick the nearest centre.
                int guessX;
                int guessY;
                if (orientation == TileOrientation.HEXAGONAL_FLAT) {
                    guessX = (int) Math.floor(px / 0.75f);
                    guessY = (int) Math.floor(py / h);
                } else {
                    float rowStep = orientation == TileOrientation.ISOMETRIC_STAGGERED ? h / 2f : h * 0.75f;
                    guessY = (int) Math.floor(py / rowStep);
                    guessX = (int) Math.floor(px);
                }
                int bestX = guessX;
                int bestY = guessY;
                float best = Float.MAX_VALUE;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        float cxw = centerX(guessX + dx, guessY + dy);
                        float cyw = centerY(guessX + dx, guessY + dy);
                        float d = (cxw - px) * (cxw - px) + (cyw - py) * (cyw - py);
                        if (d < best) {
                            best = d;
                            bestX = guessX + dx;
                            bestY = guessY + dy;
                        }
                    }
                }
                return new GridPos(bestX, bestY);
            }
        }
    }

    @Override
    public Vec2 tileToWorld(int x, int y) {
        return new Vec2(centerX(x, y), centerY(x, y));
    }

    /** Centre of a tile, x. */
    float centerX(int x, int y) {
        return switch (orientation) {
            case ORTHOGONAL -> x + 0.5f;
            case ISOMETRIC -> (x - y) * 0.5f + 0.5f;
            case ISOMETRIC_STAGGERED, HEXAGONAL_POINTY -> x + ((y & 1) != 0 ? 1f : 0.5f);
            case HEXAGONAL_FLAT -> x * 0.75f + 0.5f;
        };
    }

    /** Centre of a tile, y. */
    float centerY(int x, int y) {
        float h = unitHeight();
        return switch (orientation) {
            case ORTHOGONAL -> (y + 0.5f) * h;
            case ISOMETRIC -> (x + y) * h / 2f + h / 2f;
            case ISOMETRIC_STAGGERED -> y * h / 2f + h / 2f;
            case HEXAGONAL_POINTY -> y * h * 0.75f + h / 2f;
            case HEXAGONAL_FLAT -> y * h + ((x & 1) != 0 ? h : h / 2f);
        };
    }

    @Override
    public Rect bounds() {
        if (minX > maxX) {
            return new Rect(0, 0, 0, 0);
        }
        return new Rect(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    @Override
    public TileState state(String layer, int x, int y) {
        return state(layerOrCreate(layer), x, y);
    }

    TileStateImpl state(TileLayerImpl layer, int x, int y) {
        long key = LongObjectMap.pack(x, y);
        TileStateImpl state = layer.states.get(key);
        if (state == null) {
            state = new TileStateImpl(layer, x, y);
            layer.states.put(key, state);
        }
        return state;
    }

    @Override
    public @Nullable TileState findState(String layer, int x, int y) {
        TileLayerImpl found = byName.get(layer);
        return found == null ? null : found.states.get(LongObjectMap.pack(x, y));
    }

    @Override
    public boolean interact(String layer, int x, int y, @Nullable Entity who) {
        TileLayerImpl found = byName.get(layer);
        TileType type = found == null ? null : typeOf(cell(found, x, y));
        if (type == null || type.interaction() == null) {
            return false;
        }
        type.interaction().interact(who, found, x, y);
        return true;
    }

    // ------------------------------------------------------------------ terrain

    @Override
    public void setTerrain(String layer, int x, int y, @Nullable Terrain terrain) {
        TileLayerImpl target = layerOrCreate(layer);
        if (terrain != null) {
            terrains.add(terrain);
        }
        set(target, x, y, terrain == null ? null : terrain.tiles().get(0));
        refreshTerrain(target, x - 1, y - 1, 3, 3);
    }

    @Override
    public void paintTerrain(String layer, int x, int y, int width, int height, Terrain terrain) {
        TileLayerImpl target = layerOrCreate(layer);
        terrains.add(terrain);
        for (int ty = y; ty < y + height; ty++) {
            for (int tx = x; tx < x + width; tx++) {
                set(target, tx, ty, terrain.tiles().get(0));
            }
        }
        refreshTerrain(target, x - 1, y - 1, width + 2, height + 2);
    }

    private @Nullable Terrain terrainOf(@Nullable TileType type) {
        if (type == null) {
            return null;
        }
        for (Terrain terrain : terrains) {
            if (terrain.contains(type)) {
                return terrain;
            }
        }
        return null;
    }

    private void refreshTerrain(TileLayerImpl layer, int x, int y, int width, int height) {
        for (int ty = y; ty < y + height; ty++) {
            for (int tx = x; tx < x + width; tx++) {
                Terrain terrain = terrainOf(typeOf(cell(layer, tx, ty)));
                if (terrain == null) {
                    continue;
                }
                int mask = 0;
                mask |= terrain.contains(typeOf(cell(layer, tx, ty - 1))) ? 1 : 0;
                mask |= terrain.contains(typeOf(cell(layer, tx + 1, ty - 1))) ? 2 : 0;
                mask |= terrain.contains(typeOf(cell(layer, tx + 1, ty))) ? 4 : 0;
                mask |= terrain.contains(typeOf(cell(layer, tx + 1, ty + 1))) ? 8 : 0;
                mask |= terrain.contains(typeOf(cell(layer, tx, ty + 1))) ? 16 : 0;
                mask |= terrain.contains(typeOf(cell(layer, tx - 1, ty + 1))) ? 32 : 0;
                mask |= terrain.contains(typeOf(cell(layer, tx - 1, ty))) ? 64 : 0;
                mask |= terrain.contains(typeOf(cell(layer, tx - 1, ty - 1))) ? 128 : 0;
                TileType wanted = terrain.tileFor(mask);
                if (typeOf(cell(layer, tx, ty)) != wanted) {
                    set(layer, tx, ty, wanted);
                }
            }
        }
    }

    // ------------------------------------------------------------------ ticking

    void tick(Rng rng) {
        long worldTicks = world.ticks;
        for (int c = 0; c < loaded.size(); c++) {
            ChunkImpl chunk = loaded.get(c);
            // Work on a copy: behaviours may change tiles, which reorders the chunk's list.
            IntList ticking = chunk.ticking;
            int count = ticking.size();
            if (tickScratch.length < count) {
                tickScratch = new int[Math.max(count, tickScratch.length * 2)];
            }
            for (int i = 0; i < count; i++) {
                tickScratch[i] = ticking.get(i);
            }
            for (int i = 0; i < count; i++) {
                int packed = tickScratch[i];
                int layerIndex = packed >>> 10;
                int local = packed & 1023;
                TileType type = typeOf(chunk.get(layerIndex, local));
                if (type == null) {
                    continue;
                }
                int x = chunk.cx * Chunk.SIZE + local % Chunk.SIZE;
                int y = chunk.cy * Chunk.SIZE + local / Chunk.SIZE;
                TileLayerImpl layer = layerByIndex(layerIndex);
                if (layer == null) {
                    continue;
                }
                TileType.Behaviour random = type.randomTick();
                if (random != null && rng.nextFloat() < type.randomTickChance()) {
                    random.run(world, layer, x, y);
                }
                TileType.Behaviour periodic = type.periodicTick();
                if (periodic != null && Math.floorMod(worldTicks + x * 31L + y * 17L, type.tickPeriod()) == 0) {
                    periodic.run(world, layer, x, y);
                }
            }
        }
    }

    @Nullable TileLayerImpl layerByIndex(int index) {
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).index == index) {
                return layers.get(i);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ chunks

    @Nullable Chunk chunk(int cx, int cy) {
        return chunks.get(LongObjectMap.pack(cx, cy));
    }

    List<Chunk> loadedChunks() {
        return List.copyOf(loaded);
    }

    Subscription keepLoaded(Rect area, Owner owner) {
        Ticket ticket = new Ticket(area, owner);
        tickets.add(ticket);
        return ticket;
    }

    /**
     * Loads chunks around the cameras and tickets, applies finished generation and unloads chunks no longer needed.
     * Called every frame for generated worlds.
     */
    void stream(List<CameraImpl> cameras) {
        ChunkGenerator gen = generator;
        if (gen == null) {
            return;
        }
        frame++;
        for (CameraImpl camera : cameras) {
            Rect view = camera.bounds();
            need(
                    view.x(),
                    view.y() / unitHeight(),
                    view.x() + view.width(),
                    (view.y() + view.height()) / unitHeight(),
                    gen);
        }
        for (int i = tickets.size() - 1; i >= 0; i--) {
            Ticket ticket = tickets.get(i);
            if (!ticket.active || !ticket.owner.isEnabled()) {
                tickets.remove(i);
                continue;
            }
            Rect area = ticket.area;
            need(
                    area.x(),
                    area.y() / unitHeight(),
                    area.x() + area.width(),
                    (area.y() + area.height()) / unitHeight(),
                    gen);
        }
        int applied = 0;
        while (!ready.isEmpty() && applied < APPLY_PER_FRAME) {
            apply(ready.remove(0));
            applied++;
        }
        for (int i = loaded.size() - 1; i >= 0; i--) {
            ChunkImpl chunk = loaded.get(i);
            if (frame - chunk.lastNeeded > UNLOAD_DELAY_FRAMES) {
                unload(chunk);
            }
        }
    }

    private void need(float minWorldX, float minTileY, float maxWorldX, float maxTileY, ChunkGenerator gen) {
        int cx0 = Math.floorDiv((int) Math.floor(minWorldX), Chunk.SIZE) - MARGIN_CHUNKS;
        int cy0 = Math.floorDiv((int) Math.floor(minTileY), Chunk.SIZE) - MARGIN_CHUNKS;
        int cx1 = Math.floorDiv((int) Math.floor(maxWorldX), Chunk.SIZE) + MARGIN_CHUNKS;
        int cy1 = Math.floorDiv((int) Math.floor(maxTileY), Chunk.SIZE) + MARGIN_CHUNKS;
        for (int cy = cy0; cy <= cy1; cy++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                long key = LongObjectMap.pack(cx, cy);
                ChunkImpl chunk = chunks.get(key);
                if (chunk != null) {
                    chunk.lastNeeded = frame;
                    continue;
                }
                ChunkImpl kept = saved.remove(key);
                if (kept != null) {
                    kept.loaded = true;
                    addChunk(kept);
                    fireLoaded(kept, false);
                    continue;
                }
                if (!pending.containsKey(key)) {
                    pending.put(key, Boolean.TRUE);
                    generate(gen, cx, cy, key);
                }
            }
        }
    }

    private void generate(ChunkGenerator gen, int cx, int cy, long key) {
        long seed = world.settings().seed() * 0x9E3779B97F4A7C15L + key * 0xC2B2AE3D27D4EB4FL;
        world.worlds.executor.execute(() -> {
            ChunkDataImpl data = new ChunkDataImpl(cx * Chunk.SIZE, cy * Chunk.SIZE);
            RuntimeException failure = null;
            try {
                gen.generate(data, cx, cy, new Rng(seed));
            } catch (RuntimeException error) {
                failure = error;
            }
            RuntimeException error = failure;
            world.worlds.mainQueue.post(() -> {
                if (error != null) {
                    pending.remove(key);
                    world.worlds.logger.error("Chunk generation failed at " + cx + ", " + cy, error);
                    return;
                }
                ready.add(new GeneratedChunk(cx, cy, key, data));
            });
        });
    }

    private void apply(GeneratedChunk generated) {
        pending.remove(generated.key);
        if (!world.loaded || chunks.containsKey(generated.key)) {
            return;
        }
        ChunkImpl chunk = new ChunkImpl(this, generated.cx, generated.cy);
        addChunk(chunk);
        for (Map.Entry<String, int[]> layer : generated.data.layers.entrySet()) {
            TileLayerImpl target = layerOrCreate(layer.getKey());
            int[] cells = layer.getValue();
            for (int i = 0; i < ChunkImpl.CELLS; i++) {
                TileType type = generated.data.types.get(cells[i]);
                if (type != null) {
                    writeCell(
                            target,
                            chunk.cx * Chunk.SIZE + i % Chunk.SIZE,
                            chunk.cy * Chunk.SIZE + i / Chunk.SIZE,
                            idOf(type),
                            false);
                }
            }
        }
        chunk.modified = false;
        for (ChunkDataImpl.Spawn spawn : generated.data.spawns) {
            Entity entity = world.spawn(
                    spawn.type(), generated.data.originX() + spawn.x(), generated.data.originY() + spawn.y());
            if (entity instanceof EntityImpl impl && !impl.removed) {
                impl.chunkKey = generated.key;
                chunk.spawned.add(impl);
            }
        }
        fireLoaded(chunk, true);
    }

    private void fireLoaded(ChunkImpl chunk, boolean generated) {
        if (world.worlds.events.hasListeners(ChunkLoadEvent.class)) {
            world.worlds.events.call(new ChunkLoadEvent(chunk, generated));
        }
    }

    private void unload(ChunkImpl chunk) {
        if (world.worlds.events.hasListeners(ChunkUnloadEvent.class)) {
            world.worlds.events.call(new ChunkUnloadEvent(chunk));
        }
        for (EntityImpl entity : chunk.spawned) {
            entity.remove();
        }
        chunk.spawned.clear();
        revision++;
        long key = LongObjectMap.pack(chunk.cx, chunk.cy);
        chunks.remove(key);
        loaded.remove(chunk);
        chunk.loaded = false;
        if (chunk.modified) {
            saved.put(key, chunk);
        }
    }

    void dispose() {
        chunks.clear();
        loaded.clear();
        ready.clear();
        pending.clear();
        tickets.clear();
    }

    int pendingChunks() {
        return pending.size();
    }

    /** A finished generation waiting to be added on the main thread. */
    private record GeneratedChunk(int cx, int cy, long key, ChunkDataImpl data) {}

    /** An area kept loaded. */
    private static final class Ticket implements Subscription {
        final Rect area;
        final Owner owner;
        boolean active = true;

        Ticket(Rect area, Owner owner) {
            this.area = area;
            this.owner = owner;
        }

        @Override
        public void cancel() {
            active = false;
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }

    /** {@link ChunkData}: plain arrays filled off the main thread. */
    static final class ChunkDataImpl implements ChunkData {
        final int originX;
        final int originY;
        final Map<String, int[]> layers = new java.util.LinkedHashMap<>();
        final List<@Nullable TileType> types = new ArrayList<>(List.of());
        final Map<TileType, Integer> typeIds = new HashMap<>();
        final List<Spawn> spawns = new ArrayList<>();

        record Spawn(EntityType type, float x, float y) {}

        ChunkDataImpl(int originX, int originY) {
            this.originX = originX;
            this.originY = originY;
            types.add(null);
        }

        @Override
        public int originX() {
            return originX;
        }

        @Override
        public int originY() {
            return originY;
        }

        private static void check(int x, int y) {
            if (x < 0 || y < 0 || x >= Chunk.SIZE || y >= Chunk.SIZE) {
                throw new IndexOutOfBoundsException("Local tile " + x + ", " + y + " is outside the chunk");
            }
        }

        @Override
        public void setTile(String layer, int x, int y, @Nullable TileType type) {
            check(x, y);
            int[] cells = layers.computeIfAbsent(layer, l -> new int[ChunkImpl.CELLS]);
            int id = 0;
            if (type != null) {
                Integer known = typeIds.get(type);
                if (known == null) {
                    known = types.size();
                    types.add(type);
                    typeIds.put(type, known);
                }
                id = known;
            }
            cells[y * Chunk.SIZE + x] = id;
        }

        @Override
        public @Nullable TileType tile(String layer, int x, int y) {
            check(x, y);
            int[] cells = layers.get(layer);
            return cells == null ? null : types.get(cells[y * Chunk.SIZE + x]);
        }

        @Override
        public void spawn(EntityType type, float x, float y) {
            spawns.add(new Spawn(type, x, y));
        }
    }

    /** {@link TileState}: data of one tile. */
    static final class TileStateImpl implements TileState {
        private final TileLayerImpl layer;
        private final int x;
        private final int y;
        private final DataContainer data = new DataContainerImpl(JsonObject.EMPTY);

        TileStateImpl(TileLayerImpl layer, int x, int y) {
            this.layer = layer;
            this.x = x;
            this.y = y;
        }

        @Override
        public TileLayer layer() {
            return layer;
        }

        @Override
        public int x() {
            return x;
        }

        @Override
        public int y() {
            return y;
        }

        @Override
        public DataContainer data() {
            return data;
        }
    }

    /** {@link TileLayer}. */
    static final class TileLayerImpl implements TileLayer {
        final TileMapImpl map;
        final String name;
        final int zOrder;
        final int index;
        final LongObjectMap<TileStateImpl> states = new LongObjectMap<>();
        boolean visible = true;
        boolean collision = true;
        Vec2 parallax = Vec2.ONE;
        Color tint = Color.WHITE;
        String renderLayer = "tiles";
        int version;

        TileLayerImpl(TileMapImpl map, String name, int zOrder, int index) {
            this.map = map;
            this.name = name;
            this.zOrder = zOrder;
            this.index = index;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public TileMap map() {
            return map;
        }

        @Override
        public int zOrder() {
            return zOrder;
        }

        @Override
        public boolean isVisible() {
            return visible;
        }

        @Override
        public void setVisible(boolean value) {
            visible = value;
        }

        @Override
        public boolean isCollision() {
            return collision;
        }

        @Override
        public void setCollision(boolean value) {
            collision = value;
        }

        @Override
        public Vec2 parallax() {
            return parallax;
        }

        @Override
        public void setParallax(Vec2 factor) {
            parallax = factor;
        }

        @Override
        public Color tint() {
            return tint;
        }

        @Override
        public void setTint(Color value) {
            tint = value;
            version++;
        }

        @Override
        public String renderLayer() {
            return renderLayer;
        }

        @Override
        public void setRenderLayer(String value) {
            renderLayer = value;
        }

        @Override
        public @Nullable TileType get(int x, int y) {
            return map.typeOf(map.cell(this, x, y));
        }

        @Override
        public boolean set(int x, int y, @Nullable TileType type) {
            return map.set(this, x, y, type);
        }

        @Override
        public String toString() {
            return "TileLayer[" + name + "]";
        }
    }
}
