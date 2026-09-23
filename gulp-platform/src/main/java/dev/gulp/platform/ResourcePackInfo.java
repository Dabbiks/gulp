package dev.gulp.platform;

import java.util.List;

/**
 * A resource pack found by the platform.
 *
 * <pre>{@code
 * files.listResourcePacks(callback); // callback.success(List.of(new ResourcePackInfo("hd", "HD textures", paths)))
 * }</pre>
 *
 * @param id the folder or file name
 * @param description the first line of the pack's {@code pack.txt}, or empty
 * @param files paths of the files it contains, relative to its assets, as in the asset manifest
 */
public record ResourcePackInfo(String id, String description, List<String> files) {}
