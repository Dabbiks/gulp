package dev.gulp.backend.desktop;

import dev.gulp.core.MainQueue;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformExecutor;
import dev.gulp.platform.PlatformFiles;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
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

    @Override
    public void writeUserData(String name, ByteBuffer data, PlatformCallback<Void> callback) {
        byte[] bytes = new byte[data.remaining()];
        data.duplicate().get(bytes);
        async(callback, () -> {
            Path file = dataDirectory.resolve(requireRelative(name));
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
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
