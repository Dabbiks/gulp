package dev.gulp.api.registry;

import dev.gulp.api.audio.Sound;
import dev.gulp.api.entity.ComponentType;
import dev.gulp.api.entity.DamageType;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.particle.ParticleEffect;
import dev.gulp.api.physics.CollisionLayer;
import dev.gulp.api.ui.Theme;
import dev.gulp.api.ui.Transition;
import dev.gulp.api.world.TileType;
import java.util.Collection;
import org.jspecify.annotations.Nullable;

/**
 * All registries: the built-in ones listed as constants here, and those created by the game.
 *
 * <pre>{@code
 * @Override public void onLoad() {
 *     JUMP = registries().register(Registries.INPUT_ACTION, InputAction.builder(key("jump")).build());
 *     Registry<Item> items = registries().create(key("items"), Item.class);
 * }
 * }</pre>
 *
 * <p>Registration is allowed only during the load phase ({@code onLoad} of the game and its modules); afterwards every
 * registry is frozen and writes throw {@link IllegalStateException}.
 */
public interface Registries {

    /** Entity types. */
    RegistryKey<EntityType> ENTITY_TYPE = builtIn("entity_type", EntityType.class);
    /** Tile types. */
    RegistryKey<TileType> TILE_TYPE = builtIn("tile_type", TileType.class);
    /** Component types. */
    RegistryKey<ComponentType> COMPONENT_TYPE = builtIn("component_type", ComponentType.class);
    /** Sound definitions. */
    RegistryKey<Sound> SOUND = builtIn("sound", Sound.class);
    /** Particle effects. */
    RegistryKey<ParticleEffect> PARTICLE_EFFECT = builtIn("particle_effect", ParticleEffect.class);
    /** Input actions. */
    RegistryKey<InputAction> INPUT_ACTION = builtIn("input_action", InputAction.class);
    /** Collision layers. */
    RegistryKey<CollisionLayer> COLLISION_LAYER = builtIn("collision_layer", CollisionLayer.class);
    /** Damage types. */
    RegistryKey<DamageType> DAMAGE_TYPE = builtIn("damage_type", DamageType.class);
    /** Screen and world transitions. */
    RegistryKey<Transition> TRANSITION = builtIn("transition", Transition.class);
    /** UI themes. */
    RegistryKey<Theme> THEME = builtIn("theme", Theme.class);

    /**
     * Returns a registry.
     *
     * @param <T> the value type
     * @param key the registry key
     * @return the registry
     * @throws IllegalArgumentException if no such registry exists
     */
    <T extends Keyed> Registry<T> get(RegistryKey<T> key);

    /**
     * Returns a registry by key, without type information.
     *
     * @param key the registry key
     * @return the registry, or {@code null} if none exists
     */
    @Nullable Registry<?> get(Key key);

    /**
     * Registers a value in a registry.
     *
     * @param <T> the value type
     * @param registry the registry key
     * @param value the value, registered under its own key
     * @return the value, for assigning to a constant
     * @throws IllegalStateException if registries are frozen
     */
    default <T extends Keyed> T register(RegistryKey<T> registry, T value) {
        return get(registry).register(value);
    }

    /**
     * Creates a game registry. Allowed only during the load phase.
     *
     * @param <T> the value type
     * @param key the registry key, in the game namespace
     * @param type the class of values
     * @return the new registry
     * @throws IllegalStateException if registries are frozen
     * @throws IllegalArgumentException if a registry with this key exists or the key is reserved
     */
    <T extends Keyed> Registry<T> create(Key key, Class<T> type);

    /**
     * Returns every registry.
     *
     * @return the registries in creation order, built-in first
     */
    Collection<Registry<?>> all();

    /**
     * Returns whether registries are frozen.
     *
     * @return {@code true} after the load phase
     */
    boolean isFrozen();

    private static <T extends Keyed> RegistryKey<T> builtIn(String path, Class<T> type) {
        return RegistryKey.of(Key.of(Key.RESERVED, path), type);
    }
}
