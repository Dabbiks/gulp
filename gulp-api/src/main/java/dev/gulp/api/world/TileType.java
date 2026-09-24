package dev.gulp.api.world;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Keyed;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A kind of tile: its look (an atlas region or a tile of a {@link TileSet}, optionally animated), collision shape,
 * friction, properties and behaviour. Register tile types in {@code Registries.TILE_TYPE}; maps imported from Tiled or
 * LDtk also create unregistered types for their own tiles.
 *
 * <pre>{@code
 * GRASS = registries().register(Registries.TILE_TYPE, TileType.builder(key("grass"))
 *         .tileSet(TERRAIN, 0)
 *         .tickRandomly(0.01f, (world, layer, x, y) -> layer.set(x, y + 1, FLOWER))
 *         .build());
 * WALL = registries().register(Registries.TILE_TYPE, TileType.builder(key("wall"))
 *         .region(GameAssets.Sprites.WALL).shape(TileShape.FULL).build());
 * }</pre>
 */
public final class TileType implements Keyed {

    /** Runs for a tile: random ticks, periodic ticks and interaction. */
    @FunctionalInterface
    public interface Behaviour {
        /**
         * Runs for one tile.
         *
         * @param world the world
         * @param layer the layer the tile is on
         * @param x tile column
         * @param y tile row
         */
        void run(World world, TileLayer layer, int x, int y);
    }

    /** Runs when an entity interacts with a tile. */
    @FunctionalInterface
    public interface Interaction {
        /**
         * Handles the interaction.
         *
         * @param who the entity interacting, or {@code null} for the game itself
         * @param layer the layer
         * @param x tile column
         * @param y tile row
         */
        void interact(@Nullable Entity who, TileLayer layer, int x, int y);
    }

    private final Key key;
    private final List<AssetKey<TextureRegion>> regions;
    private final @Nullable TileSet tileSet;
    private final int[] tileIndices;
    private final @Nullable TextureRegion directRegion;
    private final float frameSeconds;
    private final TileShape shape;
    private final float friction;
    private final Map<String, String> properties;
    private final @Nullable Interaction interaction;
    private final float randomTickChance;
    private final @Nullable Behaviour randomTick;
    private final int tickPeriod;
    private final @Nullable Behaviour periodicTick;
    private final boolean stateful;

