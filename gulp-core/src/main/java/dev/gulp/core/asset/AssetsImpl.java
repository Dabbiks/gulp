package dev.gulp.core.asset;

import dev.gulp.api.Logger;
import dev.gulp.api.Owner;
import dev.gulp.api.asset.AssetGroup;
import dev.gulp.api.asset.AssetGroupLoadedEvent;
import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.asset.AssetLoadContext;
import dev.gulp.api.asset.AssetLoadEvent;
import dev.gulp.api.asset.AssetLoadFailedEvent;
import dev.gulp.api.asset.AssetLoader;
import dev.gulp.api.asset.AssetType;
import dev.gulp.api.asset.Assets;
import dev.gulp.api.asset.LoadingScreen;
import dev.gulp.api.data.JsonValue;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.math.Rect;
import dev.gulp.api.registry.Key;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.core.CoreContext;
import dev.gulp.core.MainQueue;
import dev.gulp.core.data.JsonReader;
import dev.gulp.core.event.EventBus;
import dev.gulp.core.graphics.GraphicsImpl;
import dev.gulp.core.scheduler.PromiseImpl;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformFiles;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * {@link Assets}: loading by key through {@link PlatformFiles}, reference counting with dependencies, groups and the
 * startup group. Everything runs on the main thread; files arrive through platform callbacks.
 */
public final class AssetsImpl implements Assets {

    /** Asset manifest written by the Gradle plugin, inside the assets folder. */
    public static final String MANIFEST = "assets.manifest.json";

    /** Default loading screen: a progress bar in the middle of the screen. */
    public static final LoadingScreen DEFAULT_LOADING_SCREEN = (draw, display, progress) -> {
        float width = Math.min(display.width() * 0.6f, 480f);
        float x = (display.width() - width) / 2f;
        float y = display.height() / 2f - 3f;
        draw.color(Color.rgb(0x333333)).roundedRect(new Rect(x, y, width, 6f), 3f);
        if (progress > 0f) {
            draw.color(Color.rgb(0xffa300)).roundedRect(new Rect(x, y, Math.max(6f, width * progress), 6f), 3f);
        }
        draw.color(Color.WHITE);
    };

    private final Owner owner;
    private final CoreContext context;
    private final MainQueue mainQueue;
    private final PlatformFiles files;
    private final EventBus events;
    private final Logger logger;
    private final Map<AssetKey<?>, Entry<?>> entries = new HashMap<>();
    private final Map<AssetType<?>, AssetLoader<?>> loaders = new LinkedHashMap<>();
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private @Nullable Set<String> manifest;
    private LoadingScreen loadingScreen = DEFAULT_LOADING_SCREEN;
    private int pending;
    private int requested;
    private int finished;

    /**
     * Creates the asset manager with loaders for the built-in types.
     *
     * @param owner owner of the promises (the game)
     * @param context engine context
     * @param mainQueue main-thread queue
     * @param files where files come from
     * @param graphics decodes images and creates textures
     * @param events where asset events go
     * @param logger where load errors go
     */
    public AssetsImpl(
            Owner owner,
            CoreContext context,
            MainQueue mainQueue,
            PlatformFiles files,
            GraphicsImpl graphics,
            EventBus events,
            Logger logger) {
        this.owner = owner;
        this.context = context;
        this.mainQueue = mainQueue;
        this.files = files;
        this.events = events;
        this.logger = logger;
        registerLoader(AssetType.TEXTURE, new AssetLoader<>() {
            @Override
            public Promise<Texture> load(AssetLoadContext loading) {
                return loading.bytes().flatMap(graphics::decode).map(graphics::texture);
            }

            @Override
            public void dispose(Texture asset) {
                asset.dispose();
            }
        });
        registerLoader(AssetType.PIXMAP, loading -> loading.bytes().flatMap(graphics::decode));
        registerLoader(AssetType.TEXT, AssetLoadContext::text);
        registerLoader(AssetType.BYTES, AssetLoadContext::bytes);
    }

    // ------------------------------------------------------------------ engine hooks

