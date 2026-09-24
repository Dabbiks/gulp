package dev.gulp.api.world;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import org.jspecify.annotations.Nullable;

/**
 * Settings of a world. Change them with the {@code with}-style methods, which return copies.
 *
 * <pre>{@code
 * WorldSettings settings = WorldSettings.DEFAULT.gravity(new Vec2(0, 30)).tileSize(16).seed(42);
 * World level = worlds().create("level", settings);
 * }</pre>
 *
 * @param gravity acceleration in world units per second squared, down is positive (used by physics, stage 7)
 * @param tileSize pixel size of tile graphics; one tile is one world unit
 * @param bounds limits of the world, or {@code null} for none
 * @param ambientLight light where no light source reaches (used by lighting, stage 8)
 * @param seed seed of {@link World#rng()} and of chunk generation
 * @param persistent whether the world is saved (stage 10)
 * @param orientation layout of the tile map
 * @param tickWhenInactive whether the world ticks while another world is active
 */
public record WorldSettings(
        Vec2 gravity,
        int tileSize,
        @Nullable Rect bounds,
        Color ambientLight,
        long seed,
        boolean persistent,
        TileOrientation orientation,
        boolean tickWhenInactive) {

    /** Gravity 20 units/s² down, 16-pixel tiles, no bounds, white light, seed 0, orthogonal, paused when inactive. */
    public static final WorldSettings DEFAULT =
            new WorldSettings(new Vec2(0f, 20f), 16, null, Color.WHITE, 0L, false, TileOrientation.ORTHOGONAL, false);

    /**
     * Validates the settings.
     *
     * @param gravity gravity
     * @param tileSize tile size
     * @param bounds bounds
     * @param ambientLight ambient light
     * @param seed seed
     * @param persistent persistent
     * @param orientation orientation
     * @param tickWhenInactive tick when inactive
     */
    public WorldSettings {
        if (tileSize <= 0) {
            throw new IllegalArgumentException("Tile size must be positive: " + tileSize);
        }
    }

    /**
     * Returns a copy with another gravity.
     *
     * @param value world units per second squared
     * @return the settings
     */
    public WorldSettings gravity(Vec2 value) {
        return new WorldSettings(
                value, tileSize, bounds, ambientLight, seed, persistent, orientation, tickWhenInactive);
    }

    /**
     * Returns a copy with another tile size.
     *
     * @param pixels pixel size of tile graphics
     * @return the settings
     */
    public WorldSettings tileSize(int pixels) {
        return new WorldSettings(
                gravity, pixels, bounds, ambientLight, seed, persistent, orientation, tickWhenInactive);
    }

    /**
     * Returns a copy with bounds.
     *
     * @param value world units, or {@code null} for none
     * @return the settings
     */
    public WorldSettings bounds(@Nullable Rect value) {
        return new WorldSettings(
                gravity, tileSize, value, ambientLight, seed, persistent, orientation, tickWhenInactive);
    }

    /**
     * Returns a copy with another ambient light.
     *
     * @param value the colour
     * @return the settings
     */
    public WorldSettings ambientLight(Color value) {
        return new WorldSettings(gravity, tileSize, bounds, value, seed, persistent, orientation, tickWhenInactive);
    }

    /**
     * Returns a copy with another seed.
     *
     * @param value the seed
     * @return the settings
     */
    public WorldSettings seed(long value) {
        return new WorldSettings(
                gravity, tileSize, bounds, ambientLight, value, persistent, orientation, tickWhenInactive);
    }

    /**
     * Returns a copy that is or is not saved.
     *
     * @param value whether to save the world
     * @return the settings
     */
    public WorldSettings persistent(boolean value) {
        return new WorldSettings(gravity, tileSize, bounds, ambientLight, seed, value, orientation, tickWhenInactive);
    }

    /**
     * Returns a copy with another map orientation.
     *
     * @param value the orientation
     * @return the settings
     */
    public WorldSettings orientation(TileOrientation value) {
        return new WorldSettings(gravity, tileSize, bounds, ambientLight, seed, persistent, value, tickWhenInactive);
    }

    /**
     * Returns a copy that ticks, or does not, while another world is active.
     *
     * @param value whether to tick when inactive
     * @return the settings
     */
    public WorldSettings tickWhenInactive(boolean value) {
        return new WorldSettings(gravity, tileSize, bounds, ambientLight, seed, persistent, orientation, value);
    }
}