    private TileType(Builder builder) {
        this.key = builder.key;
        this.regions = Collections.unmodifiableList(new ArrayList<>(builder.regions));
        this.tileSet = builder.tileSet;
        this.tileIndices =
                builder.tileIndices.stream().mapToInt(Integer::intValue).toArray();
        this.directRegion = builder.directRegion;
        this.frameSeconds = builder.frameSeconds;
        this.shape = builder.shape;
        this.friction = builder.friction;
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(builder.properties));
        this.interaction = builder.interaction;
        this.randomTickChance = builder.randomTickChance;
        this.randomTick = builder.randomTick;
        this.tickPeriod = builder.tickPeriod;
        this.periodicTick = builder.periodicTick;
        this.stateful = builder.stateful;
    }

    /**
     * Starts building a tile type.
     *
     * @param key the key
     * @return the builder
     */
    public static Builder builder(Key key) {
        return new Builder(key);
    }

    @Override
    public Key key() {
        return key;
    }

    /**
     * Returns the atlas regions: one, or the frames of an animation.
     *
     * @return the region keys, empty when the look comes from a tile set
     */
    public List<AssetKey<TextureRegion>> regions() {
        return regions;
    }

    /**
     * Returns the tile set of the look.
     *
     * @return the tile set, or {@code null}
     */
    public @Nullable TileSet tileSet() {
        return tileSet;
    }

    /**
     * Returns the tile numbers in the tile set: one, or the frames of an animation.
     *
     * @return the numbers, a copy
     */
    public int[] tileIndices() {
        return tileIndices.clone();
    }

    /**
     * Returns the number of animation frames.
     *
     * @return 1 for still tiles, 0 for tiles without a look
     */
    public int frameCount() {
        return directRegion != null ? 1 : Math.max(regions.size(), tileIndices.length);
    }

    /**
     * Returns a region given directly, used by imported maps.
     *
     * @return the region, or {@code null}
     */
    public @Nullable TextureRegion directRegion() {
        return directRegion;
    }

    /**
     * Returns how long each animation frame shows.
     *
     * @return seconds
     */
    public float frameSeconds() {
        return frameSeconds;
    }

    /**
     * Returns the collision shape.
     *
     * @return the shape, {@link TileShape#NONE} by default
     */
    public TileShape shape() {
        return shape;
    }

    /**
     * Returns the friction for things standing on the tile.
     *
     * @return {@code 1} by default
     */
    public float friction() {
        return friction;
    }

    /**
     * Returns custom properties, for example those set in Tiled.
     *
     * @return the properties
     */
    public Map<String, String> properties() {
        return properties;
    }

    /**
     * Returns the interaction handler.
     *
     * @return the handler, or {@code null}
     */
    public @Nullable Interaction interaction() {
        return interaction;
    }

    /**
     * Returns the chance per tick that a tile of this type gets a random tick.
     *
     * @return {@code 0..1}
     */
    public float randomTickChance() {
        return randomTickChance;
    }

    /**
     * Returns the random tick behaviour.
     *
     * @return the behaviour, or {@code null}
     */
    public @Nullable Behaviour randomTick() {
        return randomTick;
    }

    /**
     * Returns the period of periodic ticks.
     *
     * @return ticks, 0 for none
     */
    public int tickPeriod() {
        return tickPeriod;
    }

    /**
     * Returns the periodic tick behaviour.
     *
     * @return the behaviour, or {@code null}
     */
    public @Nullable Behaviour periodicTick() {
        return periodicTick;
    }

    /**
     * Returns whether every tile of this type gets a {@link TileState} when placed.
     *
     * @return {@code true} for stateful tiles such as chests
     */
    public boolean isStateful() {
        return stateful;
    }

    /**
     * Returns whether tiles of this type tick.
     *
     * @return {@code true} if random or periodic ticks are set
     */
    public boolean ticks() {
        return randomTick != null || periodicTick != null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TileType type && type.key.equals(key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "TileType[" + key + "]";
    }

    /**
     * Builds a {@link TileType}.
     *
     * <pre>{@code
     * TileType water = TileType.builder(key("water"))
     *         .animation(0.25f, List.of(GameAssets.Sprites.WATER_0, GameAssets.Sprites.WATER_1)).build();
     * }</pre>
     */
    public static final class Builder {
        private final Key key;
        private final List<AssetKey<TextureRegion>> regions = new ArrayList<>();
        private final List<Integer> tileIndices = new ArrayList<>();
        private final Map<String, String> properties = new LinkedHashMap<>();
        private @Nullable TileSet tileSet;
        private @Nullable TextureRegion directRegion;
        private float frameSeconds = 0.2f;
        private TileShape shape = TileShape.NONE;
        private float friction = 1f;
        private @Nullable Interaction interaction;
        private float randomTickChance;
        private @Nullable Behaviour randomTick;
        private int tickPeriod;
        private @Nullable Behaviour periodicTick;
        private boolean stateful;

        private Builder(Key key) {
            this.key = key;
        }

        /**
         * Uses an atlas region as the look.
         *
         * @param region the region key
         * @return this builder
         */
        public Builder region(AssetKey<TextureRegion> region) {
            regions.clear();
            regions.add(region);
            return this;
        }

        /**
         * Uses a region already in memory, for tiles created from loaded images.
         *
         * @param region the region
         * @return this builder
         */
        public Builder region(TextureRegion region) {
            this.directRegion = region;
            return this;
        }

        /**
         * Uses a tile of a tile set as the look.
         *
         * @param set the tile set
         * @param index the tile number
         * @return this builder
         */
        public Builder tileSet(TileSet set, int index) {
            this.tileSet = set;
            tileIndices.clear();
            tileIndices.add(index);
            return this;
        }

        /**
         * Animates the look through atlas regions.
         *
         * @param secondsPerFrame how long each frame shows
         * @param frames the regions
         * @return this builder
         */
        public Builder animation(float secondsPerFrame, List<AssetKey<TextureRegion>> frames) {
            regions.clear();
            regions.addAll(frames);
            this.frameSeconds = secondsPerFrame;
            return this;
        }

        /**
         * Animates the look through tiles of a tile set.
         *
         * @param set the tile set
         * @param secondsPerFrame how long each frame shows
         * @param indices the tile numbers
         * @return this builder
         */
        public Builder animation(TileSet set, float secondsPerFrame, int... indices) {
            this.tileSet = set;
            tileIndices.clear();
            for (int index : indices) {
                tileIndices.add(index);
            }
            this.frameSeconds = secondsPerFrame;
            return this;
        }

        /**
         * Sets the collision shape.
         *
         * @param value the shape
         * @return this builder
         */
        public Builder shape(TileShape value) {
            this.shape = value;
            return this;
        }

        /**
         * Sets the friction.
         *
         * @param value the friction, {@code 1} normal, below 1 slippery
         * @return this builder
         */
        public Builder friction(float value) {
            this.friction = value;
            return this;
        }

        /**
         * Adds a custom property.
         *
         * @param name the name
         * @param value the value
         * @return this builder
         */
        public Builder property(String name, String value) {
            properties.put(name, value);
            return this;
        }

        /**
         * Sets what happens when something interacts with the tile.
         *
         * @param handler the handler
         * @return this builder
         */
        public Builder onInteract(Interaction handler) {
            this.interaction = handler;
            return this;
        }

        /**
         * Gives tiles random ticks, like growing grass.
         *
         * @param chance chance per tick for each tile, {@code 0..1}
         * @param behaviour what happens
         * @return this builder
         */
        public Builder tickRandomly(float chance, Behaviour behaviour) {
            if (!(chance > 0f && chance <= 1f)) {
                throw new IllegalArgumentException("Chance must be in (0, 1]: " + chance);
            }
            this.randomTickChance = chance;
            this.randomTick = behaviour;
            return this;
        }

        /**
         * Gives tiles periodic ticks.
         *
         * @param ticks the period, at least 1
         * @param behaviour what happens
         * @return this builder
         */
        public Builder tickEvery(int ticks, Behaviour behaviour) {
            if (ticks < 1) {
                throw new IllegalArgumentException("Period must be at least 1 tick: " + ticks);
            }
            this.tickPeriod = ticks;
            this.periodicTick = behaviour;
            return this;
        }

        /**
         * Gives every tile of this type a {@link TileState} when placed.
         *
         * @param value whether tiles are stateful
         * @return this builder
         */
        public Builder stateful(boolean value) {
            this.stateful = value;
            return this;
        }

        /**
         * Builds the type.
         *
         * @return the type
         */
        public TileType build() {
            return new TileType(this);
        }
    }
}
