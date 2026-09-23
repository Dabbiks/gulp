package dev.gulp.backend.headless;

import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformFiles;
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
        read(assets, "asset", path, callback);
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
