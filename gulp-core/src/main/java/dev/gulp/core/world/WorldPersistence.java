package dev.gulp.core.world;

import dev.gulp.api.data.Codec;
import dev.gulp.api.data.DataContainer;
import dev.gulp.api.data.JsonArray;
import dev.gulp.api.data.JsonBoolean;
import dev.gulp.api.data.JsonNumber;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonString;
import dev.gulp.api.data.JsonValue;
import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.ComponentType;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.math.Rect;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.registry.Registry;
import dev.gulp.api.spi.ComponentState;
import dev.gulp.api.world.Chunk;
import dev.gulp.api.world.TileOrientation;
import dev.gulp.api.world.TileType;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSaveEvent;
import dev.gulp.api.world.WorldSettings;
import dev.gulp.core.data.DataContainerImpl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Writes a world into save data and restores it: settings, data, changed chunks (as region objects of 32 × 32 chunks
 * with a palette of tile type keys), tile states and persistent entities with the {@code @Save} fields of their
 * persistent components.
 *
 * <pre>{@code
 * Map<String, JsonObject> regions = new LinkedHashMap<>();
 * JsonObject saved = WorldPersistence.save(world, components, regions);
 * WorldPersistence.restore(freshWorld, saved, regions, components);
 * }</pre>
 */
public final class WorldPersistence {

    /** Chunks per region side. */
    public static final int REGION = 32;

    /** Finds the generated save code of component classes. */
    public interface Components {
        /**
         * Returns the type of a component class.
         *
         * @param type the class
         * @return the type, or {@code null} without {@code @ComponentInfo}
         */
        @Nullable ComponentType type(Class<?> type);

        /**
         * Returns the save code of a component class.
         *
         * @param type the class
         * @return the code, or {@code null}
         */
        @Nullable ComponentState<?> state(Class<?> type);
    }

    private WorldPersistence() {}

    // ================================================================== saving

    /**
     * Writes a world.
     *
     * @param world the world
     * @param components the component save code
     * @param regions receives region objects by name {@code r.<x>.<y>}
     * @return the world object
     */
    public static JsonObject save(World saved, Components components, Map<String, JsonObject> regions) {
        WorldImpl world = (WorldImpl) saved;
        if (world.worlds.events.hasListeners(WorldSaveEvent.class)) {
            world.worlds.events.call(new WorldSaveEvent(world));
        }
        JsonObject.Builder out = JsonObject.builder()
                .put("name", world.name())
                .put("settings", settings(world.settings()))
                .put("data", ((DataContainerImpl) world.data()).toJson());
        TileMapImpl map = world.tileMap;
        List<String> palette = new ArrayList<>();
        Map<Integer, Integer> paletteIndex = new HashMap<>();
        Map<String, JsonObject.Builder> regionBuilders = new LinkedHashMap<>();
        List<ChunkImpl> changed = new ArrayList<>();
        for (ChunkImpl chunk : map.loaded) {
            if (chunk.modified) {
                changed.add(chunk);
            }
        }
        map.forEachKept(changed::add);
        for (ChunkImpl chunk : changed) {
            JsonObject.Builder layers = JsonObject.builder();
            for (TileMapImpl.TileLayerImpl layer : map.layers) {
                int[] cells = layer.index < chunk.cells.length ? chunk.cells[layer.index] : null;
                if (cells == null) {
                    continue;
                }
                JsonArray.Builder values = JsonArray.builder();
                for (int cell : cells) {
                    int id = cell & ChunkImpl.ID_MASK;
                    int mapped = 0;
                    if (id != 0) {
                        Integer known = paletteIndex.get(id);
                        if (known == null) {
                            TileType type = map.typeOf(id);
                            known = palette.size() + 1;
                            palette.add(type == null ? "" : type.key().toString());
                            paletteIndex.put(id, known);
                        }
                        mapped = known;
                    }
                    values.add((long) (mapped | (cell & ~ChunkImpl.ID_MASK)));
                }
                layers.put(layer.name, values.build());
            }
            String region = "r." + Math.floorDiv(chunk.cx, REGION) + "." + Math.floorDiv(chunk.cy, REGION);
            regionBuilders
                    .computeIfAbsent(region, r -> JsonObject.builder())
                    .put(chunk.cx + "," + chunk.cy, layers.build());
        }
        for (Map.Entry<String, JsonObject.Builder> entry : regionBuilders.entrySet()) {
            regions.put(entry.getKey(), entry.getValue().build());
        }
        JsonArray.Builder paletteJson = JsonArray.builder();
        for (String key : palette) {
            paletteJson.add(key);
        }
        out.put("palette", paletteJson.build());
        JsonArray.Builder regionNames = JsonArray.builder();
        for (String name : regionBuilders.keySet()) {
            regionNames.add(name);
        }
        out.put("regions", regionNames.build());
        JsonArray.Builder states = JsonArray.builder();
        for (TileMapImpl.TileLayerImpl layer : map.layers) {
            layer.states.forEachValue(state -> {
                if (!state.data().isEmpty()) {
                    states.add(JsonObject.builder()
                            .put("layer", layer.name)
                            .put("x", state.x())
                            .put("y", state.y())
                            .put("data", ((DataContainerImpl) state.data()).toJson())
                            .build());
                }
            });
        }
        out.put("states", states.build());
        JsonArray.Builder entities = JsonArray.builder();
        for (EntityImpl entity : world.entities) {
            if (entity.isPersistent() && !entity.removed) {
                entities.add(entity(entity, components));
            }
        }
        out.put("entities", entities.build());
        return out.build();
    }

