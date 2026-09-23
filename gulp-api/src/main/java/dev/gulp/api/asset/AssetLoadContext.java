package dev.gulp.api.asset;

import dev.gulp.api.scheduler.Promise;

/**
 * What an {@link AssetLoader} gets: the key, the resolved file and asynchronous reads. Assets requested through
 * {@link #dependency(AssetKey)} stay loaded as long as the asset that needs them.
 *
 * <pre>{@code
 * public Promise<Tileset> load(AssetLoadContext context) {
 *     return context.text().flatMap(json -> context.dependency(AssetKey.texture(imageOf(json)))
 *             .map(texture -> new Tileset(json, texture)));
 * }
 * }</pre>
 */
public interface AssetLoadContext {

    /**
     * Returns the key being loaded.
     *
     * @return the key
     */
    AssetKey<?> key();

    /**
     * Returns the file path inside the assets folder, with the extension, for example
     * {@code coins/sprites/player.png}.
     *
     * @return the path
     */
    String path();

    /**
     * Reads the file.
     *
     * @return the contents
     */
    Promise<byte[]> bytes();

    /**
     * Reads the file as UTF-8 text.
     *
     * @return the text
     */
    Promise<String> text();

    /**
     * Loads another asset this one needs; it is released when this one is unloaded.
     *
     * @param <D> the dependency's value
     * @param key the dependency
     * @return the dependency
     */
    <D> Promise<D> dependency(AssetKey<D> key);
}
