package dev.gulp.api.world;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.entity.EntityType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Where a world comes from: nothing, a chunk generator, a Tiled map ({@code .tmx} or {@code .tmj}) or an LDtk project ({@code .ldtk}).
 * Map objects become entities through {@link #spawn(String, EntityType)} and {@link #spawner(ObjectSpawner)}; map tiles
 * can be replaced by registered tile types with {@link #tile(String, TileType)}.
 *
 * <pre>{@code
 * worlds().register("level1", WorldSource.ldtk(GameAssets.Maps.LEVEL1)
 *         .spawn("Player", PLAYER)
 *         .spawn("Coin", COIN)
 *         .tile("Walls", WALL));          // IntGrid value "Walls" collides like WALL
 * worlds().switchTo("level1", Transitions.fade(0.5f));
 * }</pre>
 */
public final class WorldSource {

    /** What the world is made from. */
    public enum Kind {
        /** An empty world. */
        EMPTY,
        /** Chunks made by a generator. */
        GENERATOR,
        /** A Tiled map. */
        TILED,
        /** An LDtk project. */
        LDTK
    }

    private final Kind kind;
    private final @Nullable ChunkGenerator generator;
    private final @Nullable AssetKey<String> map;
    private final @Nullable String level;
    private WorldSettings settings = WorldSettings.DEFAULT;
    private final Map<String, EntityType> spawns = new LinkedHashMap<>();
    private final Map<String, TileType> tiles = new LinkedHashMap<>();
    private @Nullable ObjectSpawner spawner;
    private @Nullable Consumer<World> onLoad;

    private WorldSource(
            Kind kind, @Nullable ChunkGenerator generator, @Nullable AssetKey<String> map, @Nullable String level) {
        this.kind = kind;
        this.generator = generator;
        this.map = map;
        this.level = level;
    }

    /**
     * Returns an empty world.
     *
     * @return the source
     */
    public static WorldSource empty() {
        return new WorldSource(Kind.EMPTY, null, null, null);
    }

    /**
     * Returns an endless world made of generated chunks.
     *
     * @param generator fills each chunk
     * @return the source
     */
    public static WorldSource generator(ChunkGenerator generator) {
        return new WorldSource(Kind.GENERATOR, generator, null, null);
    }

    /**
     * Returns a world from a Tiled map, saved as XML ({@code .tmx}) or JSON ({@code .tmj}).
     *
     * @param map the {@code .tmx} or {@code .tmj} file; layers may use any Tiled encoding and compression
     * @return the source
     */
    public static WorldSource tiled(AssetKey<String> map) {
        return new WorldSource(Kind.TILED, null, map, null);
    }

    /**
     * Returns a world with every level of an LDtk project, placed where the project puts them.
     *
     * @param project the {@code .ldtk} file
     * @return the source
     */
    public static WorldSource ldtk(AssetKey<String> project) {
        return new WorldSource(Kind.LDTK, null, project, null);
    }

    /**
     * Returns a world with one level of an LDtk project, placed at the origin.
     *
     * @param project the {@code .ldtk} file
     * @param level the level identifier
     * @return the source
     */
    public static WorldSource ldtk(AssetKey<String> project, String level) {
        return new WorldSource(Kind.LDTK, null, project, level);
    }

    /**
     * Sets the world settings; maps override the tile size and orientation with their own.
     *
     * @param value the settings
     * @return this source
     */
    public WorldSource settings(WorldSettings value) {
        this.settings = value;
        return this;
    }

    /**
     * Spawns an entity type for every map object of a type (Tiled class or LDtk identifier) or name.
     *
     * @param objectType the object type or name
     * @param type the entity type
     * @return this source
     */
    public WorldSource spawn(String objectType, EntityType type) {
        spawns.put(objectType, type);
        return this;
    }

    /**
     * Handles objects without a {@link #spawn} mapping.
     *
     * @param value the spawner
     * @return this source
     */
    public WorldSource spawner(ObjectSpawner value) {
        this.spawner = value;
        return this;
    }

    /**
     * Uses a registered tile type for map tiles with a class or type (Tiled) or for an IntGrid value identifier (LDtk).
     * The map keeps drawing its own image; the registered type gives shape, friction and behaviour.
     *
     * @param name the class, type or identifier
     * @param type the tile type
     * @return this source
     */
    public WorldSource tile(String name, TileType type) {
        tiles.put(name, type);
        return this;
    }

    /**
     * Runs code after the world is loaded, before {@code WorldLoadEvent}.
     *
     * @param action the action
     * @return this source
     */
    public WorldSource onLoad(Consumer<World> action) {
        this.onLoad = action;
        return this;
    }

    /**
     * Returns the kind.
     *
     * @return the kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns the generator.
     *
     * @return the generator, or {@code null}
     */
    public @Nullable ChunkGenerator generatorOrNull() {
        return generator;
    }

    /**
     * Returns the map file.
     *
     * @return the file, or {@code null}
     */
    public @Nullable AssetKey<String> mapOrNull() {
        return map;
    }

    /**
     * Returns the LDtk level.
     *
     * @return the identifier, or {@code null} for all levels
     */
    public @Nullable String levelOrNull() {
        return level;
    }

    /**
     * Returns the settings.
     *
     * @return the settings
     */
    public WorldSettings settingsValue() {
        return settings;
    }

    /**
     * Returns the object type mappings.
     *
     * @return object type to entity type
     */
    public Map<String, EntityType> spawns() {
        return Collections.unmodifiableMap(spawns);
    }

    /**
     * Returns the tile mappings.
     *
     * @return name to tile type
     */
    public Map<String, TileType> tiles() {
        return Collections.unmodifiableMap(tiles);
    }

    /**
     * Returns the spawner.
     *
     * @return the spawner, or {@code null}
     */
    public @Nullable ObjectSpawner spawnerOrNull() {
        return spawner;
    }

    /**
     * Returns the load action.
     *
     * @return the action, or {@code null}
     */
    public @Nullable Consumer<World> onLoadOrNull() {
        return onLoad;
    }
}
