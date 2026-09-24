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
import dev.gulp.api.asset.AssetReloadEvent;
import dev.gulp.api.asset.AssetType;
import dev.gulp.api.asset.Assets;
import dev.gulp.api.asset.LoadingScreen;
import dev.gulp.api.asset.ResourcePack;
import dev.gulp.api.asset.ResourcePackChangeEvent;
import dev.gulp.api.asset.ResourcePacks;
import dev.gulp.api.data.JsonValue;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureAtlas;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.math.Rect;
import dev.gulp.api.registry.Key;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.core.CoreContext;
import dev.gulp.core.MainQueue;
import dev.gulp.core.data.JsonReader;
import dev.gulp.core.event.EventBus;
import dev.gulp.core.graphics.GraphicsImpl;
import dev.gulp.core.graphics.TextureImpl;
import dev.gulp.core.scheduler.PromiseImpl;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformDecoders;
import dev.gulp.platform.PlatformFiles;
import dev.gulp.platform.ResourcePackInfo;
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
    private final GraphicsImpl graphics;
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
    private final List<ResourcePackInfo> availablePacks = new ArrayList<>();
    private final List<ResourcePackInfo> enabledPacks = new ArrayList<>();
    private final ResourcePacksImpl resourcePacks = new ResourcePacksImpl();
    private Runnable reloadListener = () -> {};
    private boolean packFolders;

    /**
     * Creates the asset manager with loaders for the built-in types.
     *
     * @param owner owner of the promises (the game)
     * @param context engine context
     * @param mainQueue main-thread queue
     * @param files where files come from
     * @param graphics decodes images and creates textures
     * @param decoders opens font files
     * @param events where asset events go
     * @param logger where load errors go
     */
    public AssetsImpl(
            Owner owner,
            CoreContext context,
            MainQueue mainQueue,
            PlatformFiles files,
            GraphicsImpl graphics,
            PlatformDecoders decoders,
            EventBus events,
            Logger logger) {
        this.owner = owner;
        this.context = context;
        this.mainQueue = mainQueue;
        this.files = files;
        this.graphics = graphics;
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
        BuiltinLoaders.register(this, graphics, decoders);
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
        String name = key.key().path().substring(key.key().path().lastIndexOf('/') + 1);
        if (manifest == null && name.indexOf('.') < 0 && key.type().extensions().size() > 1) {
            // No manifest to consult: try the extensions one by one.
            probe(candidates(key), 0, found -> begin(entry, found));
            return;
        }
        begin(entry, resolve(key));
    }

    private void probe(List<String> candidates, int index, java.util.function.Consumer<@Nullable String> found) {
        if (index >= candidates.size()) {
            found.accept(null);
            return;
        }
        String candidate = candidates.get(index);
        readBytes(candidate).thenSync(bytes -> found.accept(candidate)).onFailure(error -> {
            if (error instanceof FileNotFoundException) {
                probe(candidates, index + 1, found);
            } else {
                found.accept(candidate);
            }
        });
    }

    private <T> void begin(Entry<T> entry, @Nullable String path) {
        AssetKey<T> key = entry.key;
        @SuppressWarnings("unchecked")
        AssetLoader<T> loader = (AssetLoader<T>) loaders.get(key.type());
        Promise<T> loading;
        if (loader == null) {
            loading = failed(new IllegalStateException("No loader is registered for asset type '" + key.type()
                    + "'; register one with assets().registerLoader(type, loader) in onLoad"));
        } else if (path == null) {
            loading = failed(new FileNotFoundException("No file for asset " + key + " in the asset manifest (tried "
                    + String.join(", ", candidates(key)) + ")"));
        } else {
            try {
                entry.path = path;
                loading = loader.load(new Context(entry.key, path, entry.dependencies));
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
        List<String> extensions = key.type().extensions();
        if (name.indexOf('.') >= 0 || extensions.isEmpty()) {
            return base;
        }
        if (manifest == null) {
            return base + "." + extensions.get(0);
        }
        if (key.type() == AssetType.ATLAS
                && packFolders
                && !filesUnder(base + "/").isEmpty()) {
            return base + "/";
        }
        for (String candidate : candidates(key)) {
            if (exists(candidate)) {
                return candidate;
            }
        }
        if (key.type() == AssetType.ATLAS && !filesUnder(base + "/").isEmpty()) {
            return base + "/";
        }
        return null;
    }

    private boolean exists(String path) {
        Set<String> known = manifest;
        if (known != null && known.contains(path)) {
            return true;
        }
        for (ResourcePackInfo pack : enabledPacks) {
            if (pack.files().contains(path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lists known files under a folder, from the manifest and the enabled resource packs.
     *
     * @param folder a path ending with {@code /}
     * @return the file paths, sorted
     */
    public List<String> filesUnder(String folder) {
        Set<String> found = new java.util.TreeSet<>();
        Set<String> known = manifest;
        if (known != null) {
            for (String path : known) {
                if (path.startsWith(folder)) {
                    found.add(path);
                }
            }
        }
        for (ResourcePackInfo pack : enabledPacks) {
            for (String path : pack.files()) {
                if (path.startsWith(folder)) {
                    found.add(path);
                }
            }
        }
        return new ArrayList<>(found);
    }

    /**
     * Reads a file, from the first enabled resource pack that has it or from the game's assets.
     *
     * @param path the asset path
     * @return the contents
     */
    public Promise<byte[]> readBytes(String path) {
        PromiseImpl<byte[]> promise = newPromise();
        PlatformCallback<ByteBuffer> callback = new PlatformCallback<>() {
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
        };
        for (ResourcePackInfo pack : enabledPacks) {
            if (pack.files().contains(path)) {
                files.readResourcePackFile(pack.id(), path, callback);
                return promise;
            }
        }
        files.readAsset(path, callback);
        return promise;
    }

    /**
     * Returns a new pending promise owned by the game.
     *
     * @param <T> the value type
     * @return the promise
     */
    public <T> PromiseImpl<T> newPromise() {
        return new PromiseImpl<>(owner, context, mainQueue);
    }

    /**
     * Returns a promise of all values, in order; it fails with the first failure.
     *
     * @param <T> the value type
     * @param promises the promises
     * @return the combined promise
     */
    <T> Promise<List<T>> all(List<Promise<T>> promises) {
        PromiseImpl<List<T>> result = newPromise();
        if (promises.isEmpty()) {
            result.complete(List.of());
            return result;
        }
        List<T> values = new ArrayList<>(Collections.nCopies(promises.size(), null));
        int[] remaining = {promises.size()};
        for (int i = 0; i < promises.size(); i++) {
            int index = i;
            promises.get(i).thenSync(value -> {
                values.set(index, value);
                if (--remaining[0] == 0) {
                    result.complete(values);
                }
            });
            promises.get(i).onFailure(result::fail);
        }
        return result;
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

    @Override
    public TextureRegion region(String key) {
        Key parsed = Key.parse(key);
        String[] parts = TextureAtlasImpl.splitRegion(parsed.namespace(), parsed.path());
        AssetKey<TextureAtlas> atlas = AssetKey.atlas(parsed.namespace() + ":" + parts[0]);
        if (!isLoaded(atlas)) {
            throw new IllegalStateException("Atlas " + atlas.key() + " is not loaded; load it (or the region key " + key
                    + ") before asking for its regions");
        }
        return get(atlas).region(parts[1]);
    }

    /**
     * Returns a region if its atlas is loaded, for inline images in text.
     *
     * @param key the region key
     * @return the region, or {@code null}
     */
    public @Nullable TextureRegion findRegion(String key) {
        try {
            Key parsed = Key.parse(key);
            String[] parts = TextureAtlasImpl.splitRegion(parsed.namespace(), parsed.path());
            AssetKey<TextureAtlas> atlas = AssetKey.atlas(parsed.namespace() + ":" + parts[0]);
            if (isLoaded(atlas)) {
                return get(atlas).find(parts[1]);
            }
            AssetKey<TextureRegion> region = AssetKey.region(key);
            return isLoaded(region) ? get(region) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns a loaded asset, or {@code null}.
     *
     * @param <T> the value type
     * @param key the asset
     * @return the value if loaded
     */
    public <T> @Nullable T getIfLoaded(AssetKey<T> key) {
        return isLoaded(key) ? get(key) : null;
    }

    @Override
    public ResourcePacks resourcePacks() {
        return resourcePacks;
    }

    /**
     * Asks the platform for resource packs; run at startup.
     *
     * @param done called on the main thread when the list arrived
     */
    public void loadResourcePacks(Runnable done) {
        files.listResourcePacks(new PlatformCallback<>() {
            @Override
            public void success(List<ResourcePackInfo> packs) {
                availablePacks.clear();
                availablePacks.addAll(packs);
                done.run();
            }

            @Override
            public void failure(Throwable error) {
                logger.warn("Cannot list resource packs", error);
                done.run();
            }
        });
    }

    /**
     * Packs atlases from their image folders even when a packed atlas exists; used in development so edited sprites are
     * picked up by hot reload.
     *
     * @param on whether to prefer folders
     */
    public void setPackFolders(boolean on) {
        this.packFolders = on;
    }

    /**
     * Sets what runs after assets were reloaded (the text system forgets its cached layouts).
     *
     * @param listener the listener
     */
    public void setReloadListener(Runnable listener) {
        this.reloadListener = listener;
    }

    /**
     * Reloads the assets read from a changed file. The manifest is read again first, so new files are found.
     *
     * @param path the changed asset path
     */
    public void fileChanged(String path) {
        loadManifest(() -> {
            List<Entry<?>> affected = new ArrayList<>();
            for (Entry<?> entry : entries.values()) {
                String entryPath = entry.path;
                if (entry.loaded
                        && entryPath != null
                        && (entryPath.equals(path) || (entryPath.endsWith("/") && path.startsWith(entryPath)))) {
                    affected.add(entry);
                }
            }
            for (Entry<?> entry : affected) {
                reload(entry);
            }
        });
    }

    /**
     * Reloads every loaded asset, after the resource packs changed.
     *
     * @return completes when all reloads finished
     */
    Promise<Void> reloadAll() {
        List<Promise<Object>> reloads = new ArrayList<>();
        for (Entry<?> entry : new ArrayList<>(entries.values())) {
            if (entry.loaded && entry.key.type() != AssetType.REGION) {
                reloads.add(reload(entry));
            }
        }
        return all(reloads).map(list -> null);
    }

    /** Loads an asset again and swaps the result in; textures and atlases keep their identity. */
    @SuppressWarnings("unchecked")
    private <T> Promise<Object> reload(Entry<T> entry) {
        PromiseImpl<Object> done = newPromise();
        done.onFailure(error -> {});
        AssetLoader<T> loader = (AssetLoader<T>) loaders.get(entry.key.type());
        // The path the asset was loaded from: without a manifest resolve() only guesses the first extension.
        String path = entry.path != null ? entry.path : resolve(entry.key);
        if (loader == null || path == null) {
            done.complete(entry.key);
            return done;
        }
        if (entry.key.type() == AssetType.TEXTURE && entry.value instanceof TextureImpl texture) {
            readBytes(path)
                    .flatMap(graphics::decode)
                    .thenSync(pixmap -> {
                        texture.replace(pixmap);
                        reloaded(entry);
                        done.complete(entry.key);
                    })
                    .onFailure(error -> reloadFailed(entry, error, done));
            return done;
        }
        List<AssetKey<?>> dependencies = new ArrayList<>();
        Promise<T> loading;
        try {
            loading = loader.load(new Context(entry.key, path, dependencies));
        } catch (Throwable error) {
            reloadFailed(entry, error, done);
            return done;
        }
        loading.thenSync(value -> {
            T previous = entry.value;
            List<AssetKey<?>> previousDependencies = new ArrayList<>(entry.dependencies);
            entry.dependencies.clear();
            entry.dependencies.addAll(dependencies);
            entry.path = path;
            if (previous instanceof TextureAtlasImpl atlas && value instanceof TextureAtlasImpl fresh) {
                atlas.replace(fresh.regionMap(), fresh.pages(), freshOwned(fresh));
            } else {
                entry.value = value;
                if (previous != null && previous != value) {
                    loader.dispose(previous);
                }
            }
            for (AssetKey<?> dependency : previousDependencies) {
                unload(dependency);
            }
            reloaded(entry);
            done.complete(entry.key);
        });
        loading.onFailure(error -> {
            for (AssetKey<?> dependency : dependencies) {
                unload(dependency);
            }
            reloadFailed(entry, error, done);
        });
        return done;
    }

    private static List<Texture> freshOwned(TextureAtlasImpl atlas) {
        return atlas.ownedPages();
    }

    private void reloaded(Entry<?> entry) {
        logger.info("Reloaded " + entry.key);
        reloadListener.run();
        if (events.hasListeners(AssetReloadEvent.class)) {
            events.call(new AssetReloadEvent(entry.key));
        }
    }

    private void reloadFailed(Entry<?> entry, Throwable error, PromiseImpl<Object> done) {
        logger.error("Cannot reload asset " + entry.key + "; keeping the previous version: " + error.getMessage());
        done.complete(entry.key);
    }

    /** {@link ResourcePacks} over the platform's pack list. */
    private final class ResourcePacksImpl implements ResourcePacks {
        @Override
        public List<ResourcePack> available() {
            List<ResourcePack> packs = new ArrayList<>();
            for (ResourcePackInfo info : availablePacks) {
                packs.add(new ResourcePack(info.id(), info.description()));
            }
            return packs;
        }

        @Override
        public List<ResourcePack> enabled() {
            List<ResourcePack> packs = new ArrayList<>();
            for (ResourcePackInfo info : enabledPacks) {
                packs.add(new ResourcePack(info.id(), info.description()));
            }
            return packs;
        }

        @Override
        public Promise<Void> setEnabled(List<String> ids) {
            context.checkMainThread("ResourcePacks.setEnabled");
            List<ResourcePackInfo> chosen = new ArrayList<>();
            for (String id : ids) {
                ResourcePackInfo found = null;
                for (ResourcePackInfo info : availablePacks) {
                    if (info.id().equals(id)) {
                        found = info;
                    }
                }
                if (found == null) {
                    logger.warn("Unknown resource pack '" + id + "'; available: " + available());
                } else if (!chosen.contains(found)) {
                    chosen.add(found);
                }
            }
            enabledPacks.clear();
            enabledPacks.addAll(chosen);
            return reloadAll().map(nothing -> {
                if (events.hasListeners(ResourcePackChangeEvent.class)) {
                    events.call(new ResourcePackChangeEvent(enabled()));
                }
                return null;
            });
        }
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

        @Nullable String path;

        Entry(AssetKey<T> key, PromiseImpl<T> promise) {
            this.key = key;
            this.promise = promise;
        }
    }

    private final class Context implements AssetLoadContext {
        private final AssetKey<?> key;
        private final String path;
        private final List<AssetKey<?>> dependencies;

        Context(AssetKey<?> key, String path, List<AssetKey<?>> dependencies) {
            this.key = key;
            this.path = path;
            this.dependencies = dependencies;
        }

        @Override
        public AssetKey<?> key() {
            return key;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public Promise<byte[]> bytes() {
            return readBytes(path);
        }

        @Override
        public Promise<String> text() {
            return bytes().map(bytes -> new String(bytes, StandardCharsets.UTF_8));
        }

        @Override
        public <D> Promise<D> dependency(AssetKey<D> key) {
            dependencies.add(key);
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
