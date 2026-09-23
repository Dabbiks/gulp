package dev.gulp.api.asset;

import java.util.Set;

/**
 * Named set of assets loaded and unloaded together, for example everything one level needs. Folders are expanded from
 * the asset manifest when the group loads.
 *
 * <pre>{@code
 * assets().group("level1")
 *         .add(AssetKey.texture("coins:backgrounds/forest"))
 *         .addFolder("coins:sprites/level1");
 * assets().loadGroup("level1").thenSync(done -> startLevel());
 * }</pre>
 */
public interface AssetGroup {

    /**
     * Returns the name.
     *
     * @return the name
     */
    String name();

    /**
     * Adds an asset.
     *
     * @param key the asset
     * @return this group
     */
    AssetGroup add(AssetKey<?> key);

    /**
     * Adds every file under a folder; the type of each file comes from its extension (images become textures, text
     * formats become text, anything else bytes).
     *
     * @param folder {@code namespace:path} of the folder
     * @return this group
     */
    AssetGroup addFolder(String folder);

    /**
     * Returns the assets added one by one; folder contents are known only after loading.
     *
     * @return the keys
     */
    Set<AssetKey<?>> keys();

    /**
     * Returns the folders added.
     *
     * @return the folders as {@code namespace:path}
     */
    Set<String> folders();

    /**
     * Returns whether every asset of the group is loaded.
     *
     * @return {@code true} after a successful {@link Assets#loadGroup(String)}
     */
    boolean isLoaded();

    /**
     * Returns the loading progress.
     *
     * @return from {@code 0} to {@code 1}
     */
    float progress();
}
