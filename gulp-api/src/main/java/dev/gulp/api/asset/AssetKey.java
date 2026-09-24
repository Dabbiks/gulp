package dev.gulp.api.asset;

import dev.gulp.api.audio.AudioClip;
import dev.gulp.api.audio.Music;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureAtlas;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.registry.Key;
import dev.gulp.api.text.Font;

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

    /**
     * Creates a font key.
     *
     * @param key {@code namespace:path}
     * @return the key
     */
    public static AssetKey<Font> font(String key) {
        return of(AssetType.FONT, key);
    }

    /**
     * Creates an atlas key.
     *
     * @param key {@code namespace:path} of the atlas, for example {@code coins:sprites}
     * @return the key
     */
    public static AssetKey<TextureAtlas> atlas(String key) {
        return of(AssetType.ATLAS, key);
    }

    /**
     * Creates a key for one atlas region.
     *
     * @param key {@code namespace:atlas/region}, for example {@code coins:sprites/player/idle_0}
     * @return the key
     */
    public static AssetKey<TextureRegion> region(String key) {
        return of(AssetType.REGION, key);
    }

    /**
     * Creates a key for a short sound decoded into memory.
     *
     * @param key {@code namespace:path}, for example {@code coins:sounds/pickup}
     * @return the key
     */
    public static AssetKey<AudioClip> audio(String key) {
        return of(AssetType.AUDIO, key);
    }

    /**
     * Creates a key for a streamed music track.
     *
     * @param key {@code namespace:path}, for example {@code coins:music/theme}
     * @return the key
     */
    public static AssetKey<Music> music(String key) {
        return of(AssetType.MUSIC, key);
    }

    @Override
    public String toString() {
        return key + " (" + type + ")";
    }
}
