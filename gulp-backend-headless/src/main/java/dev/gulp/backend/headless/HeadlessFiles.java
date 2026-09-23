package dev.gulp.backend.headless;

import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformFiles;
import dev.gulp.platform.ResourcePackInfo;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Assets and user data kept in memory. Tests put assets in before starting the game and can inspect saved user data
 * afterwards. Callbacks run before the next frame.
 *
 * <pre>{@code
 * backend.files().putAsset("coins/lang/pl_pl.json", json.getBytes(UTF_8));
 * byte[] saved = backend.files().userData("saves/slot1.bin");
 * }</pre>
 */
public final class HeadlessFiles implements PlatformFiles {

    private final Consumer<Runnable> mainQueue;
    private final Map<String, byte[]> assets = new TreeMap<>();
    private final Map<String, byte[]> userData = new TreeMap<>();

    HeadlessFiles(Consumer<Runnable> mainQueue) {
        this.mainQueue = mainQueue;
    }

    /**
     * Adds or replaces an asset.
     *
     * @param path path relative to the assets root
     * @param content the bytes
     */
    public void putAsset(String path, byte[] content) {
        assets.put(path, content.clone());
    }

    /**
     * Returns a copy of stored user data.
     *
     * @param name file name
     * @return the bytes, or {@code null} if absent
     */
    public byte @Nullable [] userData(String name) {
        byte[] content = userData.get(name);
        return content == null ? null : content.clone();
    }

    @Override
    public void readAsset(String path, PlatformCallback<ByteBuffer> callback) {
        if (classpathAssets && !assets.containsKey(path)) {
            // Assets of the libraries on the classpath, such as the engine's built-in font.
            byte[] bundled = classpathAsset(path);
            if (bundled != null) {
                mainQueue.accept(() -> callback.success(direct(bundled)));
                return;
            }
        }
        read(assets, "asset", path, callback);
    }

    private boolean classpathAssets;

    /**
     * Also serves assets from the classpath (the engine's built-in font), for tests that draw text.
     *
     * @param on whether to read classpath assets
     */
    public void useClasspathAssets(boolean on) {
        this.classpathAssets = on;
    }

    private static byte @Nullable [] classpathAsset(String path) {
        if (path.equals("assets.manifest.json")) {
            return null;
        }
        try (java.io.InputStream in = HeadlessFiles.class.getClassLoader().getResourceAsStream("assets/" + path)) {
            return in == null ? null : in.readAllBytes();
        } catch (java.io.IOException e) {
            return null;
        }
    }

    @Override
    public void readUserData(String name, PlatformCallback<ByteBuffer> callback) {
        read(userData, "user data", name, callback);
    }

    private void read(Map<String, byte[]> store, String kind, String name, PlatformCallback<ByteBuffer> callback) {
        byte[] content = store.get(name);
        mainQueue.accept(() -> {
            if (content == null) {
                callback.failure(new FileNotFoundException("No " + kind + " '" + name + "'"));
            } else {
                callback.success(direct(content));
            }
        });
    }

    @Override
    public void writeUserData(String name, ByteBuffer data, PlatformCallback<Void> callback) {
        byte[] content = new byte[data.remaining()];
        data.duplicate().get(content);
        userData.put(name, content);
        mainQueue.accept(() -> callback.success(null));
    }

    @Override
    public void deleteUserData(String name, PlatformCallback<Void> callback) {
        userData.remove(name);
        mainQueue.accept(() -> callback.success(null));
    }

    @Override
    public void listUserData(String prefix, PlatformCallback<List<String>> callback) {
        List<String> names = new ArrayList<>();
        for (String name : userData.keySet()) {
            if (name.startsWith(prefix)) {
                names.add(name);
            }
        }
        mainQueue.accept(() -> callback.success(List.copyOf(names)));
    }

    // ------------------------------------------------------------------ resource packs and watching

    private final Map<String, ResourcePackInfo> packs = new java.util.LinkedHashMap<>();
    private final Map<String, Map<String, byte[]>> packFiles = new TreeMap<>();
    private @Nullable Consumer<String> watcher;

    /**
     * Adds a resource pack.
     *
     * @param id the pack id
     * @param description the description
     * @param files file contents by asset path
     */
    public void putResourcePack(String id, String description, Map<String, byte[]> files) {
        packs.put(id, new ResourcePackInfo(id, description, List.copyOf(new TreeMap<>(files).keySet())));
        packFiles.put(id, new TreeMap<>(files));
    }

    /**
     * Reports a changed asset to the watcher, as a desktop file watcher would.
     *
     * @param path the asset path
     */
    public void simulateAssetChange(String path) {
        Consumer<String> current = watcher;
        if (current != null) {
            mainQueue.accept(() -> current.accept(path));
        }
    }

    /**
     * Returns whether someone watches the assets.
     *
     * @return {@code true} while a watcher is set
     */
    public boolean isWatched() {
        return watcher != null;
    }

    @Override
    public void listResourcePacks(PlatformCallback<List<ResourcePackInfo>> callback) {
        List<ResourcePackInfo> list = List.copyOf(packs.values());
        mainQueue.accept(() -> callback.success(list));
    }

    @Override
    public void readResourcePackFile(String packId, String path, PlatformCallback<ByteBuffer> callback) {
        read(packFiles.getOrDefault(packId, Map.of()), "resource pack file", path, callback);
    }

    @Override
    public void watchAssets(@Nullable Consumer<String> listener) {
        this.watcher = listener;
    }

    @Override
    public String userDataLocation() {
        return "memory (headless)";
    }

    static ByteBuffer direct(byte[] content) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(content.length).order(ByteOrder.nativeOrder());
        buffer.put(content).flip();
        return buffer;
    }
}
