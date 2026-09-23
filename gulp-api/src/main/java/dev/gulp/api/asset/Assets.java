package dev.gulp.api.asset;

import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.scheduler.Promise;

/**
 * Loads assets by key, asynchronously on every platform. Loaded assets are counted: each {@link #load(AssetKey)} needs
 * one {@link #unload(AssetKey)}, and an asset is freed, with the assets it depends on, when nothing uses it.
 *
 * <p>The {@code startup} group loads before {@code Game.onStart} behind a loading screen, so assets added to it in
 * {@code onLoad} can be used with {@link #get(AssetKey)} from {@code onStart} on.
 *
 * <pre>{@code
 * static final AssetKey<Texture> PLAYER = AssetKey.texture("coins:sprites/player");
 *
 * public void onLoad() {
 *     assets().startup().add(PLAYER).addFolder("coins:sprites/ui");
 * }
 *
 * public void onStart() {
 *     Texture player = assets().get(PLAYER);
 * }
 * }</pre>
 */
public interface Assets {

    /** Name of the group loaded before {@code Game.onStart}. */
    String STARTUP = "startup";

    /**
     * Returns a loaded asset.
     *
     * @param <T> the asset value
     * @param key the asset
     * @return the asset
     * @throws IllegalStateException if it is not loaded (yet), with a hint how to load it, or loading failed
     */
    <T> T get(AssetKey<T> key);

    /**
     * Loads an asset, or counts one more use of a loaded one.
     *
     * @param <T> the asset value
     * @param key the asset
     * @return the asset, delivered on the main thread
     */
    <T> Promise<T> load(AssetKey<T> key);

    /**
     * Returns whether an asset is loaded.
     *
     * @param key the asset
     * @return {@code true} once loading succeeded and until it is unloaded
     */
    boolean isLoaded(AssetKey<?> key);

    /**
     * Releases one use of an asset; the last one frees it and releases its dependencies.
     *
     * @param key the asset
     */
    void unload(AssetKey<?> key);

    /**
     * Returns the progress of everything being loaded now.
     *
     * @return from {@code 0} to {@code 1}; {@code 1} when nothing is loading
     */
    float progress();

    /**
     * Returns a group, creating it on first use.
     *
     * @param name the group name
     * @return the group
     */
    AssetGroup group(String name);

    /**
     * Returns the group loaded before {@code Game.onStart}.
     *
     * @return the {@value #STARTUP} group
     */
    AssetGroup startup();

    /**
     * Loads every asset of a group.
     *
     * @param name the group name
     * @return completes when all are loaded; fails with the first error
     */
    Promise<Void> loadGroup(String name);

    /**
     * Releases the assets of a group once.
     *
     * @param name the group name
     */
    void unloadGroup(String name);

    /**
     * Registers the loader of an asset type, replacing an earlier one.
     *
     * @param <T> the asset value
     * @param type the type
     * @param loader the loader
     */
    <T> void registerLoader(AssetType<T> type, AssetLoader<T> loader);

    /**
     * Replaces the screen drawn while the startup group loads.
     *
     * @param screen the loading screen
     */
    void setLoadingScreen(LoadingScreen screen);

    /**
     * Returns a region of a loaded atlas by its image path.
     *
     * @param key {@code namespace:atlas/region}, for example {@code coins:sprites/player/idle_0}
     * @return the region
     * @throws IllegalStateException if the atlas {@code coins:sprites} is not loaded
     * @throws IllegalArgumentException if the atlas has no such region
     */
    TextureRegion region(String key);

    /**
     * Returns the resource packs.
     *
     * @return the resource packs
     */
    ResourcePacks resourcePacks();
}
