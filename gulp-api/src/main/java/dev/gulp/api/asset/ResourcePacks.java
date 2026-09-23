package dev.gulp.api.asset;

import dev.gulp.api.scheduler.Promise;
import java.util.List;

/**
 * Resource packs: on desktop, folders or ZIP files in the {@code resourcepacks/} folder of the game's data; on the web,
 * packs bundled by the build. Enabled packs override assets by path, the first in the list winning; changing the list
 * reloads loaded assets in place and fires {@link ResourcePackChangeEvent}.
 *
 * <pre>{@code
 * assets().resourcePacks().setEnabled(List.of("hd-textures"));
 * }</pre>
 */
public interface ResourcePacks {

    /**
     * Returns the packs found.
     *
     * @return the packs
     */
    List<ResourcePack> available();

    /**
     * Returns the enabled packs, highest priority first.
     *
     * @return the packs
     */
    List<ResourcePack> enabled();

    /**
     * Enables packs in order and reloads loaded assets.
     *
     * @param ids pack ids, highest priority first; unknown ids are ignored with a warning
     * @return completes after the reload
     */
    Promise<Void> setEnabled(List<String> ids);
}
