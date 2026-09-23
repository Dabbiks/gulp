package dev.gulp.core.data;

import dev.gulp.api.Owner;
import dev.gulp.api.data.Config;
import dev.gulp.api.data.ConfigReloadEvent;
import dev.gulp.api.data.ConfigSection;
import dev.gulp.api.data.JsonNull;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonParseException;
import dev.gulp.api.data.JsonValue;
import dev.gulp.api.event.Events;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.core.CoreContext;
import dev.gulp.core.MainQueue;
import dev.gulp.core.scheduler.PromiseImpl;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformFiles;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * {@link Config} backed by a defaults asset and a user file. Values are the defaults deep-merged with the user file;
 * saving writes the merged values to the user file.
 */
public final class ConfigImpl extends AbstractSection implements Config {

    private final String name;
    private final String assetPath;
    private final String userPath;
    private final PlatformFiles files;
    private final CoreContext context;
    private final Events events;
    private final Owner owner;
    private final MainQueue mainQueue;
    private JsonObject values = JsonObject.EMPTY;
    private boolean loaded;

    /**
     * Creates an empty config; call {@link #load(Runnable)} to read the files.
     *
     * @param name the file name without extension
     * @param assetPath path of the defaults, relative to the assets root
     * @param userPath path of the user file, relative to the user data directory
     * @param files file access
     * @param context loggers
     * @param events fires {@link ConfigReloadEvent}
     * @param owner the game or module that owns this config
     * @param mainQueue for promise callbacks
     */
    public ConfigImpl(
            String name,
            String assetPath,
            String userPath,
            PlatformFiles files,
            CoreContext context,
            Events events,
            Owner owner,
            MainQueue mainQueue) {
        this.name = name;
        this.assetPath = assetPath;
        this.userPath = userPath;
        this.files = files;
        this.context = context;
        this.events = events;
        this.owner = owner;
        this.mainQueue = mainQueue;
    }

    /**
     * Reads the defaults and the user file. Missing files count as empty; unparsable files are logged and ignored.
     *
     * @param done runs on the main thread when both files were read
     */
    public void load(Runnable done) {
        JsonObject[] parts = {JsonObject.EMPTY, JsonObject.EMPTY};
        int[] remaining = {2};
        Runnable finish = () -> {
            if (--remaining[0] == 0) {
                values = merge(parts[0], parts[1]);
                loaded = true;
                done.run();
            }
        };
        files.readAsset(assetPath, reader(assetPath, parts, 0, finish));
        files.readUserData(userPath, reader(userPath, parts, 1, finish));
    }

    private PlatformCallback<ByteBuffer> reader(String path, JsonObject[] parts, int slot, Runnable finish) {
        return new PlatformCallback<>() {
            @Override
            public void success(ByteBuffer bytes) {
                byte[] data = new byte[bytes.remaining()];
                bytes.duplicate().get(data);
                try {
                    JsonValue parsed = YamlReader.parse(new String(data, StandardCharsets.UTF_8));
                    if (parsed instanceof JsonObject object) {
                        parts[slot] = object;
                    } else if (!parsed.isNull()) {
                        context.loggerOf(owner).error("Config " + path + " must be a map of keys to values");
                    }
                } catch (JsonParseException e) {
                    context.loggerOf(owner).error("Cannot read config " + path + ": " + e.getMessage());
                }
                finish.run();
            }

            @Override
            public void failure(Throwable error) {
                if (!(error instanceof FileNotFoundException)) {
                    context.loggerOf(owner).warn("Cannot read config " + path, error);
                }
                finish.run();
            }
        };
    }

