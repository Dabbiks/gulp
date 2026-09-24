package dev.gulp.backend.desktop;

import dev.gulp.api.data.JsonArray;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonValue;
import dev.gulp.core.MainQueue;
import dev.gulp.core.data.JsonReader;
import dev.gulp.core.data.JsonWriter;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformExecutor;
import dev.gulp.platform.PlatformFiles;
import dev.gulp.platform.ResourcePackInfo;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jspecify.annotations.Nullable;

/**
 * Desktop files. Assets come from a directory ({@code -Dgulp.assetsDir}, or {@code ./assets} when it exists, useful in
 * development) and otherwise from the classpath under {@code assets/}. User data lives in the application data
 * directory: {@code %APPDATA%/<game>} on Windows, {@code ~/Library/Application Support/<game>} on macOS,
 * {@code $XDG_DATA_HOME/<game>} on Linux, or {@code -Dgulp.dataDir}. I/O runs on the executor; callbacks run on the
 * main thread before the next frame.
 */
public final class DesktopFiles implements PlatformFiles {

    private final @Nullable Path assetsDirectory;
    private final Path dataDirectory;
    private final PlatformExecutor executor;
    private final MainQueue mainQueue;

    DesktopFiles(@Nullable Path assetsDirectory, Path dataDirectory, PlatformExecutor executor, MainQueue mainQueue) {
        this.assetsDirectory = assetsDirectory;
        this.dataDirectory = dataDirectory;
        this.executor = executor;
        this.mainQueue = mainQueue;
    }

    /**
     * Returns the assets directory from {@code gulp.assetsDir} or {@code ./assets}.
     *
     * @return the directory, or {@code null} to use only the classpath
     */
    static @Nullable Path defaultAssetsDirectory() {
        String configured = System.getProperty("gulp.assetsDir");
        if (configured != null) {
            return Path.of(configured);
        }
        Path local = Path.of("assets");
        return Files.isDirectory(local) ? local : null;
    }

