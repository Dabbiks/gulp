package dev.gulp.api.registry;

/**
 * Typed identifier of a registry.
 *
 * <pre>{@code
 * public static final RegistryKey<Item> ITEMS = RegistryKey.of(Key.of("shop", "items"), Item.class);
 * }</pre>
 *
 * @param <T> the type of registered values
 * @param key the registry key
 * @param type the class of registered values
 */
public record RegistryKey<T extends Keyed>(Key key, Class<T> type) {

    /**
     * Creates a registry key.
     *
     * @param <T> the type of registered values
     * @param key the registry key
     * @param type the class of registered values
     * @return the registry key
     */
    public static <T extends Keyed> RegistryKey<T> of(Key key, Class<T> type) {
        return new RegistryKey<>(key, type);
    }

    /**
     * Returns the key as text.
     *
     * @return {@code namespace:path}
     */
    @Override
    public String toString() {
        return key.toString();
    }
}