    /**
     * Returns whether the files were read at least once.
     *
     * @return {@code true} after the first load
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Returns the asset path of the defaults, for hot reload.
     *
     * @return the path inside the assets folder
     */
    public String assetPath() {
        return assetPath;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String path() {
        return "";
    }

    @Override
    JsonValue rootValue() {
        return values;
    }

    @Override
    public @Nullable JsonValue getValue(String path) {
        return walk(values, path);
    }

    @Override
    public @Nullable ConfigSection getSection(String path) {
        return getValue(path) instanceof JsonObject ? new Section(this, path) : null;
    }

    @Override
    public void set(String path, @Nullable Object value) {
        JsonValue json = JsonValue.of(value);
        values = (JsonObject) with(values, path.split("\\."), 0, json.isNull() ? null : json);
    }

    @Override
    public Promise<@Nullable Void> save() {
        PromiseImpl<@Nullable Void> promise = new PromiseImpl<>(owner, context, mainQueue);
        byte[] bytes = YamlWriter.write(values).getBytes(StandardCharsets.UTF_8);
        files.writeUserData(userPath, ByteBuffer.wrap(bytes), new PlatformCallback<>() {
            @Override
            public void success(@Nullable Void result) {
                promise.complete(null);
            }

            @Override
            public void failure(Throwable error) {
                promise.fail(error);
            }
        });
        return promise;
    }

    @Override
    public Promise<@Nullable Void> reload() {
        PromiseImpl<@Nullable Void> promise = new PromiseImpl<>(owner, context, mainQueue);
        load(() -> {
            events.call(new ConfigReloadEvent(this));
            promise.complete(null);
        });
        return promise;
    }

    @Override
    public String toString() {
        return "Config " + name + " " + values;
    }

    /** Finds the value at a dotted path, or {@code null}. */
    static @Nullable JsonValue walk(JsonValue root, String path) {
        if (path.isEmpty()) {
            return root;
        }
        JsonValue current = root;
        int start = 0;
        while (true) {
            int dot = path.indexOf('.', start);
            String segment = dot < 0 ? path.substring(start) : path.substring(start, dot);
            if (!(current instanceof JsonObject object)) {
                return null;
            }
            current = object.get(segment);
            if (current == null) {
                return null;
            }
            if (dot < 0) {
                return current instanceof JsonNull ? null : current;
            }
            start = dot + 1;
        }
    }

    private static JsonValue with(JsonValue node, String[] segments, int index, @Nullable JsonValue value) {
        JsonObject object = node instanceof JsonObject o ? o : JsonObject.EMPTY;
        String segment = segments[index];
        if (index == segments.length - 1) {
            return value == null ? object.without(segment) : object.with(segment, value);
        }
        JsonValue child = object.get(segment);
        JsonValue updated = with(child == null ? JsonObject.EMPTY : child, segments, index + 1, value);
        return object.with(segment, updated);
    }

    /** Deep merge: maps are merged key by key, anything else in {@code overrides} replaces the default. */
    static JsonObject merge(JsonObject defaults, JsonObject overrides) {
        JsonObject result = defaults;
        for (Map.Entry<String, JsonValue> entry : overrides.members().entrySet()) {
            JsonValue base = result.get(entry.getKey());
            JsonValue value = entry.getValue();
            if (base instanceof JsonObject baseObject && value instanceof JsonObject overrideObject) {
                value = merge(baseObject, overrideObject);
            }
            result = result.with(entry.getKey(), value);
        }
        return result;
    }

    /** A view of a nested map. */
    private static final class Section extends AbstractSection {
        private final ConfigImpl config;
        private final String prefix;

        Section(ConfigImpl config, String prefix) {
            this.config = config;
            this.prefix = prefix;
        }

        @Override
        public String path() {
            return prefix;
        }

        @Override
        JsonValue rootValue() {
            return config.values;
        }

        @Override
        public @Nullable JsonValue getValue(String path) {
            return config.getValue(prefix + "." + path);
        }

        @Override
        public @Nullable ConfigSection getSection(String path) {
            return config.getSection(prefix + "." + path);
        }

        @Override
        public void set(String path, @Nullable Object value) {
            config.set(prefix + "." + path, value);
        }
    }
}