    private static JsonObject settings(WorldSettings settings) {
        JsonObject.Builder out = JsonObject.builder()
                .put("gravity", Codec.VEC2.encode(settings.gravity()))
                .put("tileSize", settings.tileSize())
                .put("ambient", settings.ambientLight().toRgba8888() & 0xFFFFFFFFL)
                .put("seed", settings.seed())
                .put("orientation", settings.orientation().name())
                .put("tickWhenInactive", settings.tickWhenInactive());
        Rect bounds = settings.bounds();
        if (bounds != null) {
            out.put(
                    "bounds",
                    JsonArray.builder()
                            .add(bounds.x())
                            .add(bounds.y())
                            .add(bounds.width())
                            .add(bounds.height())
                            .build());
        }
        return out.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static JsonObject entity(EntityImpl entity, Components components) {
        JsonObject.Builder out = JsonObject.builder()
                .put("type", entity.type.key().toString())
                .put("id", entity.id.toString())
                .put("x", entity.x)
                .put("y", entity.y)
                .put("rotation", entity.rotation)
                .put("sx", entity.scaleX)
                .put("sy", entity.scaleY)
                .put("visible", entity.visible)
                .put("flipX", entity.flipX)
                .put("flipY", entity.flipY)
                .put("data", ((DataContainerImpl) entity.data()).toJson());
        String name = entity.name();
        if (name != null) {
            out.put("name", name);
        }
        JsonArray.Builder tags = JsonArray.builder();
        for (String tag : entity.tags.tags) {
            tags.add(tag);
        }
        out.put("tags", tags.build());
        JsonObject.Builder saved = JsonObject.builder();
        for (int i = 0; i < entity.componentCount; i++) {
            Component component = entity.components[i];
            ComponentType type = components.type(component.getClass());
            ComponentState state = components.state(component.getClass());
            if (type != null && type.persistent() && state != null) {
                saved.put(type.key().toString(), state.save(component));
            }
        }
        out.put("components", saved.build());
        return out.build();
    }

    // ================================================================== loading

    /**
     * Reads the settings of a saved world, for worlds created without a source.
     *
     * @param world the saved world object
     * @return the settings, persistent
     */
    public static WorldSettings settingsOf(JsonObject world) {
        JsonObject json = world.getOrThrow("settings").asObject();
        WorldSettings settings = WorldSettings.DEFAULT
                .gravity(Codec.VEC2.decode(json.getOrThrow("gravity")))
                .tileSize(number(json, "tileSize", 16).intValue())
                .ambientLight(
                        Color.rgba((int) number(json, "ambient", 0xFFFFFFFFL).longValue()))
                .seed(number(json, "seed", 0).longValue())
                .orientation(TileOrientation.valueOf(string(json, "orientation", "ORTHOGONAL")))
                .tickWhenInactive(bool(json, "tickWhenInactive"))
                .persistent(true);
        JsonValue bounds = json.get("bounds");
        if (bounds instanceof JsonArray array && array.size() == 4) {
            settings = settings.bounds(new Rect(
                    (float) ((JsonNumber) array.get(0)).doubleValue(),
                    (float) ((JsonNumber) array.get(1)).doubleValue(),
                    (float) ((JsonNumber) array.get(2)).doubleValue(),
                    (float) ((JsonNumber) array.get(3)).doubleValue()));
        }
        return settings;
    }

    /**
     * Restores a saved world into a freshly loaded one: data, changed chunks, tile states and persistent entities
     * (the persistent entities the source spawned are removed first).
     *
     * @param world the world, just loaded from its source or created
     * @param saved the saved world object
     * @param regions the saved region objects by name
     * @param components the component save code
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void restore(World target, JsonObject saved, Map<String, JsonObject> regions, Components components) {
        WorldImpl world = (WorldImpl) target;
        ((DataContainerImpl) world.data()).replaceWith(saved.getOrThrow("data").asObject());
        TileMapImpl map = world.tileMap;
        Registry<TileType> tileTypes = world.worlds.engine.registries().get(Registries.TILE_TYPE);
        List<TileType> palette = new ArrayList<>();
        palette.add(null);
        for (JsonValue key : saved.getOrThrow("palette").asArray()) {
            String text = ((JsonString) key).value();
            TileType type = text.isEmpty() ? null : map.typeByKey(text);
            if (type == null && !text.isEmpty()) {
                type = tileTypes.get(Key.parse(text));
                if (type == null) {
                    world.worlds.logger.warn("Saved tile type " + text + " is unknown; its tiles are dropped");
                }
            }
            palette.add(type);
        }
        for (JsonValue name : saved.getOrThrow("regions").asArray()) {
            JsonObject region = regions.get(((JsonString) name).value());
            if (region == null) {
                world.worlds.logger.warn("Region " + name + " of world " + world.name() + " is missing");
                continue;
            }
            for (String chunkName : region.names()) {
                int comma = chunkName.indexOf(',');
                int cx = Integer.parseInt(chunkName.substring(0, comma));
                int cy = Integer.parseInt(chunkName.substring(comma + 1));
                JsonObject layers = region.getOrThrow(chunkName).asObject();
                for (String layerName : layers.names()) {
                    TileMapImpl.TileLayerImpl layer = map.layerOrCreate(layerName);
                    JsonArray cells = layers.getOrThrow(layerName).asArray();
                    for (int i = 0; i < cells.size() && i < ChunkImpl.CELLS; i++) {
                        int cell = (int) ((JsonNumber) cells.get(i)).longValue();
                        int index = cell & ChunkImpl.ID_MASK;
                        TileType type = index < palette.size() ? palette.get(index) : null;
                        int value = type == null ? 0 : map.idOf(type) | (cell & ~ChunkImpl.ID_MASK);
                        map.writeCell(
                                layer, cx * Chunk.SIZE + i % Chunk.SIZE, cy * Chunk.SIZE + i / Chunk.SIZE, value, true);
                    }
                }
            }
        }
        for (JsonValue value : saved.getOrThrow("states").asArray()) {
            JsonObject state = value.asObject();
            TileMapImpl.TileLayerImpl layer = map.layerOrCreate(string(state, "layer", ""));
            DataContainer data = map.state(
                            layer,
                            number(state, "x", 0).intValue(),
                            number(state, "y", 0).intValue())
                    .data();
            ((DataContainerImpl) data).replaceWith(state.getOrThrow("data").asObject());
        }
        for (EntityImpl entity : new ArrayList<>(world.entities)) {
            if (entity.isPersistent()) {
                entity.remove();
            }
        }
        world.flushRemovals();
        Registry<EntityType> types = world.worlds.engine.registries().get(Registries.ENTITY_TYPE);
        for (JsonValue value : saved.getOrThrow("entities").asArray()) {
            JsonObject json = value.asObject();
            String typeKey = string(json, "type", "");
            EntityType type = types.get(Key.parse(typeKey));
            if (type == null) {
                world.worlds.logger.warn("Saved entity type " + typeKey + " is not registered; the entity is dropped");
                continue;
            }
            world.spawn(
                    type,
                    (float) number(json, "x", 0).doubleValue(),
                    (float) number(json, "y", 0).doubleValue(),
                    e -> {
                        EntityImpl impl = (EntityImpl) e;
                        impl.id = UUID.fromString(
                                string(json, "id", UUID.randomUUID().toString()));
                        impl.rotation = (float) number(json, "rotation", 0).doubleValue();
                        impl.prevRotation = impl.rotation;
                        impl.scaleX = (float) number(json, "sx", 1).doubleValue();
                        impl.scaleY = (float) number(json, "sy", 1).doubleValue();
                        impl.visible = !json.has("visible") || bool(json, "visible");
                        impl.flipX = bool(json, "flipX");
                        impl.flipY = bool(json, "flipY");
                        String name = json.get("name") instanceof JsonString text ? text.value() : null;
                        if (name != null) {
                            impl.setName(name);
                        }
                        for (String tag : List.copyOf(impl.tags.tags)) {
                            impl.tags.remove(tag);
                        }
                        for (JsonValue tag : json.getOrThrow("tags").asArray()) {
                            impl.tags.add(((JsonString) tag).value());
                        }
                        ((DataContainerImpl) impl.data())
                                .replaceWith(json.getOrThrow("data").asObject());
                        JsonObject savedComponents =
                                json.getOrThrow("components").asObject();
                        for (int i = 0; i < impl.componentCount; i++) {
                            Component component = impl.components[i];
                            ComponentType componentType = components.type(component.getClass());
                            ComponentState state = components.state(component.getClass());
                            if (componentType == null || state == null) {
                                continue;
                            }
                            JsonValue fields =
                                    savedComponents.get(componentType.key().toString());
                            if (fields instanceof JsonObject object) {
                                state.load(component, object);
                            }
                        }
                    });
        }
    }

    private static JsonNumber number(JsonObject json, String name, long fallback) {
        return json.get(name) instanceof JsonNumber number ? number : JsonNumber.of(fallback);
    }

    private static String string(JsonObject json, String name, String fallback) {
        return json.get(name) instanceof JsonString text ? text.value() : fallback;
    }

    private static boolean bool(JsonObject json, String name) {
        return json.get(name) instanceof JsonBoolean value && value.value();
    }
}
