package dev.gulp.core.save;

import dev.gulp.api.Logger;
import dev.gulp.api.data.DataContainer;
import dev.gulp.api.data.JsonArray;
import dev.gulp.api.data.JsonNumber;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonString;
import dev.gulp.api.data.JsonValue;
import dev.gulp.api.entity.ComponentType;
import dev.gulp.api.event.EventPriority;
import dev.gulp.api.event.lifecycle.FocusLostEvent;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.save.GameLoadEvent;
import dev.gulp.api.save.GameSaveEvent;
import dev.gulp.api.save.SaveMetadata;
import dev.gulp.api.save.SaveSlot;
import dev.gulp.api.save.SaveStore;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.api.scheduler.Task;
import dev.gulp.api.spi.ComponentState;
import dev.gulp.api.world.World;
import dev.gulp.core.GulpEngine;
import dev.gulp.core.data.DataContainerImpl;
import dev.gulp.core.module.ModuleManagerImpl;
import dev.gulp.core.scheduler.PromiseImpl;
import dev.gulp.core.world.WorldPersistence;
import dev.gulp.core.world.WorldsImpl;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformFiles;
import dev.gulp.platform.PlatformModules;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * {@link SaveStore}: each slot is {@code saves/<slot>/slot.bin} (game, module and world data), {@code
 * saves/<slot>/meta.bin} (written last, so a slot without it is incomplete) and region files {@code
 * saves/<slot>/regions/<world>/r.<x>.<y>.bin}, all in the tagged binary format of {@link Tags}.
 */
