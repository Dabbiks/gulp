package dev.gulp.api.world;

import dev.gulp.api.registry.Keyed;

/**
 * Type of tile in a tile map. Registered in {@code Registries}; the full definition arrives in roadmap stage 6.
 *
 * <pre>{@code
 * TileType grass = registries().get(Registries.TILE_TYPE).getOrThrow(key("grass"));
 * }</pre>
 */
public interface TileType extends Keyed {}