    /**
     * Reads the asset manifest; without one, extensions are guessed and folders cannot be listed.
     *
     * @param done called on the main thread when the manifest was read or found missing
     */
    public void loadManifest(Runnable done) {
        files.readAsset(MANIFEST, new PlatformCallback<>() {
            @Override
            public void success(ByteBuffer data) {
                try {
                    manifest = parseManifest(
                            StandardCharsets.UTF_8.decode(data.duplicate()).toString());
                } catch (RuntimeException e) {
                    logger.error("Cannot read the asset manifest", e);
                }
                done.run();
            }

            @Override
            public void failure(Throwable error) {
                if (!(error instanceof FileNotFoundException)) {
                    logger.warn("Cannot read the asset manifest", error);
                }
                done.run();
            }
        });
    }

    static Set<String> parseManifest(String json) {
        Set<String> paths = new HashSet<>();
        for (JsonValue file :
                JsonReader.parse(json).asObject().getOrThrow("files").asArray()) {
            paths.add(file.asObject().getOrThrow("path").asString());
        }
        return paths;
    }

    /**
     * Returns the screen drawn while the startup group loads.
     *
     * @return the loading screen
     */
    public LoadingScreen loadingScreen() {
        return loadingScreen;
    }

    /** Forgets every asset without disposing it; GPU resources are freed by the graphics module at shutdown. */
    public void clear() {
        entries.clear();
        groups.clear();
        pending = 0;
    }

    // ------------------------------------------------------------------ Assets

    @Override
    public <T> T get(AssetKey<T> key) {
        Entry<?> entry = entries.get(key);
        if (entry == null) {
            throw new IllegalStateException(
                    "Asset " + key + " is not loaded. Load it with assets().load(key), add it to"
                            + " a group, or add it to assets().startup() in onLoad so it is ready in onStart.");
        }
        if (entry.error != null) {
            throw new IllegalStateException(
                    "Asset " + key + " failed to load: " + entry.error.getMessage(), entry.error);
        }
        if (!entry.loaded) {
            throw new IllegalStateException("Asset " + key + " is still loading; wait for the promise of load(key).");
        }
        @SuppressWarnings("unchecked")
        T value = (T) entry.value;
        return value;
    }

    @Override
    public <T> Promise<T> load(AssetKey<T> key) {
        context.checkMainThread("Assets.load");
        @SuppressWarnings("unchecked")
        Entry<T> entry = (Entry<T>) entries.get(key);
        if (entry != null && entry.error == null) {
            entry.references++;
            return entry.promise;
        }
        entry = new Entry<>(key, new PromiseImpl<>(owner, context, mainQueue));
        // Failures are logged once, by this manager; callers may still add their own handlers.
        entry.promise.onFailure(error -> {});
        entries.put(key, entry);
        start(entry);
        return entry.promise;
    }

    private <T> void start(Entry<T> entry) {
        if (pending == 0) {
            requested = 0;
            finished = 0;
        }
        pending++;
        requested++;
        AssetKey<T> key = entry.key;
        @SuppressWarnings("unchecked")
        AssetLoader<T> loader = (AssetLoader<T>) loaders.get(key.type());
        String path = resolve(key);
        Promise<T> loading;
        if (loader == null) {
            loading = failed(new IllegalStateException("No loader is registered for asset type '" + key.type()
                    + "'; register one with assets().registerLoader(type, loader) in onLoad"));
        } else if (path == null) {
            loading = failed(new FileNotFoundException("No file for asset " + key + " in the asset manifest (tried "
                    + String.join(", ", candidates(key)) + ")"));
        } else {
            try {
                loading = loader.load(new Context(entry, path));
            } catch (Throwable error) {
                loading = failed(error);
            }
        }
        loading.thenSync(value -> finish(entry, loader, value));
        loading.onFailure(error -> fail(entry, error));
    }

    private <T> void finish(Entry<T> entry, @Nullable AssetLoader<T> loader, T value) {
        pending--;
        finished++;
        if (entry.released) {
            if (loader != null) {
                loader.dispose(value);
            }
            releaseDependencies(entry);
            entry.promise.fail(new IllegalStateException("Asset " + entry.key + " was unloaded while loading"));
            return;
        }
        entry.value = value;
        entry.loaded = true;
        if (events.hasListeners(AssetLoadEvent.class)) {
            events.call(new AssetLoadEvent(entry.key));
        }
        entry.promise.complete(value);
    }

    private void fail(Entry<?> entry, Throwable error) {
        pending--;
        finished++;
        entry.error = error;
        releaseDependencies(entry);
        logger.error("Cannot load asset " + entry.key + ": " + error.getMessage());
        if (events.hasListeners(AssetLoadFailedEvent.class)) {
            events.call(new AssetLoadFailedEvent(entry.key, error));
        }
        entry.promise.fail(error);
    }