public final class SaveStoreImpl implements SaveStore {

    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]+");
    private static final int THUMBNAIL_WIDTH = 160;

    /** Creates promises owned by the game. */
    public interface Promises {
        /**
         * Creates a promise.
         *
         * @param <T> the result type
         * @return the promise
         */
        <T> PromiseImpl<T> create();

        /**
         * Creates a promise that has already failed; its handlers still run, on the next frame.
         *
         * @param <T> the result type
         * @param error the failure
         * @return the promise
         */
        <T> PromiseImpl<T> failed(Throwable error);
    }

    private final GulpEngine engine;
    private final PlatformFiles files;
    private final WorldsImpl worlds;
    private final ModuleManagerImpl modules;
    private final PlatformModules generated;
    private final Promises promises;
    private final Logger logger;
    private final java.util.function.Function<byte[], Promise<Pixmap>> decoder;
    private final int version;
    private final Map<Integer, Consumer<DataContainer>> migrations = new HashMap<>();
    private final WorldPersistence.Components components;
    private boolean busy;
    private long playTicksBase;
    private long tickAtLoad;
    private @Nullable String autosaveSlot;
    private @Nullable Task autosaveTask;

    /**
     * Creates the store.
     *
     * @param engine the engine
     * @param files user data files
     * @param worlds the worlds
     * @param modules the modules
     * @param generated generated component save code
     * @param promises creates promises
     * @param logger where problems go
     * @param decoder decodes thumbnail PNG files
     * @param version the save version written now
     */
    public SaveStoreImpl(
            GulpEngine engine,
            PlatformFiles files,
            WorldsImpl worlds,
            ModuleManagerImpl modules,
            PlatformModules generated,
            Promises promises,
            Logger logger,
            java.util.function.Function<byte[], Promise<Pixmap>> decoder,
            int version) {
        this.engine = engine;
        this.files = files;
        this.worlds = worlds;
        this.modules = modules;
        this.generated = generated;
        this.promises = promises;
        this.logger = logger;
        this.decoder = decoder;
        this.version = version;
        this.components = new WorldPersistence.Components() {
            @Override
            public @Nullable ComponentType type(Class<?> type) {
                return generated.lookup(ComponentType.class, type);
            }

            @Override
            public @Nullable ComponentState<?> state(Class<?> type) {
                return generated.lookup(ComponentState.class, type);
            }
        };
    }

    /**
     * Returns every component type the processor generated save code for.
     *
     * @return the types
     */
    @SuppressWarnings("unchecked")
    public List<ComponentType> componentTypes() {
        List<ComponentType> types = generated.lookup(List.class, ComponentType.class);
        return types == null ? List.of() : types;
    }

    /**
     * Returns the saved fields of a component, for the entity inspector.
     *
     * @param component the component
     * @return its {@code @Save} fields, or {@code null} without generated save code
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public @Nullable JsonObject describe(dev.gulp.api.entity.Component component) {
        ComponentState state = components.state(component.getClass());
        if (state == null) {
            return null;
        }
        try {
            return state.save(component);
        } catch (RuntimeException error) {
            return null;
        }
    }

    /**
     * Saves when a web tab is hidden, if autosave is on; closing the tab does not run {@code onStop}.
     *
     * @param owner the engine owner
     */
    public void start(dev.gulp.api.Owner owner) {
        if (engine.platform().isWeb()) {
            owner.on(FocusLostEvent.class, EventPriority.MONITOR, e -> {
                String slot = autosaveSlot;
                if (slot != null && !busy) {
                    slot(slot).save();
                }
            });
        }
    }

    // ================================================================== SaveStore

    @Override
    public SaveSlot slot(String name) {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Slot names use [a-z0-9_-]: '" + name + "'");
        }
        return new Slot(name);
    }

    @Override
    public Promise<List<SaveMetadata>> list() {
        PromiseImpl<List<SaveMetadata>> promise = promises.create();
        files.listUserData(
                "saves/",
                callback(
                        names -> {
                            Set<String> slots = new LinkedHashSet<>();
                            for (String name : names) {
                                String rest = name.substring("saves/".length());
                                int slash = rest.indexOf('/');
                                if (slash > 0 && rest.substring(slash + 1).equals("meta.bin")) {
                                    slots.add(rest.substring(0, slash));
                                }
                            }
                            List<SaveMetadata> result = new ArrayList<>();
                            if (slots.isEmpty()) {
                                promise.complete(result);
                                return;
                            }
                            int[] remaining = {slots.size()};
                            for (String slot : slots) {
                                new Slot(slot)
                                        .metadata()
                                        .thenSync(meta -> {
                                            result.add(meta);
                                            if (--remaining[0] == 0) {
                                                result.sort((a, b) -> Long.compare(b.savedAt(), a.savedAt()));
                                                promise.complete(result);
                                            }
                                        })
                                        .onFailure(error -> {
                                            logger.warn("Cannot read slot " + slot + ": " + error.getMessage());
                                            if (--remaining[0] == 0) {
                                                result.sort((a, b) -> Long.compare(b.savedAt(), a.savedAt()));
                                                promise.complete(result);
                                            }
                                        });
                            }
                        },
                        promise));
        return promise;
    }

    @Override
    public void migration(int fromVersion, Consumer<DataContainer> step) {
        if (fromVersion < 1 || fromVersion >= version) {
            throw new IllegalArgumentException(
                    "Migrations go from 1 to " + (version - 1) + " (the save version is " + version + ")");
        }
        migrations.put(fromVersion, step);
    }

    @Override
    public int version() {
        return version;
    }

    @Override
    public void autosave(String slot, Duration interval) {
        slot(slot);
        stopAutosave();
        autosaveSlot = slot;
        autosaveTask = engine.scheduler().realtime().owner(engine.game()).every(interval, interval, () -> {
            if (!busy) {
                slot(slot).save();
            }
        });
    }

    @Override
    public void stopAutosave() {
        Task task = autosaveTask;
        if (task != null) {
            task.cancel();
        }
        autosaveTask = null;
        autosaveSlot = null;
    }

    @Override
    public @Nullable String autosaveSlot() {
        return autosaveSlot;
    }

    @Override
    public boolean isBusy() {
        return busy;
    }

    // ================================================================== helpers

    private static <T> PlatformCallback<T> callback(Consumer<T> success, PromiseImpl<?> promise) {
        return new PlatformCallback<>() {
            @Override
            public void success(T value) {
                try {
                    success.accept(value);
                } catch (RuntimeException error) {
                    promise.fail(error);
                }
            }

            @Override
            public void failure(Throwable error) {
                promise.fail(error);
            }
        };
    }

    private static String fileName(String worldName) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < worldName.length(); i++) {
            char c = worldName.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-') {
                out.append(c);
            } else {
                out.append('_').append(Integer.toHexString(c)).append('_');
            }
        }
        return out.toString();
    }

    private static byte[] bytes(ByteBuffer buffer) {
        ByteBuffer copy = buffer.duplicate();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }

    private static JsonArray intArray(byte[] bytes) {
        JsonArray.Builder out = JsonArray.builder();
        for (byte b : bytes) {
            out.add(b & 0xFF);
        }
        return out.build();
    }

    private static byte[] byteArray(JsonArray array) {
        byte[] bytes = new byte[array.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((JsonNumber) array.get(i)).intValue();
        }
        return bytes;
    }

    /** Writes files one after another, then completes. */
    private void writeAll(Map<String, byte[]> contents, PromiseImpl<Void> promise, Runnable done) {
        List<Map.Entry<String, byte[]>> entries = new ArrayList<>(contents.entrySet());
        writeFrom(entries, 0, promise, done);
    }

    private void writeFrom(
            List<Map.Entry<String, byte[]>> entries, int index, PromiseImpl<Void> promise, Runnable done) {
        if (index >= entries.size()) {
            done.run();
            return;
        }
        Map.Entry<String, byte[]> entry = entries.get(index);
        files.writeUserData(
                entry.getKey(),
                ByteBuffer.wrap(entry.getValue()),
                callback(ignored -> writeFrom(entries, index + 1, promise, done), promise));
    }

    private void deleteAll(List<String> names, PromiseImpl<?> promise, Runnable done) {
        if (names.isEmpty()) {
            done.run();
            return;
        }
        int[] remaining = {names.size()};
        for (String name : names) {
            files.deleteUserData(
                    name,
                    callback(
                            ignored -> {
                                if (--remaining[0] == 0) {
                                    done.run();
                                }
                            },
                            promise));
        }
    }

    // ================================================================== slot

    /** One slot. */
    private final class Slot implements SaveSlot {
        private final String name;
        private final String prefix;

        Slot(String name) {
            this.name = name;
            this.prefix = "saves/" + name + "/";
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Promise<Void> save() {
            return save(name);
        }

        @Override
        public Promise<Void> save(String title) {
            if (busy) {
                return promises.failed(new IllegalStateException("A save or load is already running"));
            }
            PromiseImpl<Void> promise = promises.create();
            busy = true;
            promise.onFailure(error -> busy = false);
            Map<String, byte[]> contents;
            JsonObject meta;
            try {
                contents = capture();
                meta = JsonObject.builder()
                        .put("slot", name)
                        .put("title", title)
                        .put("savedAt", System.currentTimeMillis())
                        .put("playTicks", playTicksBase + engine.tick() - tickAtLoad)
                        .put("version", version)
                        .build();
            } catch (RuntimeException error) {
                busy = false;
                return promises.failed(error);
            }
            thumbnail(png -> {
                JsonObject finalMeta = png == null ? meta : meta.with("thumbnail", intArray(png));
                // New files first and meta.bin last, then the files the new save no longer has: a save cut short
                // leaves the previous one readable instead of an empty slot.
                files.listUserData(
                        prefix,
                        callback(
                                existing -> {
                                    Map<String, byte[]> ordered = new LinkedHashMap<>(contents);
                                    ordered.put(prefix + "meta.bin", Tags.write(finalMeta));
                                    List<String> stale = new ArrayList<>(existing);
                                    stale.removeAll(ordered.keySet());
                                    writeAll(
                                            ordered,
                                            promise,
                                            () -> deleteAll(stale, promise, () -> {
                                                busy = false;
                                                promise.complete(null);
                                            }));
                                },
                                promise));
            });
            return promise;
        }

        /** Captures the game state at once, on the main thread. */
        private Map<String, byte[]> capture() {
            DataContainerImpl game = new DataContainerImpl(JsonObject.EMPTY);
            if (engine.events().hasListeners(GameSaveEvent.class)) {
                engine.events().call(new GameSaveEvent(this, game));
            }
            JsonObject.Builder moduleData = JsonObject.builder();
            for (Map.Entry<String, DataContainer> entry : modules.allData().entrySet()) {
                moduleData.put(entry.getKey(), ((DataContainerImpl) entry.getValue()).toJson());
            }
            Map<String, byte[]> contents = new LinkedHashMap<>();
            JsonArray.Builder savedWorlds = JsonArray.builder();
            for (World world : worlds.loadedWorlds()) {
                if (!world.settings().persistent()) {
                    continue;
                }
                Map<String, JsonObject> regions = new LinkedHashMap<>();
                savedWorlds.add(WorldPersistence.save(world, components, regions));
                String folder = prefix + "regions/" + fileName(world.name()) + "/";
                for (Map.Entry<String, JsonObject> region : regions.entrySet()) {
                    contents.put(folder + region.getKey() + ".bin", Tags.write(region.getValue()));
                }
            }
            World active = worlds.activeWorld();
            JsonObject.Builder root = JsonObject.builder()
                    .put("version", version)
                    .put("game", game.toJson())
                    .put("modules", moduleData.build())
                    .put("worlds", savedWorlds.build())
                    .put("playTicks", playTicksBase + engine.tick() - tickAtLoad);
            if (active != null) {
                root.put("active", active.name());
            }
            contents.put(prefix + "slot.bin", Tags.write(root.build()));
            return contents;
        }

        private void thumbnail(Consumer<byte @Nullable []> done) {
            if (engine.platform().backend().equals("headless")) {
                done.accept(null);
                return;
            }
            engine.display()
                    .screenshot()
                    .thenSync(image -> {
                        int height = Math.max(1, image.height() * THUMBNAIL_WIDTH / Math.max(1, image.width()));
                        Pixmap small = image.scaled(THUMBNAIL_WIDTH, height);
                        done.accept(small.encodePng());
                    })
                    .onFailure(error -> done.accept(null));
        }

        @Override
        public Promise<Void> load() {
            if (busy) {
                return promises.failed(new IllegalStateException("A save or load is already running"));
            }
            PromiseImpl<Void> promise = promises.create();
            busy = true;
            promise.onFailure(error -> busy = false);
            files.readUserData(
                    prefix + "slot.bin",
                    callback(
                            buffer -> {
                                JsonObject root = Tags.read(bytes(buffer)).asObject();
                                int saved = ((JsonNumber) root.getOrThrow("version")).intValue();
                                if (saved > version) {
                                    throw new IllegalStateException("Slot " + name + " was saved by a newer version ("
                                            + saved + " > " + version + ")");
                                }
                                DataContainerImpl game = new DataContainerImpl(
                                        root.getOrThrow("game").asObject());
                                for (int v = saved; v < version; v++) {
                                    Consumer<DataContainer> step = migrations.get(v);
                                    if (step != null) {
                                        step.accept(game);
                                    }
                                }
                                List<JsonObject> savedWorlds = new ArrayList<>();
                                for (JsonValue world : root.getOrThrow("worlds").asArray()) {
                                    savedWorlds.add(world.asObject());
                                }
                                loadWorlds(savedWorlds, 0, promise, () -> {
                                    JsonObject moduleData =
                                            root.getOrThrow("modules").asObject();
                                    for (Map.Entry<String, DataContainer> entry :
                                            modules.allData().entrySet()) {
                                        JsonValue data = moduleData.get(entry.getKey());
                                        ((DataContainerImpl) entry.getValue())
                                                .replaceWith(
                                                        data instanceof JsonObject object ? object : JsonObject.EMPTY);
                                    }
                                    if (root.get("active") instanceof JsonString active) {
                                        for (World world : worlds.loadedWorlds()) {
                                            if (world.name().equals(active.value())) {
                                                worlds.activateNow(world);
                                            }
                                        }
                                    }
                                    playTicksBase =
                                            root.get("playTicks") instanceof JsonNumber ticks ? ticks.longValue() : 0L;
                                    tickAtLoad = engine.tick();
                                    busy = false;
                                    if (engine.events().hasListeners(GameLoadEvent.class)) {
                                        engine.events().call(new GameLoadEvent(this, game, saved));
                                    }
                                    promise.complete(null);
                                });
                            },
                            promise));
            return promise;
        }

        private void loadWorlds(List<JsonObject> saved, int index, PromiseImpl<Void> promise, Runnable done) {
            if (index >= saved.size()) {
                done.run();
                return;
            }
            JsonObject world = saved.get(index);
            String worldName = ((JsonString) world.getOrThrow("name")).value();
            worlds.reload(worldName, WorldPersistence.settingsOf(world))
                    .thenSync(fresh -> {
                        String folder = prefix + "regions/" + fileName(worldName) + "/";
                        files.listUserData(
                                folder,
                                callback(
                                        names -> {
                                            Map<String, JsonObject> regions = new HashMap<>();
                                            readRegions(names, 0, folder, regions, promise, () -> {
                                                WorldPersistence.restore(fresh, world, regions, components);
                                                loadWorlds(saved, index + 1, promise, done);
                                            });
                                        },
                                        promise));
                    })
                    .onFailure(promise::fail);
        }

        private void readRegions(
                List<String> names,
                int index,
                String folder,
                Map<String, JsonObject> into,
                PromiseImpl<Void> promise,
                Runnable done) {
            if (index >= names.size()) {
                done.run();
                return;
            }
            String file = names.get(index);
            files.readUserData(
                    file,
                    callback(
                            buffer -> {
                                String region = file.substring(folder.length());
                                if (region.endsWith(".bin")) {
                                    region = region.substring(0, region.length() - 4);
                                }
                                into.put(region, Tags.read(bytes(buffer)).asObject());
                                readRegions(names, index + 1, folder, into, promise, done);
                            },
                            promise));
        }

        @Override
        public Promise<Void> delete() {
            PromiseImpl<Void> promise = promises.create();
            files.listUserData(
                    prefix, callback(names -> deleteAll(names, promise, () -> promise.complete(null)), promise));
            return promise;
        }

        @Override
        public Promise<Boolean> exists() {
            PromiseImpl<Boolean> promise = promises.create();
            files.listUserData(
                    prefix, callback(names -> promise.complete(names.contains(prefix + "meta.bin")), promise));
            return promise;
        }

        @Override
        public Promise<SaveMetadata> metadata() {
            PromiseImpl<SaveMetadata> promise = promises.create();
            files.readUserData(
                    prefix + "meta.bin",
                    callback(
                            buffer -> {
                                JsonObject meta = Tags.read(bytes(buffer)).asObject();
                                String title = meta.get("title") instanceof JsonString text ? text.value() : name;
                                long savedAt = ((JsonNumber) meta.getOrThrow("savedAt")).longValue();
                                long ticks = ((JsonNumber) meta.getOrThrow("playTicks")).longValue();
                                int savedVersion = ((JsonNumber) meta.getOrThrow("version")).intValue();
                                JsonValue thumbnail = meta.get("thumbnail");
                                if (thumbnail instanceof JsonArray png) {
                                    decoder.apply(byteArray(png))
                                            .thenSync(image -> promise.complete(
                                                    new SaveMetadata(name, title, savedAt, ticks, savedVersion, image)))
                                            .onFailure(error -> promise.complete(
                                                    new SaveMetadata(name, title, savedAt, ticks, savedVersion, null)));
                                } else {
                                    promise.complete(new SaveMetadata(name, title, savedAt, ticks, savedVersion, null));
                                }
                            },
                            promise));
            return promise;
        }

        @Override
        public Promise<byte[]> exportData() {
            PromiseImpl<byte[]> promise = promises.create();
            files.listUserData(
                    prefix,
                    callback(
                            names -> {
                                if (!names.contains(prefix + "meta.bin")) {
                                    throw new IllegalStateException("Slot " + name + " has no save");
                                }
                                JsonObject.Builder packed = JsonObject.builder();
                                int[] remaining = {names.size()};
                                for (String file : names) {
                                    files.readUserData(
                                            file,
                                            callback(
                                                    buffer -> {
                                                        packed.put(
                                                                file.substring(prefix.length()),
                                                                intArray(bytes(buffer)));
                                                        if (--remaining[0] == 0) {
                                                            promise.complete(Tags.write(JsonObject.builder()
                                                                    .put("gulpSave", 1)
                                                                    .put("files", packed.build())
                                                                    .build()));
                                                        }
                                                    },
                                                    promise));
                                }
                            },
                            promise));
            return promise;
        }

        @Override
        public Promise<Void> importData(byte[] data) {
            JsonObject files;
            try {
                JsonObject root = Tags.read(data).asObject();
                if (!root.has("gulpSave")) {
                    throw new IllegalArgumentException("Not an exported Gulp save");
                }
                files = root.getOrThrow("files").asObject();
                if (!files.has("meta.bin") || !files.has("slot.bin")) {
                    throw new IllegalArgumentException("The export has no slot data");
                }
            } catch (RuntimeException error) {
                return promises.failed(error);
            }
            PromiseImpl<Void> promise = promises.create();
            Map<String, byte[]> contents = new LinkedHashMap<>();
            for (String file : files.names()) {
                if (!file.equals("meta.bin")) {
                    contents.put(prefix + file, byteArray(files.getOrThrow(file).asArray()));
                }
            }
            contents.put(
                    prefix + "meta.bin", byteArray(files.getOrThrow("meta.bin").asArray()));
            SaveStoreImpl.this.files.listUserData(
                    prefix,
                    callback(
                            existing -> {
                                List<String> stale = new ArrayList<>(existing);
                                stale.removeAll(contents.keySet());
                                writeAll(
                                        contents,
                                        promise,
                                        () -> deleteAll(stale, promise, () -> promise.complete(null)));
                            },
                            promise));
            return promise;
        }

        @Override
        public Promise<Void> exportFile() {
            PromiseImpl<Void> promise = promises.create();
            exportData()
                    .thenSync(bytes -> files.offerFile(
                            name + ".gulpsave",
                            ByteBuffer.wrap(bytes),
                            callback(ok -> promise.complete(null), promise)))
                    .onFailure(promise::fail);
            return promise;
        }

        @Override
        public Promise<Void> importFile() {
            PromiseImpl<Void> promise = promises.create();
            files.pickFile(
                    name + ".gulpsave",
                    callback(
                            buffer -> importData(bytes(buffer))
                                    .thenSync(ok -> promise.complete(null))
                                    .onFailure(promise::fail),
                            promise));
            return promise;
        }

        @Override
        public String toString() {
            return "SaveSlot[" + name + "]";
        }
    }
}
