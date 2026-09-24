package dev.gulp.core.world;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.data.DataType;
import dev.gulp.api.data.JsonArray;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonString;
import dev.gulp.api.data.JsonValue;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.registry.Key;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.api.world.MapObject;
import dev.gulp.api.world.ObjectSpawner;
import dev.gulp.api.world.TileOrientation;
import dev.gulp.api.world.TileSet;
import dev.gulp.api.world.TileShape;
import dev.gulp.api.world.TileType;
import dev.gulp.api.world.WorldSource;
import dev.gulp.core.data.JsonReader;
import dev.gulp.core.util.Inflate;
import dev.gulp.core.util.Zstd;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Loads Tiled ({@code .tmx} or {@code .tmj}, with embedded or external {@code .tsx}/{@code .tsj} tile sets) and LDtk
 * ({@code .ldtk}) maps into a world: tile layers in every Tiled encoding and compression, animated tiles, flips, custom
 * properties, object and entity layers, image layers as parallax. Tiled XML goes through {@link TiledXml} into the same
 * tree as JSON. Positions are converted from pixels to tiles by the map tile width.
 */
final class MapLoaders {

    private MapLoaders() {}

    // ------------------------------------------------------------------ shared

    /** Resolves a path written in a map relative to the map file into an asset key text. */
    static String relative(AssetKey<String> map, String path) {
        String namespace = map.key().namespace();
        String mapPath = map.key().path();
        String folder = mapPath.contains("/") ? mapPath.substring(0, mapPath.lastIndexOf('/') + 1) : "";
        List<String> parts = new ArrayList<>();
        for (String part : (folder + path.replace('\\', '/')).split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!parts.isEmpty()) {
                    parts.remove(parts.size() - 1);
                }
                continue;
            }
            parts.add(part);
        }
        String joined = String.join("/", parts);
        if (!joined.equals(joined.toLowerCase(Locale.ROOT)) || joined.contains(" ")) {
            throw new IllegalArgumentException("Map " + map.key() + " refers to '" + path
                    + "'; asset file names must be lower case without spaces ([a-z0-9_./-])");
        }
        return namespace + ":" + joined;
    }

    private static String sanitize(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            out.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-' ? c : '_');
        }
        return out.isEmpty() ? "_" : out.toString();
    }

    private static String text(@Nullable JsonValue value) {
        if (value == null || value.isNull()) {
            return "";
        }
        return value instanceof JsonString string ? string.value() : value.toString();
    }

    private static String string(JsonObject object, String name, String fallback) {
        JsonValue value = object.get(name);
        return value == null || value.isNull() ? fallback : value.asString();
    }

    private static int integer(JsonObject object, String name, int fallback) {
        JsonValue value = object.get(name);
        return value == null || value.isNull() ? fallback : value.asInt();
    }

    private static float number(JsonObject object, String name, float fallback) {
        JsonValue value = object.get(name);
        return value == null || value.isNull() ? fallback : value.asFloat();
    }

    private static boolean flag(JsonObject object, String name, boolean fallback) {
        JsonValue value = object.get(name);
        return value == null || value.isNull() ? fallback : value.asBoolean();
    }

    private static JsonArray array(JsonObject object, String name) {
        JsonValue value = object.get(name);
        return value == null || value.isNull() ? new JsonArray(List.of()) : value.asArray();
    }

    /** Properties of a Tiled object, layer or tile. */
    private static Map<String, String> tiledProperties(JsonObject object) {
        Map<String, String> result = new LinkedHashMap<>();
        JsonArray properties = array(object, "properties");
        for (int i = 0; i < properties.size(); i++) {
            JsonObject property = properties.get(i).asObject();
            result.put(string(property, "name", ""), text(property.get("value")));
        }
        return result;
    }

    /** Builds the tile type for a map tile, taking shape and behaviour from a registered type when mapped. */
    private static TileType mapTile(
            String key,
            TileSet set,
            int index,
            int[] frames,
            float frameSeconds,
            Map<String, String> properties,
            @Nullable TileType mapped) {
        TileType.Builder builder = TileType.builder(Key.of(Key.RESERVED, key));
        if (frames.length > 1) {
            builder.animation(set, frameSeconds, frames);
        } else {
            builder.tileSet(set, index);
        }
        properties.forEach(builder::property);
        if (mapped != null) {
            builder.shape(mapped.shape()).friction(mapped.friction());
            mapped.properties().forEach(builder::property);
            if (mapped.interaction() != null) {
                builder.onInteract(mapped.interaction());
            }
            if (mapped.randomTick() != null) {
                builder.tickRandomly(mapped.randomTickChance(), mapped.randomTick());
            }
            if (mapped.periodicTick() != null) {
                builder.tickEvery(mapped.tickPeriod(), mapped.periodicTick());
            }
            builder.stateful(mapped.isStateful());
        }
        String shapeName = properties.getOrDefault("shape", properties.get("collision"));
        if (shapeName != null) {
            TileShape shape = TileShape.byName(shapeName.toLowerCase(Locale.ROOT));
            if (shape != null) {
                builder.shape(shape);
            }
        }
        return builder.build();
    }

    /** Spawns map objects through the source's mappings and spawner; properties go into entity data. */
    private static void spawnObjects(WorldsImpl worlds, WorldImpl world, WorldSource source, List<MapObject> objects) {
        Map<String, EntityType> spawns = source.spawns();
        ObjectSpawner spawner = source.spawnerOrNull();
        String namespace = worlds.engine.game().id();
        for (MapObject object : objects) {
            Entity entity = null;
            EntityType type = spawns.get(object.type());
            if (type == null && !object.name().isEmpty()) {
                type = spawns.get(object.name());
            }
            if (type != null) {
                entity = world.spawn(type, object.x(), object.y(), e -> {
                    if (!object.name().isEmpty() && world.entity(object.name()) == null) {
                        e.setName(object.name());
                    }
                });
            } else if (spawner != null) {
                entity = spawner.spawn(world, object);
            }
            if (entity != null && !entity.isRemoved()) {
                for (Map.Entry<String, String> property : object.properties().entrySet()) {
                    entity.data()
                            .set(Key.of(namespace, sanitize(property.getKey())), DataType.STRING, property.getValue());
                }
            }
        }
    }

    // ------------------------------------------------------------------ Tiled

    /** A Tiled tile set: gid range, image layout and per-tile data. */
    private static final class TiledSet {
        int firstGid;
        int count;

        @Nullable TileSet sheet;

        String name = "";
        final Map<Integer, JsonObject> tiles = new HashMap<>();
        final Map<Integer, AssetKey<Texture>> images = new HashMap<>();
        final Map<Integer, int[]> imageSizes = new HashMap<>();
    }

    static Promise<Void> tiled(WorldsImpl worlds, WorldImpl world, WorldSource source) {
        AssetKey<String> mapKey = java.util.Objects.requireNonNull(source.mapOrNull());
        return worlds.assets.load(mapKey).flatMap(text -> {
            JsonObject map = TiledXml.isXml(text)
                    ? TiledXml.map(text)
                    : JsonReader.parse(text).asObject();
            List<Promise<JsonObject>> sets = new ArrayList<>();
            JsonArray setArray = array(map, "tilesets");
            for (int i = 0; i < setArray.size(); i++) {
                JsonObject set = setArray.get(i).asObject();
                String external = string(set, "source", "");
                if (external.isEmpty()) {
                    sets.add(done(worlds, set));
                } else {
                    int firstGid = integer(set, "firstgid", 1);
                    sets.add(worlds.assets
                            .load(AssetKey.text(relative(mapKey, external)))
                            .map(content -> (TiledXml.isXml(content)
                                            ? TiledXml.tileset(content)
                                            : JsonReader.parse(content).asObject())
                                    .with("firstgid", dev.gulp.api.data.JsonNumber.of(firstGid))));
                }
            }
            return worlds.assets
                    .all(sets)
                    .flatMap(loadedSets -> buildTiled(worlds, world, source, mapKey, map, loadedSets));
        });
    }

    private static Promise<JsonObject> done(WorldsImpl worlds, JsonObject value) {
        var promise = worlds.assets.<JsonObject>newPromise();
        promise.complete(value);
        return promise;
    }

    private static Promise<Void> buildTiled(
            WorldsImpl worlds,
            WorldImpl world,
            WorldSource source,
            AssetKey<String> mapKey,
            JsonObject map,
            List<JsonObject> setJsons) {
        int tileWidth = integer(map, "tilewidth", world.settings().tileSize());
        int tileHeight = integer(map, "tileheight", tileWidth);
        TileMapImpl tiles = world.tileMap;
        tiles.setTileSize(tileWidth, tileHeight);
        String orientation = string(map, "orientation", "orthogonal");
        String staggerAxis = string(map, "staggeraxis", "y");
        tiles.setOrientation(
                switch (orientation) {
                    case "isometric" -> TileOrientation.ISOMETRIC;
                    case "staggered" -> TileOrientation.ISOMETRIC_STAGGERED;
                    case "hexagonal" ->
                        staggerAxis.equals("x") ? TileOrientation.HEXAGONAL_FLAT : TileOrientation.HEXAGONAL_POINTY;
                    default -> TileOrientation.ORTHOGONAL;
                });
        List<TiledSet> sets = new ArrayList<>();
        List<Promise<Texture>> textures = new ArrayList<>();
        for (JsonObject json : setJsons) {
            TiledSet set = new TiledSet();
            set.firstGid = integer(json, "firstgid", 1);
            set.count = integer(json, "tilecount", 0);
            set.name = sanitize(string(json, "name", "set" + set.firstGid));
            String image = string(json, "image", "");
            if (!image.isEmpty()) {
                AssetKey<Texture> texture = AssetKey.texture(relative(mapKey, image));
                set.sheet = new TileSet(
                        texture,
                        integer(json, "tilewidth", tileWidth),
                        integer(json, "tileheight", tileHeight),
                        integer(json, "margin", 0),
                        integer(json, "spacing", 0));
                textures.add(worlds.assets.load(texture));
            }
            JsonArray tileArray = array(json, "tiles");
            for (int i = 0; i < tileArray.size(); i++) {
                JsonObject tile = tileArray.get(i).asObject();
                int id = integer(tile, "id", 0);
                set.tiles.put(id, tile);
                String tileImage = string(tile, "image", "");
                if (!tileImage.isEmpty()) {
                    AssetKey<Texture> texture = AssetKey.texture(relative(mapKey, tileImage));
                    set.images.put(id, texture);
                    set.imageSizes.put(id, new int[] {
                        integer(tile, "imagewidth", tileWidth), integer(tile, "imageheight", tileHeight)
                    });
                    textures.add(worlds.assets.load(texture));
                }
            }
            sets.add(set);
        }
        sets.sort(java.util.Comparator.comparingInt(s -> s.firstGid));
        return worlds.assets.all(textures).map(ignored -> {
            Map<Integer, TileType> byGid = new HashMap<>();
            List<MapObject> objects = new ArrayList<>();
            int[] z = {0};
            readTiledLayers(
                    worlds,
                    world,
                    source,
                    mapKey,
                    array(map, "layers"),
                    sets,
                    byGid,
                    objects,
                    z,
                    tileWidth,
                    1f,
                    0f,
                    0f);
            spawnObjects(worlds, world, source, objects);
            return null;
        });
    }

    private static @Nullable TileType tiledType(
            int gid, List<TiledSet> sets, Map<Integer, TileType> byGid, AssetKey<String> mapKey, WorldSource source) {
        TileType known = byGid.get(gid);
        if (known != null) {
            return known;
        }
        TiledSet owner = null;
        for (TiledSet set : sets) {
            if (gid >= set.firstGid) {
                owner = set;
            }
        }
        if (owner == null) {
            return null;
        }
        int local = gid - owner.firstGid;
        JsonObject tile = owner.tiles.get(local);
        Map<String, String> properties = tile != null ? tiledProperties(tile) : Map.of();
        String tileClass = tile != null ? string(tile, "type", string(tile, "class", "")) : "";
        TileType mapped = tileClass.isEmpty() ? null : source.tiles().get(tileClass);
        TileSet sheet = owner.sheet;
        int index = local;
        AssetKey<Texture> image = owner.images.get(local);
        if (image != null) {
            int[] size = owner.imageSizes.get(local);
            sheet = TileSet.of(image, size[0], size[1]);
            index = 0;
        }
        if (sheet == null) {
            return null;
        }
        int[] frames = new int[0];
        float frameSeconds = 0.2f;
        if (tile != null) {
            JsonArray animation = array(tile, "animation");
            if (animation.size() > 1 && image == null) {
                frames = new int[animation.size()];
                for (int i = 0; i < animation.size(); i++) {
                    JsonObject frame = animation.get(i).asObject();
                    frames[i] = integer(frame, "tileid", 0);
                    if (i == 0) {
                        frameSeconds = integer(frame, "duration", 200) / 1000f;
                    }
                }
            }
        }
        String key = "tiled/" + sanitize(mapKey.key().path()) + "/" + owner.name + "/" + local;
        TileType type = mapTile(key, sheet, index, frames, frameSeconds, properties, mapped);
        byGid.put(gid, type);
        return type;
    }

    private static void readTiledLayers(
            WorldsImpl worlds,
            WorldImpl world,
            WorldSource source,
            AssetKey<String> mapKey,
            JsonArray layers,
            List<TiledSet> sets,
            Map<Integer, TileType> byGid,
            List<MapObject> objects,
            int[] z,
            int tileWidth,
            float parentOpacity,
            float parentOffsetX,
            float parentOffsetY) {
        TileMapImpl tiles = world.tileMap;
        for (int l = 0; l < layers.size(); l++) {
            JsonObject layer = layers.get(l).asObject();
            String type = string(layer, "type", "");
            String name = string(layer, "name", "layer" + l);
            float opacity = parentOpacity * number(layer, "opacity", 1f);
            float offsetX = parentOffsetX + number(layer, "offsetx", 0f);
            float offsetY = parentOffsetY + number(layer, "offsety", 0f);
            Map<String, String> properties = tiledProperties(layer);
            switch (type) {
                case "group" ->
                    readTiledLayers(
                            worlds,
                            world,
                            source,
                            mapKey,
                            array(layer, "layers"),
                            sets,
                            byGid,
                            objects,
                            z,
                            tileWidth,
                            opacity,
                            offsetX,
                            offsetY);
                case "tilelayer" -> {
                    TileMapImpl.TileLayerImpl target = tiles.addLayer(name, z[0]++ * 10);
                    target.setVisible(flag(layer, "visible", true));
                    target.setTint(Color.WHITE.withAlpha(opacity));
                    target.setParallax(new Vec2(number(layer, "parallaxx", 1f), number(layer, "parallaxy", 1f)));
                    String renderLayer = properties.get("renderLayer");
                    if (renderLayer != null) {
                        target.setRenderLayer(renderLayer);
                    }
                    if (properties.containsKey("collision")) {
                        target.setCollision(Boolean.parseBoolean(properties.get("collision")));
                    }
                    JsonArray chunkArray = array(layer, "chunks");
                    if (chunkArray.size() > 0) {
                        for (int c = 0; c < chunkArray.size(); c++) {
                            JsonObject chunk = chunkArray.get(c).asObject();
                            writeTiled(
                                    tiles,
                                    target,
                                    chunk,
                                    layer,
                                    integer(chunk, "x", 0),
                                    integer(chunk, "y", 0),
                                    integer(chunk, "width", 0),
                                    sets,
                                    byGid,
                                    mapKey,
                                    source);
                        }
                    } else {
                        writeTiled(
                                tiles,
                                target,
                                layer,
                                layer,
                                0,
                                0,
                                integer(layer, "width", 0),
                                sets,
                                byGid,
                                mapKey,
                                source);
                    }
                }
                case "objectgroup" -> {
                    JsonArray list = array(layer, "objects");
                    for (int i = 0; i < list.size(); i++) {
                        JsonObject object = list.get(i).asObject();
                        float width = number(object, "width", 0f);
                        float height = number(object, "height", 0f);
                        float x = number(object, "x", 0f) + offsetX;
                        float y = number(object, "y", 0f) + offsetY;
                        if (object.has("gid")) {
                            y -= height;
                        }
                        objects.add(new MapObject(
                                string(object, "name", ""),
                                string(object, "type", string(object, "class", "")),
                                name,
                                (x + width / 2f) / tileWidth,
                                (y + height / 2f) / tileWidth,
                                width / tileWidth,
                                height / tileWidth,
                                tiledProperties(object)));
                    }
                }
                case "imagelayer" -> {
                    String image = string(layer, "image", "");
                    if (!image.isEmpty()) {
                        var parallax = world.parallax()
                                .layer(AssetKey.texture(relative(mapKey, image)), number(layer, "parallaxx", 1f))
                                .factor(number(layer, "parallaxx", 1f), number(layer, "parallaxy", 1f))
                                .offset(offsetX / tileWidth, offsetY / tileWidth)
                                .tint(Color.WHITE.withAlpha(opacity));
                        if (flag(layer, "repeatx", false)) {
                            parallax.repeatX();
                        }
                        if (flag(layer, "repeaty", false)) {
                            parallax.repeatY();
                        }
                    }
                }
                default -> worlds.logger.warn("Skipping Tiled layer '" + name + "' of unknown type '" + type + "'");
            }
        }
    }

    private static void writeTiled(
            TileMapImpl tiles,
            TileMapImpl.TileLayerImpl target,
            JsonObject holder,
            JsonObject layer,
            int originX,
            int originY,
            int width,
            List<TiledSet> sets,
            Map<Integer, TileType> byGid,
            AssetKey<String> mapKey,
            WorldSource source) {
        int[] gids = tiledData(holder, layer);
        for (int i = 0; i < gids.length; i++) {
            int raw = gids[i];
            int gid = raw & ChunkImpl.ID_MASK;
            if (gid == 0) {
                continue;
            }
            TileType type = tiledType(gid, sets, byGid, mapKey, source);
            if (type == null) {
                continue;
            }
            int flags = raw & ~ChunkImpl.ID_MASK;
            tiles.writeCell(target, originX + i % width, originY + i / width, tiles.idOf(type) | flags, false);
        }
    }

    private static int[] tiledData(JsonObject holder, JsonObject layer) {
        JsonValue data = holder.get("data");
        if (data == null) {
            return new int[0];
        }
        if (data instanceof JsonArray array) {
            int[] result = new int[array.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = (int) array.get(i).asLong();
            }
            return result;
        }
        if (!string(layer, "encoding", "csv").equals("base64")) {
            throw new IllegalArgumentException("Unsupported Tiled layer encoding: " + string(layer, "encoding", ""));
        }
        byte[] bytes = base64(data.asString());
        String compression = string(layer, "compression", "");
        bytes = switch (compression) {
            case "" -> bytes;
            case "zlib" -> Inflate.zlib(bytes);
            case "gzip" -> Inflate.gzip(bytes);
            case "zstd" -> Zstd.decompress(bytes);
            default -> throw new IllegalArgumentException("Unsupported Tiled layer compression: " + compression);
        };
        int[] result = new int[bytes.length / 4];
        for (int i = 0; i < result.length; i++) {
            result[i] = (bytes[i * 4] & 0xFF)
                    | (bytes[i * 4 + 1] & 0xFF) << 8
                    | (bytes[i * 4 + 2] & 0xFF) << 16
                    | (bytes[i * 4 + 3] & 0xFF) << 24;
        }
        return result;
    }

    /** Decodes standard Base64, skipping whitespace such as the line breaks in {@code .tmx} files. */
    static byte[] base64(String text) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        int symbols = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '=') {
                break;
            }
            if (!Character.isWhitespace(c)) {
                symbols++;
            }
        }
        byte[] out = new byte[symbols * 6 / 8];
        int bits = 0;
        int count = 0;
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '=') {
                break;
            }
            if (Character.isWhitespace(c)) {
                continue;
            }
            int value = alphabet.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base64 character '" + c + "'");
            }
            bits = (bits << 6) | value;
            count += 6;
            if (count >= 8) {
                count -= 8;
                if (n < out.length) {
                    out[n++] = (byte) (bits >> count);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ LDtk

    static Promise<Void> ldtk(WorldsImpl worlds, WorldImpl world, WorldSource source) {
        AssetKey<String> projectKey = java.util.Objects.requireNonNull(source.mapOrNull());
        return worlds.assets.load(projectKey).flatMap(text -> {
            JsonObject project = JsonReader.parse(text).asObject();
            String wanted = source.levelOrNull();
            List<Promise<JsonObject>> levels = new ArrayList<>();
            JsonArray levelArray = array(project, "levels");
            for (int i = 0; i < levelArray.size(); i++) {
                JsonObject level = levelArray.get(i).asObject();
                if (wanted != null && !string(level, "identifier", "").equals(wanted)) {
                    continue;
                }
                JsonValue instances = level.get("layerInstances");
                String external = string(level, "externalRelPath", "");
                if ((instances == null || instances.isNull()) && !external.isEmpty()) {
                    levels.add(worlds.assets
                            .load(AssetKey.text(relative(projectKey, external)))
                            .map(json -> JsonReader.parse(json).asObject()));
                } else {
                    levels.add(done(worlds, level));
                }
            }
            if (wanted != null && levels.isEmpty()) {
                throw new IllegalArgumentException(
                        "LDtk project " + projectKey.key() + " has no level '" + wanted + "'");
            }
            return worlds.assets
                    .all(levels)
                    .flatMap(loadedLevels -> buildLdtk(worlds, world, source, projectKey, project, loadedLevels));
        });
    }

    private static Promise<Void> buildLdtk(
            WorldsImpl worlds,
            WorldImpl world,
            WorldSource source,
            AssetKey<String> projectKey,
            JsonObject project,
            List<JsonObject> levels) {
        int grid = integer(project, "defaultGridSize", world.settings().tileSize());
        TileMapImpl tiles = world.tileMap;
        tiles.setTileSize(grid, grid);
        JsonObject defs =
                project.get("defs") != null ? project.getOrThrow("defs").asObject() : JsonObject.EMPTY;
        Map<Integer, TileSet> tileSets = new HashMap<>();
        List<Promise<Texture>> textures = new ArrayList<>();
        JsonArray setArray = array(defs, "tilesets");
        for (int i = 0; i < setArray.size(); i++) {
            JsonObject set = setArray.get(i).asObject();
            String relPath = string(set, "relPath", "");
            if (relPath.isEmpty()) {
                continue;
            }
            AssetKey<Texture> texture = AssetKey.texture(relative(projectKey, relPath));
            int size = integer(set, "tileGridSize", grid);
            tileSets.put(
                    integer(set, "uid", -1),
                    new TileSet(texture, size, size, integer(set, "padding", 0), integer(set, "spacing", 0)));
            textures.add(worlds.assets.load(texture));
        }
        Map<Integer, Map<Integer, String>> intGridNames = new HashMap<>();
        JsonArray layerDefs = array(defs, "layers");
        for (int i = 0; i < layerDefs.size(); i++) {
            JsonObject def = layerDefs.get(i).asObject();
            Map<Integer, String> values = new HashMap<>();
            JsonArray intValues = array(def, "intGridValues");
            for (int v = 0; v < intValues.size(); v++) {
                JsonObject value = intValues.get(v).asObject();
                values.put(integer(value, "value", 0), string(value, "identifier", ""));
            }
            intGridNames.put(integer(def, "uid", -1), values);
        }
        boolean single = source.levelOrNull() != null;
        return worlds.assets.all(textures).map(ignored -> {
            Map<String, TileType> types = new HashMap<>();
            List<MapObject> objects = new ArrayList<>();
            for (JsonObject level : levels) {
                int levelX = single ? 0 : Math.floorDiv(integer(level, "worldX", 0), grid);
                int levelY = single ? 0 : Math.floorDiv(integer(level, "worldY", 0), grid);
                JsonArray instances = array(level, "layerInstances");
                int count = instances.size();
                for (int l = count - 1; l >= 0; l--) {
                    JsonObject layer = instances.get(l).asObject();
                    int zOrder = (count - 1 - l) * 10;
                    readLdtkLayer(
                            world,
                            source,
                            projectKey,
                            layer,
                            zOrder,
                            levelX,
                            levelY,
                            grid,
                            tileSets,
                            intGridNames,
                            types,
                            objects);
                }
            }
            spawnObjects(worlds, world, source, objects);
            return null;
        });
    }

    private static void readLdtkLayer(
            WorldImpl world,
            WorldSource source,
            AssetKey<String> projectKey,
            JsonObject layer,
            int zOrder,
            int levelX,
            int levelY,
            int grid,
            Map<Integer, TileSet> tileSets,
            Map<Integer, Map<Integer, String>> intGridNames,
            Map<String, TileType> types,
            List<MapObject> objects) {
        TileMapImpl tiles = world.tileMap;
        String name = string(layer, "__identifier", "layer");
        String type = string(layer, "__type", "");
        int gridSize = integer(layer, "__gridSize", grid);
        int columns = integer(layer, "__cWid", 0);
        boolean visible = flag(layer, "visible", true);
        float opacity = number(layer, "__opacity", 1f);
        int offsetX = integer(layer, "__pxTotalOffsetX", 0);
        int offsetY = integer(layer, "__pxTotalOffsetY", 0);
        if (type.equals("Entities")) {
            JsonArray entities = array(layer, "entityInstances");
            for (int i = 0; i < entities.size(); i++) {
                JsonObject entity = entities.get(i).asObject();
                JsonArray px = array(entity, "px");
                JsonArray pivot = array(entity, "__pivot");
                float width = number(entity, "width", gridSize);
                float height = number(entity, "height", gridSize);
                float pivotX = pivot.size() > 1 ? pivot.get(0).asFloat() : 0f;
                float pivotY = pivot.size() > 1 ? pivot.get(1).asFloat() : 0f;
                float left = px.get(0).asFloat() - pivotX * width + offsetX;
                float top = px.get(1).asFloat() - pivotY * height + offsetY;
                Map<String, String> fields = new LinkedHashMap<>();
                JsonArray fieldArray = array(entity, "fieldInstances");
                String objectName = "";
                for (int f = 0; f < fieldArray.size(); f++) {
                    JsonObject field = fieldArray.get(f).asObject();
                    String fieldName = string(field, "__identifier", "");
                    String value = text(field.get("__value"));
                    fields.put(fieldName, value);
                    if (fieldName.equalsIgnoreCase("name")) {
                        objectName = value;
                    }
                }
                objects.add(new MapObject(
                        objectName,
                        string(entity, "__identifier", ""),
                        name,
                        levelX + (left + width / 2f) / grid,
                        levelY + (top + height / 2f) / grid,
                        width / grid,
                        height / grid,
                        fields));
            }
            return;
        }
        int cellScale = Math.max(1, gridSize / grid);
        if (type.equals("IntGrid")) {
            JsonArray csv = array(layer, "intGridCsv");
            Map<Integer, String> names = intGridNames.getOrDefault(integer(layer, "layerDefUid", -1), Map.of());
            TileMapImpl.TileLayerImpl gridLayer = tiles.addLayer(name + ".grid", zOrder + 1);
            gridLayer.setVisible(false);
            for (int i = 0; i < csv.size(); i++) {
                int value = csv.get(i).asInt();
                if (value == 0) {
                    continue;
                }
                String valueName = names.getOrDefault(value, Integer.toString(value));
                String typeKey = "ldtk/" + sanitize(name) + "/"
                        + sanitize(valueName.isEmpty() ? Integer.toString(value) : valueName);
                TileType tileType = source.tiles().get(valueName.isEmpty() ? Integer.toString(value) : valueName);
                if (tileType == null) {
                    tileType = types.computeIfAbsent(
                            typeKey,
                            k -> TileType.builder(Key.of(Key.RESERVED, k))
                                    .shape(TileShape.FULL)
                                    .property("intgrid", Integer.toString(value))
                                    .build());
                }
                int cx = levelX + (i % columns) * cellScale + offsetX / grid;
                int cy = levelY + (i / columns) * cellScale + offsetY / grid;
                for (int dy = 0; dy < cellScale; dy++) {
                    for (int dx = 0; dx < cellScale; dx++) {
                        tiles.writeCell(gridLayer, cx + dx, cy + dy, tiles.idOf(tileType), false);
                    }
                }
            }
        }
        JsonArray placed = array(layer, type.equals("Tiles") ? "gridTiles" : "autoLayerTiles");
        if (placed.size() == 0) {
            return;
        }
        TileSet set = tileSets.get(integer(layer, "__tilesetDefUid", -1));
        if (set == null) {
            return;
        }
        List<TileMapImpl.TileLayerImpl> stack = new ArrayList<>();
        for (int i = 0; i < placed.size(); i++) {
            JsonObject tile = placed.get(i).asObject();
            JsonArray px = array(tile, "px");
            int tileId = integer(tile, "t", 0);
            int flipBits = integer(tile, "f", 0);
            int x = levelX + Math.floorDiv(px.get(0).asInt() + offsetX, grid);
            int y = levelY + Math.floorDiv(px.get(1).asInt() + offsetY, grid);
            String key = "ldtk/" + sanitize(projectKey.key().path()) + "/"
                    + set.texture().key().path().hashCode() + "/" + tileId;
            TileType tileType =
                    types.computeIfAbsent(key, k -> mapTile(k, set, tileId, new int[0], 0.2f, Map.of(), null));
            int cell = tiles.idOf(tileType)
                    | ((flipBits & 1) != 0 ? ChunkImpl.FLIP_H : 0)
                    | ((flipBits & 2) != 0 ? ChunkImpl.FLIP_V : 0);
            // Auto layers can stack several tiles in one cell: each extra tile goes to the next sub-layer.
            for (int depth = 0; ; depth++) {
                if (depth == stack.size()) {
                    String subName = depth == 0 ? name : name + "#" + (depth + 1);
                    boolean created = tiles.findLayer(subName) == null;
                    TileMapImpl.TileLayerImpl sub = tiles.addLayer(subName, zOrder);
                    if (created) {
                        // Levels share layers by name; the first level sets their look.
                        sub.setVisible(visible);
                        sub.setCollision(false);
                        sub.setTint(Color.WHITE.withAlpha(opacity));
                    }
                    stack.add(sub);
                }
                TileMapImpl.TileLayerImpl sub = stack.get(depth);
                if (tiles.cell(sub, x, y) == 0) {
                    tiles.writeCell(sub, x, y, cell, false);
                    break;
                }
            }
        }
    }
}
