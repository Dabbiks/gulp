package dev.gulp.api.asset;

import dev.gulp.api.scheduler.Promise;

/**
 * Turns a file into an asset. Register loaders for custom {@link AssetType}s in {@code onLoad}; they run on the main
 * thread and read files asynchronously through the context.
 *
 * <pre>{@code
 * assets().registerLoader(LEVEL, new AssetLoader<>() {
 *     public Promise<Level> load(AssetLoadContext context) {
 *         return context.text().map(Level::parse);
 *     }
 * });
 * }</pre>
 *
 * @param <T> the loaded value
 */
@FunctionalInterface
public interface AssetLoader<T> {

    /**
     * Starts loading an asset.
     *
     * @param context the file, its key and access to dependencies
     * @return the asset, delivered on the main thread
     */
    Promise<T> load(AssetLoadContext context);

    /**
     * Frees an asset nothing uses any more (GPU memory, for example). Dependencies are released by the engine.
     *
     * @param asset the asset being unloaded
     */
    default void dispose(T asset) {}
}