    /**
     * Returns the application data directory of a game.
     *
     * @param appId the game id
     * @param osName the value of {@code os.name}
     * @param home the user home directory
     * @param appData the {@code APPDATA} variable, or {@code null}
     * @param xdgDataHome the {@code XDG_DATA_HOME} variable, or {@code null}
     * @return the directory
     */
    static Path dataDirectory(
            String appId, String osName, String home, @Nullable String appData, @Nullable String xdgDataHome) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return (appData != null ? Path.of(appData) : Path.of(home, "AppData", "Roaming")).resolve(appId);
        }
        if (MacOsFirstThread.isMacOs(osName)) {
            return Path.of(home, "Library", "Application Support", appId);
        }
        return (xdgDataHome != null && !xdgDataHome.isEmpty() ? Path.of(xdgDataHome) : Path.of(home, ".local", "share"))
                .resolve(appId);
    }

    /**
     * Returns the data directory for this machine, or {@code -Dgulp.dataDir}.
     *
     * @param appId the game id
     * @return the directory
     */
    static Path defaultDataDirectory(String appId) {
        String configured = System.getProperty("gulp.dataDir");
        if (configured != null) {
            return Path.of(configured);
        }
        return dataDirectory(
                appId,
                System.getProperty("os.name", ""),
                System.getProperty("user.home", "."),
                System.getenv("APPDATA"),
                System.getenv("XDG_DATA_HOME"));
    }

    /**
     * Rejects paths that could escape their root.
     *
     * @param path a relative path with {@code /} separators
     * @return the path
     * @throws IllegalArgumentException if it is absolute, empty or contains {@code ..}
     */
    static String requireRelative(String path) {
        if (path.isEmpty()
                || path.startsWith("/")
                || path.contains("\\")
                || path.contains(":")
                || ("/" + path + "/").contains("/../")) {
            throw new IllegalArgumentException("Invalid relative path '" + path + "'");
        }
        return path;
    }

    @Override
    public void readAsset(String path, PlatformCallback<ByteBuffer> callback) {
        async(callback, () -> ByteBuffer.wrap(readAssetBytes(requireRelative(path))));
    }

    private byte[] readAssetBytes(String path) throws IOException {
        if (assetsDirectory != null && path.equals(MANIFEST) && Files.isDirectory(assetsDirectory)) {
            return liveManifest(assetsDirectory);
        }
        if (assetsDirectory != null) {
            Path file = assetsDirectory.resolve(path);
            if (Files.isRegularFile(file)) {
                return Files.readAllBytes(file);
            }
        }
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = DesktopFiles.class.getClassLoader();
        }
        try (InputStream in = loader.getResourceAsStream("assets/" + path)) {
            if (in == null) {
                throw new FileNotFoundException("No asset '" + path + "'");
            }
            return in.readAllBytes();
        }
    }

    @Override
    public void readUserData(String name, PlatformCallback<ByteBuffer> callback) {
        async(callback, () -> {
            Path file = dataDirectory.resolve(requireRelative(name));
            if (!Files.isRegularFile(file)) {
                throw new FileNotFoundException("No user data '" + name + "'");
            }
            return ByteBuffer.wrap(Files.readAllBytes(file));
        });
    }

    private final java.util.Map<String, Object> writeLocks = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Long> writtenSequence = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong writeSequence = new java.util.concurrent.atomic.AtomicLong();

    @Override
    public void writeUserData(String name, ByteBuffer data, PlatformCallback<Void> callback) {
        byte[] bytes = new byte[data.remaining()];
        data.duplicate().get(bytes);
        // Writes run on parallel virtual threads: writes of one file are serialised, and a write that was requested
        // earlier than the one already on disk is dropped, so the last requested contents always win.
        long sequence = writeSequence.incrementAndGet();
        async(callback, () -> {
            synchronized (writeLocks.computeIfAbsent(name, n -> new Object())) {
                if (writtenSequence.getOrDefault(name, 0L) > sequence) {
                    return null;
                }
                Path file = dataDirectory.resolve(requireRelative(name));
                Files.createDirectories(file.getParent());
                Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
                Files.write(temporary, bytes);
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
                writtenSequence.put(name, sequence);
            }
            return null;
        });
    }

    @Override
    public void deleteUserData(String name, PlatformCallback<Void> callback) {
        async(callback, () -> {
            Files.deleteIfExists(dataDirectory.resolve(requireRelative(name)));
            return null;
        });
    }

    @Override
    public void listUserData(String prefix, PlatformCallback<List<String>> callback) {
        async(callback, () -> {
            List<String> names = new ArrayList<>();
            if (Files.isDirectory(dataDirectory)) {
                try (Stream<Path> files = Files.walk(dataDirectory)) {
                    files.filter(Files::isRegularFile).forEach(file -> {
                        String name = dataDirectory.relativize(file).toString().replace('\\', '/');
                        if (name.startsWith(prefix) && !name.endsWith(".tmp")) {
                            names.add(name);
                        }
                    });
                }
            }
            names.sort(null);
            return List.copyOf(names);
        });
    }

    // ------------------------------------------------------------------ manifest, resource packs, watching

    /** Name of the asset manifest inside the assets folder. */
    static final String MANIFEST = "assets.manifest.json";

    private @Nullable Consumer<String> watcher;
    private @Nullable WatchService watchService;
    private final Map<WatchKey, Path> watchedDirectories = new HashMap<>();

    /**
     * Builds the manifest from the assets folder merged with the one on the classpath, so files added during
     * development are found without a rebuild.
     */
    private byte[] liveManifest(Path directory) throws IOException {
        Set<String> paths = new TreeSet<>();
        try (Stream<Path> files = Files.walk(directory)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                String path = directory.relativize(file).toString().replace('\\', '/');
                if (!path.equals(MANIFEST)) {
                    paths.add(path);
                }
            });
        }
        ClassLoader loader = classLoader();
        try (InputStream in = loader.getResourceAsStream("assets/" + MANIFEST)) {
            if (in != null) {
                for (JsonValue file : JsonReader.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                        .asObject()
                        .getOrThrow("files")
                        .asArray()) {
                    paths.add(file.asObject().getOrThrow("path").asString());
                }
            }
        }
        JsonArray.Builder files = JsonArray.builder();
        for (String path : paths) {
            files.add(JsonObject.builder().put("path", path).build());
        }
        return JsonWriter.write(JsonObject.builder().put("files", files.build()).build(), false)
                .getBytes(StandardCharsets.UTF_8);
    }

    private static ClassLoader classLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader != null ? loader : DesktopFiles.class.getClassLoader();
    }

    private Path packsDirectory() {
        return dataDirectory.resolve("resourcepacks");
    }

    @Override
    public void listResourcePacks(PlatformCallback<List<ResourcePackInfo>> callback) {
        async(callback, () -> {
            Path directory = packsDirectory();
            List<ResourcePackInfo> packs = new ArrayList<>();
            if (!Files.isDirectory(directory)) {
                return packs;
            }
            try (Stream<Path> entries = Files.list(directory)) {
                for (Path entry : entries.sorted().toList()) {
                    String id = entry.getFileName().toString();
                    if (Files.isDirectory(entry)) {
                        packs.add(folderPack(id, entry));
                    } else if (id.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        packs.add(zipPack(id, entry));
                    }
                }
            }
            return packs;
        });
    }

    private static ResourcePackInfo folderPack(String id, Path folder) throws IOException {
        List<String> files = new ArrayList<>();
        Path assets = folder.resolve("assets");
        if (Files.isDirectory(assets)) {
            try (Stream<Path> walk = Files.walk(assets)) {
                walk.filter(Files::isRegularFile)
                        .forEach(file ->
                                files.add(assets.relativize(file).toString().replace('\\', '/')));
            }
        }
        files.sort(null);
        Path description = folder.resolve("pack.txt");
        String text = Files.isRegularFile(description) ? firstLine(Files.readString(description)) : "";
        return new ResourcePackInfo(id, text, List.copyOf(files));
    }

    private static ResourcePackInfo zipPack(String id, Path zip) throws IOException {
        List<String> files = new ArrayList<>();
        String text = "";
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            for (ZipEntry entry : java.util.Collections.list(archive.entries())) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (entry.getName().startsWith("assets/")) {
                    files.add(entry.getName().substring("assets/".length()));
                } else if (entry.getName().equals("pack.txt")) {
                    try (InputStream in = archive.getInputStream(entry)) {
                        text = firstLine(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
        }
        files.sort(null);
        return new ResourcePackInfo(id, text, List.copyOf(files));
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return (newline < 0 ? text : text.substring(0, newline)).strip();
    }

    @Override
    public void readResourcePackFile(String packId, String path, PlatformCallback<ByteBuffer> callback) {
        async(callback, () -> {
            Path pack = packsDirectory().resolve(requireRelative(packId));
            String inside = "assets/" + requireRelative(path);
            if (Files.isDirectory(pack)) {
                Path file = pack.resolve(inside);
                if (!Files.isRegularFile(file)) {
                    throw new FileNotFoundException("No '" + path + "' in resource pack '" + packId + "'");
                }
                return ByteBuffer.wrap(Files.readAllBytes(file));
            }
            try (ZipFile archive = new ZipFile(pack.toFile())) {
                ZipEntry entry = archive.getEntry(inside);
                if (entry == null) {
                    throw new FileNotFoundException("No '" + path + "' in resource pack '" + packId + "'");
                }
                try (InputStream in = archive.getInputStream(entry)) {
                    return ByteBuffer.wrap(in.readAllBytes());
                }
            }
        });
    }

    @Override
    public void watchAssets(@Nullable Consumer<String> listener) {
        this.watcher = listener;
        if (listener == null || assetsDirectory == null || watchService != null) {
            return;
        }
        if (!Files.isDirectory(assetsDirectory)) {
            // A game without its own assets (only the built-in ones) has nothing to watch.
            return;
        }
        try {
            WatchService service = assetsDirectory.getFileSystem().newWatchService();
            try (Stream<Path> directories = Files.walk(assetsDirectory)) {
                for (Path directory : directories.filter(Files::isDirectory).toList()) {
                    register(service, directory);
                }
            }
            watchService = service;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot watch " + assetsDirectory, e);
        }
    }

    private void register(WatchService service, Path directory) throws IOException {
        WatchKey key = directory.register(
                service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        watchedDirectories.put(key, directory);
    }

    /** Delivers file changes seen since the last call; runs on the main thread before each frame. */
    void pollChanges() {
        WatchService service = watchService;
        Consumer<String> listener = watcher;
        Path root = assetsDirectory;
        if (service == null || listener == null || root == null) {
            return;
        }
        Set<String> changed = new TreeSet<>();
        WatchKey key;
        while ((key = service.poll()) != null) {
            Path directory = watchedDirectories.get(key);
            for (WatchEvent<?> event : key.pollEvents()) {
                if (directory == null || !(event.context() instanceof Path name)) {
                    continue;
                }
                Path file = directory.resolve(name);
                if (Files.isDirectory(file) && event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                    try {
                        register(service, file);
                    } catch (IOException ignored) {
                        // The folder vanished again.
                    }
                    continue;
                }
                changed.add(root.relativize(file).toString().replace('\\', '/'));
            }
            if (!key.reset()) {
                watchedDirectories.remove(key);
            }
        }
        for (String path : changed) {
            listener.accept(path);
        }
    }

    /** Stops watching. */
    void closeWatcher() {
        WatchService service = watchService;
        watchService = null;
        if (service != null) {
            try {
                service.close();
            } catch (IOException ignored) {
                // Nothing to do at shutdown.
            }
        }
    }

    @Override
    public String userDataLocation() {
        return dataDirectory.toAbsolutePath().toString();
    }

    private <T> void async(PlatformCallback<T> callback, Callable<T> work) {
        executor.execute(() -> {
            T result;
            try {
                result = work.call();
            } catch (Throwable error) {
                mainQueue.post(() -> callback.failure(error));
                return;
            }
            mainQueue.post(() -> callback.success(result));
        });
    }
}
