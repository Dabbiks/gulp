package dev.gulp.platform;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Read-only game assets and writable user data. All reads are asynchronous because the web can only fetch.
 *
 * <p>Asset paths are relative to the assets root and use {@code /}, for example {@code coins/textures/player.png}.
 * User data lives in the platform's application data directory on desktop and in IndexedDB on the web.
 *
 * <pre>{@code
 * files.readAsset("assets.manifest.json", callback);
 * files.writeUserData("saves/slot1.bin", bytes, callback);
 * }</pre>
 */
public interface PlatformFiles {

    /**
     * Reads a whole asset.
     *
     * @param path path relative to the assets root
     * @param callback receives the bytes, or fails if the asset does not exist
     */
    void readAsset(String path, PlatformCallback<ByteBuffer> callback);

    /**
     * Reads a user data file.
     *
     * @param name file name relative to the game's data directory
     * @param callback receives the bytes, or fails if the file does not exist
     */
    void readUserData(String name, PlatformCallback<ByteBuffer> callback);

    /**
     * Writes a user data file atomically, replacing any previous content.
     *
     * @param name file name relative to the game's data directory
     * @param data bytes from position to limit
     * @param callback completes when the data is durable
     */
    void writeUserData(String name, ByteBuffer data, PlatformCallback<Void> callback);

    /**
     * Deletes a user data file. Deleting a missing file succeeds.
     *
     * @param name file name relative to the game's data directory
     * @param callback completes when deleted
     */
    void deleteUserData(String name, PlatformCallback<Void> callback);

    /**
     * Lists user data files.
     *
     * @param prefix only names starting with this prefix, or an empty string for all
     * @param callback receives the names
     */
    void listUserData(String prefix, PlatformCallback<List<String>> callback);

    /**
     * Describes where user data is stored, for logs and crash reports.
     *
     * @return for example a directory path or {@code "IndexedDB gulp/coins"}
     */
    String userDataLocation();
}
