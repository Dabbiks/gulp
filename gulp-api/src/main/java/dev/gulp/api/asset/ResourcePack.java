package dev.gulp.api.asset;

/**
 * A folder or ZIP in {@code resourcepacks/} whose files replace the game's assets with the same paths.
 *
 * <pre>{@code
 * for (ResourcePack pack : assets().resourcePacks().available()) logger().info(pack.id());
 * }</pre>
 *
 * @param id the folder or file name
 * @param description text from the pack's {@code pack.txt}, or empty
 */
public record ResourcePack(String id, String description) {}
