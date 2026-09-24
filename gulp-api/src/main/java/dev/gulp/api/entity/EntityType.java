package dev.gulp.api.entity;

import dev.gulp.api.PauseMode;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Keyed;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * What an entity is made of: size, component factories, tags, render layer. Components are given as factories so that
 * every entity gets its own instances. A type can extend another with {@link Builder#parent(EntityType)}.
 *
 * <pre>{@code
 * SLIME = registries().register(Registries.ENTITY_TYPE, EntityType.builder(key("slime"))
 *         .size(0.8f, 0.6f)
 *         .component(() -> new SpriteComponent(GameAssets.Sprites.SLIME))
 *         .component(SlimeAi::new)
 *         .tags("enemy")
 *         .layer("entities")
 *         .build());
 * }</pre>
 */
public final class EntityType implements Keyed {

    /** Default render layer of entities. */
    public static final String DEFAULT_LAYER = "entities";

    private final Key key;
    private final Vec2 size;
    private final List<Supplier<? extends Component>> components;
    private final Set<String> tags;
    private final String layer;
    private final int zIndex;
    private final boolean persistent;
    private final PauseMode pauseMode;

    private EntityType(Builder builder) {
        EntityType parent = builder.parent;
        List<Supplier<? extends Component>> all = new ArrayList<>();
        Set<String> allTags = new LinkedHashSet<>();
        if (parent != null) {
            all.addAll(parent.components);
            allTags.addAll(parent.tags);
        }
        all.addAll(builder.components);
        allTags.addAll(builder.tags);
        this.key = builder.key;
        this.components = Collections.unmodifiableList(all);
        this.tags = Collections.unmodifiableSet(allTags);
        this.size = builder.size != null ? builder.size : parent != null ? parent.size : Vec2.ONE;
        this.layer = builder.layer != null ? builder.layer : parent != null ? parent.layer : DEFAULT_LAYER;
        this.zIndex = builder.zIndex != null ? builder.zIndex : parent != null ? parent.zIndex : 0;
        this.persistent = builder.persistent != null ? builder.persistent : parent == null || parent.persistent;
        this.pauseMode =
                builder.pauseMode != null ? builder.pauseMode : parent != null ? parent.pauseMode : PauseMode.GAME;
    }

    /**
     * Starts building a type.
     *
     * @param key the type key, for example {@code key("slime")}
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
     * Returns the size of new entities.
     *
     * @return world units
     */
    public Vec2 size() {
        return size;
    }

    /**
     * Returns the component factories, the parent's first.
     *
     * @return the factories
     */
    public List<Supplier<? extends Component>> components() {
        return components;
    }

    /**
     * Returns the tags of new entities, the parent's included.
     *
     * @return the tags
     */
    public Set<String> tags() {
        return tags;
    }

    /**
     * Returns the render layer of new entities.
     *
     * @return the layer name
     */
    public String layer() {
        return layer;
    }

    /**
     * Returns the draw order of new entities.
     *
     * @return the order
     */
    public int zIndex() {
        return zIndex;
    }

    /**
     * Returns whether new entities are saved with their world.
     *
     * @return {@code true} by default
     */
    public boolean isPersistent() {
        return persistent;
    }

    /**
     * Returns whether new entities tick while the game is paused.
     *
     * @return {@link PauseMode#GAME} by default
     */
    public PauseMode pauseMode() {
        return pauseMode;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EntityType type && type.key.equals(key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "EntityType[" + key + "]";
    }

    /**
     * Builds an {@link EntityType}.
     *
     * <pre>{@code
     * EntityType bigSlime = EntityType.builder(key("big_slime")).parent(SLIME).size(1.6f, 1.2f).build();
     * }</pre>
     */
    public static final class Builder {
        private final Key key;
        private final List<Supplier<? extends Component>> components = new ArrayList<>();
        private final Set<String> tags = new LinkedHashSet<>();
        private @Nullable EntityType parent;
        private @Nullable Vec2 size;
        private @Nullable String layer;
        private @Nullable Integer zIndex;
        private @Nullable Boolean persistent;
        private @Nullable PauseMode pauseMode;

        private Builder(Key key) {
            this.key = key;
        }

        /**
         * Inherits the size, components, tags, layer and flags of another type; values set here win, components and
         * tags are added.
         *
         * @param type the parent type
         * @return this builder
         */
        public Builder parent(EntityType type) {
            this.parent = type;
            return this;
        }

        /**
         * Sets the size of the bounds.
         *
         * @param width world units
         * @param height world units
         * @return this builder
         * @throws IllegalArgumentException if a side is negative
         */
        public Builder size(float width, float height) {
            if (!(width >= 0f && height >= 0f)) {
                throw new IllegalArgumentException("Entity size must not be negative: " + width + " x " + height);
            }
            this.size = new Vec2(width, height);
            return this;
        }

        /**
         * Adds a component factory, called once per entity.
         *
         * @param factory creates the component
         * @return this builder
         */
        public Builder component(Supplier<? extends Component> factory) {
            components.add(factory);
            return this;
        }

        /**
         * Adds tags.
         *
         * @param names the tags
         * @return this builder
         */
        public Builder tags(String... names) {
            Collections.addAll(tags, names);
            return this;
        }

        /**
         * Sets the render layer.
         *
         * @param name the layer name
         * @return this builder
         */
        public Builder layer(String name) {
            this.layer = name;
            return this;
        }

        /**
         * Sets the draw order within the layer.
         *
         * @param order the order
         * @return this builder
         */
        public Builder zIndex(int order) {
            this.zIndex = order;
            return this;
        }

        /**
         * Chooses whether entities are saved with their world.
         *
         * @param value whether to save
         * @return this builder
         */
        public Builder persistent(boolean value) {
            this.persistent = value;
            return this;
        }

        /**
         * Chooses whether entities tick while the game is paused.
         *
         * @param mode the mode
         * @return this builder
         */
        public Builder pauseMode(PauseMode mode) {
            this.pauseMode = mode;
            return this;
        }

        /**
         * Builds the type.
         *
         * @return the type
         */
        public EntityType build() {
            return new EntityType(this);
        }
    }
}
