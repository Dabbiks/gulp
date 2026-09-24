package dev.gulp.api.asset;

import dev.gulp.api.audio.AudioClip;
import dev.gulp.api.audio.Music;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureAtlas;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.text.Font;
import java.util.List;
import java.util.Locale;

/**
 * Kind of asset: what {@link Assets#get(AssetKey)} returns and which file extensions it is read from. Built-in types
 * cover textures, images, text and raw bytes; games add their own with {@link #of(String, String...)} and an
 * {@link AssetLoader}.
 *
 * <pre>{@code
 * AssetType<Level> LEVEL = AssetType.of("level", "lvl");
 * assets().registerLoader(LEVEL, context -> context.text().map(Level::parse));
 * }</pre>
 *
 * @param <T> the loaded value
 */
public final class AssetType<T> {

    /** GPU texture from PNG, JPG or WebP; disposed when unloaded. */
    public static final AssetType<Texture> TEXTURE = new AssetType<>("texture", List.of("png", "jpg", "jpeg", "webp"));

    /** Image in memory, for pixel access or {@code Graphics.texture(pixmap)}. */
    public static final AssetType<Pixmap> PIXMAP = new AssetType<>("pixmap", List.of("png", "jpg", "jpeg", "webp"));

    /** UTF-8 text. */
    public static final AssetType<String> TEXT = new AssetType<>(
            "text",
            List.of(
                    "txt", "json", "yml", "yaml", "md", "csv", "glsl", "frag", "vert", "tmj", "tsj", "tmx", "tsx",
                    "ldtk"));

    /** File contents as bytes. */
    public static final AssetType<byte[]> BYTES = new AssetType<>("bytes", List.of("bin"));

    /** Font: MSDF ({@code .msdf.json}), bitmap ({@code .fnt}) or dynamic ({@code .ttf}, {@code .otf}). */
    public static final AssetType<Font> FONT = new AssetType<>("font", List.of("msdf.json", "fnt", "ttf", "otf"));

    /**
     * Texture atlas: a packed {@code .atlas.json} (or TexturePacker JSON) with its pages; in development a folder of
     * images packed when loaded.
     */
    public static final AssetType<TextureAtlas> ATLAS = new AssetType<>("atlas", List.of("atlas.json", "json"));

    /**
     * One region of an atlas, keyed by the image path: {@code coins:sprites/player/idle_0} is region
     * {@code player/idle_0} of atlas {@code coins:sprites}, which is loaded with it.
     */
    public static final AssetType<TextureRegion> REGION = new AssetType<>("region", List.of());

    /** A short sound decoded into memory: OGG Vorbis or WAV. */
    public static final AssetType<AudioClip> AUDIO = new AssetType<>("audio", List.of("ogg", "wav"));

    /** A music track streamed while it plays: OGG Vorbis or WAV. */
    public static final AssetType<Music> MUSIC = new AssetType<>("music", List.of("ogg", "wav"));

    private final String name;
    private final List<String> extensions;

    private AssetType(String name, List<String> extensions) {
        this.name = name;
        this.extensions = extensions;
    }

    /**
     * Creates an asset type for a custom loader.
     *
     * @param <T> the loaded value
     * @param name short name for messages
     * @param extensions file extensions tried when a key has none, in order, without the dot
     * @return the type; compare types by identity, so keep it in a constant
     */
    public static <T> AssetType<T> of(String name, String... extensions) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("Asset type name must not be blank");
        }
        List<String> list = List.of(extensions);
        for (String extension : list) {
            if (extension.isEmpty() || extension.startsWith(".")) {
                throw new IllegalArgumentException("Give extensions without the dot: '" + extension + "'");
            }
        }
        return new AssetType<>(
                name, list.stream().map(e -> e.toLowerCase(Locale.ROOT)).toList());
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the extensions tried for keys without one.
     *
     * @return the extensions, lower case, without the dot
     */
    public List<String> extensions() {
        return extensions;
    }

    @Override
    public String toString() {
        return name;
    }
}
