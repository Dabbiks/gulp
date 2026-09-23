package dev.gulp.api.registry;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Content of one type, registered by key during {@code onLoad} and frozen afterwards.
 *
 * <pre>{@code
 * Registry<Item> items = registries().create(key("items"), Item.class);   // in onLoad
 * items.register(key("sword"), new Item(key("sword"), 100));
 * ...
 * Item sword = items.getOrThrow(Key.parse("coins:sword"));               // any time later
 * }</pre>
 *
 * @param <T> the type of registered values
 */
public interface Registry<T extends Keyed> extends Iterable<T> {

    /**
     * Returns the identifier of this registry.
     *
     * @return the registry key
     */
    RegistryKey<T> registryKey();

    /**
     * Registers a value.
     *
     * @param key the key; must equal {@code value.key()}
     * @param value the value
     * @return the value, for assigning to a constant
     * @throws IllegalStateException if the registry is frozen
     * @throws IllegalArgumentException if the key is taken, reserved, or differs from the value's key
     */
    T register(Key key, T value);

    /**
     * Registers a value under its own key.
     *
     * @param value the value
     * @return the value
     * @throws IllegalStateException if the registry is frozen
     * @throws IllegalArgumentException if the key is taken or reserved
     */
    default T register(T value) {
        return register(value.key(), value);
    }

    /**
     * Returns a registered value.
     *
     * @param key the key
     * @return the value, or {@code null} if none is registered
     */
    @Nullable T get(Key key);

    /**
     * Returns a registered value.
     *
     * @param key the key
     * @return the value
     * @throws IllegalArgumentException if none is registered, naming the registry and key
     */
    T getOrThrow(Key key);

    /**
     * Returns whether a key is registered.
     *
     * @param key the key
     * @return {@code true} if registered
     */
    boolean contains(Key key);

    /**
     * Returns the registered keys in registration order.
     *
     * @return an unmodifiable view
     */
    Set<Key> keys();

    /**
     * Returns the registered values in registration order.
     *
     * @return an unmodifiable view
     */
    Collection<T> values();

    /**
     * Returns the registered values as a stream.
     *
     * @return a stream in registration order
     */
    Stream<T> stream();

    /**
     * Returns the number of registered values.
     *
     * @return the size
     */
    int size();

    /**
     * Returns whether registration is closed.
     *
     * @return {@code true} after the load phase
     */
    boolean isFrozen();
}
