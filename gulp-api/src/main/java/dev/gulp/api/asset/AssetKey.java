package dev.gulp.api.asset;

import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.registry.Key;

/**
 * Typed key of an asset: {@code namespace:path} plus the {@link AssetType}. The key {@code coins:sprites/player} names
 * the file {@code assets/coins/sprites/player.png}; the extension may be left out.
 *
 * <pre>{@code
 * static final AssetKey<Texture> PLAYER = AssetKey.texture("coins:sprites/player");
 * Texture player = assets().get(PLAYER);
 * }</pre>
 *
 * @param <T> the loaded value
 * @param key the namespaced path
 * @param type the asset type
 */
public record AssetKey<T>(Key key, AssetType<T> type) {

    /**
     * Creates a key of any type.
     *
     * @param <T> the loaded value
     * @param type the asset type
     * @param key {@code namespace:path}
     * @return the key
     */
    public static <T> AssetKey<T> of(AssetType<T> type, String key) {
        return new AssetKey<>(Key.parse(key), type);
    }

    /**
     * Creates a texture key.
     *
     * @param key {@code namespace:path}
     * @return the key
     */
    public static AssetKey<Texture> texture(String key) {
        return of(AssetType.TEXTURE, key);
    }

    /**
     * Creates an image key.
     *
     * @param key {@code namespace:path}
     * @return the key
     */
    public static AssetKey<Pixmap> pixmap(String key) {
        return of(AssetType.PIXMAP, key);
    }

    /**
     * Creates a text key.
     *
     * @param key {@code namespace:path}
     * @return the key
     */
    public static AssetKey<String> text(String key) {
        return of(AssetType.TEXT, key);
    }

    /**
     * Creates a key for raw bytes.
     *
     * @param key {@code namespace:path}
     * @return the key
     */
    public static AssetKey<byte[]> bytes(String key) {
        return of(AssetType.BYTES, key);
    }

    @Override
    public String toString() {
        return key + " (" + type + ")";
    }
}