    private <T> Promise<T> failed(Throwable error) {
        return PromiseImpl.failed(owner, context, mainQueue, error);
    }

    /** File path of a key: {@code namespace/path}, with the first extension of the type found in the manifest. */
    @Nullable String resolve(AssetKey<?> key) {
        String base = key.key().namespace() + "/" + key.key().path();
        String name = base.substring(base.lastIndexOf('/') + 1);
        Set<String> known = manifest;
        if (name.indexOf('.') >= 0) {
            return base;
        }
        if (known == null) {
            List<String> extensions = key.type().extensions();
            return extensions.isEmpty() ? base : base + "." + extensions.get(0);
        }
        for (String candidate : candidates(key)) {
            if (known.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static List<String> candidates(AssetKey<?> key) {
        String base = key.key().namespace() + "/" + key.key().path();
        List<String> candidates = new ArrayList<>();
        for (String extension : key.type().extensions()) {
            candidates.add(base + "." + extension);
        }
        candidates.add(base);
        return candidates;
    }

    @Override
    public boolean isLoaded(AssetKey<?> key) {
        Entry<?> entry = entries.get(key);
        return entry != null && entry.loaded;
    }

    @Override
    public void unload(AssetKey<?> key) {
        context.checkMainThread("Assets.unload");
        Entry<?> entry = entries.get(key);
        if (entry == null) {
            return;
        }
        if (--entry.references > 0) {
            return;
        }
        entries.remove(key);
        entry.released = true;
        if (entry.loaded) {
            dispose(entry);
            releaseDependencies(entry);
        }
    }

    private <T> void dispose(Entry<T> entry) {
        @SuppressWarnings("unchecked")
        AssetLoader<T> loader = (AssetLoader<T>) loaders.get(entry.key.type());
        T value = entry.value;
        if (loader != null && value != null) {
            try {
                loader.dispose(value);
            } catch (Throwable error) {
                logger.error("Disposing asset " + entry.key + " failed", error);
            }
        }
        entry.value = null;
        entry.loaded = false;
    }

    private void releaseDependencies(Entry<?> entry) {
        List<AssetKey<?>> dependencies = new ArrayList<>(entry.dependencies);
        entry.dependencies.clear();
        for (AssetKey<?> dependency : dependencies) {
            unload(dependency);
        }
    }

    /**
     * Number of uses of an asset, for tests.
     *
     * @param key the asset
     * @return the reference count, 0 if not tracked
     */
    int references(AssetKey<?> key) {
        Entry<?> entry = entries.get(key);
        return entry == null ? 0 : entry.references;
    }

    @Override
    public float progress() {
        return pending == 0 || requested == 0 ? 1f : finished / (float) requested;
    }

    @Override
    public AssetGroup group(String name) {
        return groups.computeIfAbsent(name, Group::new);
    }

    @Override
    public AssetGroup startup() {
        return group(STARTUP);
    }

    @Override
    public Promise<Void> loadGroup(String name) {
        context.checkMainThread("Assets.loadGroup");
        Group group = groups.computeIfAbsent(name, Group::new);
        PromiseImpl<Void> result = new PromiseImpl<>(owner, context, mainQueue);
        List<AssetKey<?>> keys;
        try {
            keys = group.expand();
        } catch (RuntimeException error) {
            result.fail(error);
            return result;
        }
        group.active.addAll(keys);
        group.total = keys.size();
        group.done = 0;
        if (keys.isEmpty()) {
            groupLoaded(group, result);
            return result;
        }
        for (AssetKey<?> key : keys) {
            Promise<?> loading = load(key);
            loading.thenSync(value -> {
                group.done++;
                if (group.done == group.total && !result.isDone()) {
                    groupLoaded(group, result);
                }
            });
            loading.onFailure(result::fail);
        }
        return result;
    }

    private void groupLoaded(Group group, PromiseImpl<Void> result) {
        group.loaded = true;
        if (events.hasListeners(AssetGroupLoadedEvent.class)) {
            events.call(new AssetGroupLoadedEvent(group.name));
        }
        result.complete(null);
    }

    @Override
    public void unloadGroup(String name) {
        Group group = groups.get(name);
        if (group == null) {
            return;
        }
        List<AssetKey<?>> active = new ArrayList<>(group.active);
        group.active.clear();
        group.loaded = false;
        group.done = 0;
        group.total = 0;
        for (AssetKey<?> key : active) {
            unload(key);
        }
    }

    @Override
    public <T> void registerLoader(AssetType<T> type, AssetLoader<T> loader) {
        loaders.put(type, loader);
    }

    @Override
    public void setLoadingScreen(LoadingScreen screen) {
        this.loadingScreen = screen;
    }

    /** Type of a file found in a folder, by extension; images become textures. */
    private AssetType<?> typeOf(String extension) {
        if (AssetType.TEXTURE.extensions().contains(extension)) {
            return AssetType.TEXTURE;
        }
        for (AssetType<?> type : loaders.keySet()) {
            if (type != AssetType.PIXMAP && type.extensions().contains(extension)) {
                return type;
            }
        }
        return AssetType.BYTES;
    }

    // ------------------------------------------------------------------ internals

    private static final class Entry<T> {
        final AssetKey<T> key;
        final PromiseImpl<T> promise;
        final List<AssetKey<?>> dependencies = new ArrayList<>();
        int references = 1;

        @Nullable T value;

        @Nullable Throwable error;

        boolean loaded;
        boolean released;

        Entry(AssetKey<T> key, PromiseImpl<T> promise) {
            this.key = key;
            this.promise = promise;
        }
    }

    private final class Context implements AssetLoadContext {
        private final Entry<?> entry;
        private final String path;

        Context(Entry<?> entry, String path) {
            this.entry = entry;
            this.path = path;
        }

        @Override
        public AssetKey<?> key() {
            return entry.key;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public Promise<byte[]> bytes() {
            PromiseImpl<byte[]> promise = new PromiseImpl<>(owner, context, mainQueue);
            files.readAsset(path, new PlatformCallback<>() {
                @Override
                public void success(ByteBuffer data) {
                    byte[] bytes = new byte[data.remaining()];
                    data.duplicate().get(bytes);
                    promise.complete(bytes);
                }

                @Override
                public void failure(Throwable error) {
                    promise.fail(error);
                }
            });
            return promise;
        }

        @Override
        public Promise<String> text() {
            return bytes().map(bytes -> new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public <D> Promise<D> dependency(AssetKey<D> key) {
            entry.dependencies.add(key);
            return load(key);
        }
    }

    private final class Group implements AssetGroup {
        final String name;
        final Set<AssetKey<?>> keys = new LinkedHashSet<>();
        final Set<String> folders = new LinkedHashSet<>();
        final List<AssetKey<?>> active = new ArrayList<>();
        boolean loaded;
        int total;
        int done;

        Group(String name) {
            this.name = name;
        }

        List<AssetKey<?>> expand() {
            Set<AssetKey<?>> all = new LinkedHashSet<>(keys);
            for (String folder : folders) {
                Set<String> known = manifest;
                if (known == null) {
                    throw new IllegalStateException("Group '" + name + "' adds folder '" + folder + "', but there is no"
                            + " asset manifest (assets/" + MANIFEST + ", written by the Gulp Gradle plugin)");
                }
                Key folderKey = Key.parse(folder);
                String prefix = folderKey.namespace() + "/" + folderKey.path() + "/";
                List<String> found = new ArrayList<>();
                for (String path : known) {
                    if (path.startsWith(prefix)) {
                        found.add(path);
                    }
                }
                Collections.sort(found);
                for (String path : found) {
                    String relative = path.substring(folderKey.namespace().length() + 1);
                    int dot = relative.lastIndexOf('.');
                    boolean hasExtension = dot > relative.lastIndexOf('/');
                    String extension =
                            hasExtension ? relative.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
                    String keyPath = hasExtension ? relative.substring(0, dot) : relative;
                    all.add(new AssetKey<>(Key.of(folderKey.namespace(), keyPath), typeOf(extension)));
                }
            }
            return new ArrayList<>(all);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public AssetGroup add(AssetKey<?> key) {
            keys.add(key);
            return this;
        }

        @Override
        public AssetGroup addFolder(String folder) {
            Key.parse(folder);
            folders.add(folder);
            return this;
        }

        @Override
        public Set<AssetKey<?>> keys() {
            return Collections.unmodifiableSet(keys);
        }

        @Override
        public Set<String> folders() {
            return Collections.unmodifiableSet(folders);
        }

        @Override
        public boolean isLoaded() {
            return loaded;
        }

        @Override
        public float progress() {
            return total == 0 ? (loaded ? 1f : 0f) : done / (float) total;
        }
    }
}
