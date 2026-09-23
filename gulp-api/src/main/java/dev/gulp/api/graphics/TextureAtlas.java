package dev.gulp.api.graphics;

import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Images packed into texture pages, found by their original path. Atlases come from {@code packAssets}, which packs every
 * {@code sprites/} folder, from TexturePacker JSON, or (in development) are packed when loaded.
 *
 * <pre>{@code
 * TextureAtlas sprites = assets().get(AssetKey.atlas("coins:sprites"));
 * TextureRegion idle = sprites.region("player/idle_0");
 * List<TextureRegion> run = sprites.regions("player/run_"); // run_0, run_1, ... in order
 * }</pre>
 */
public interface TextureAtlas {

    /**
     * Returns a region.
     *
     * @param name the path inside the atlas folder, without extension
     * @return the region
     * @throws IllegalArgumentException if there is none
     */
    TextureRegion region(String name);

    /**
     * Returns a region if it exists.
     *
     * @param name the path without extension
     * @return the region, or {@code null}
     */
    @Nullable TextureRegion find(String name);

    /**
     * Returns the regions whose names start with a prefix, ordered by name with numbers compared as numbers.
     *
     * @param prefix the prefix, for example {@code player/run_}
     * @return the regions
     */
    List<TextureRegion> regions(String prefix);

    /**
     * Returns all region names.
     *
     * @return the names
     */
    Set<String> names();

    /**
     * Returns the texture pages.
     *
     * @return the pages
     */
    List<Texture> pages();
}
