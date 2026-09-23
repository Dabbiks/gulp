package dev.gulp.api.data;

import dev.gulp.api.registry.Key;
import dev.gulp.api.spi.ApiSupport;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Arbitrary typed data attached to a module, entity, world, tile or save slot, stored by key. Modules add their own
 * data to shared objects without subclassing them.
 *
 * <pre>{@code
 * Key kills = key("kills");
 * int current = entity.data().getOrDefault(kills, DataType.INT, 0);
 * entity.data().set(kills, DataType.INT, current + 1);
 * }</pre>
 *
 * <p>Reading with a different type than the value was written with converts through JSON when the shapes match (for
 * example {@code INT} as {@code LONG}) and fails with {@link CodecException} otherwise.
 */
public interface DataContainer {

    /**
     * Creates an empty container.
     *
     * @return the container
     */
    static DataContainer create() {
        return ApiSupport.get().newDataContainer(JsonObject.EMPTY);
    }

    /**
     * Creates a container from its JSON form, as produced by {@link #toJson()}.
     *
     * @param json the JSON object whose member names are keys
     * @return the container
     * @throws IllegalArgumentException if a member name is not a valid key
     */
    static DataContainer fromJson(JsonObject json) {
        return ApiSupport.get().newDataContainer(json);
    }

    /**
     * Stores a value.
     *
     * @param <T> the value type
     * @param key the key
     * @param type the data type
     * @param value the value
     */
    <T> void set(Key key, DataType<T> type, T value);

    /**
     * Reads a value.
     *
     * @param <T> the value type
     * @param key the key
     * @param type the data type to read as
     * @return the value, or {@code null} if absent
     * @throws CodecException if the stored value cannot be read as this type
     */
    <T> @Nullable T get(Key key, DataType<T> type);

    /**
     * Reads a value or returns a default.
     *
     * @param <T> the value type
     * @param key the key
     * @param type the data type to read as
     * @param defaultValue returned when absent
     * @return the value or the default
     * @throws CodecException if the stored value cannot be read as this type
     */
    <T> T getOrDefault(Key key, DataType<T> type, T defaultValue);

    /**
     * Returns whether a value is stored.
     *
     * @param key the key
     * @return {@code true} if present
     */
    boolean has(Key key);

    /**
     * Removes a value.
     *
     * @param key the key
     * @return {@code true} if a value was removed
     */
    boolean remove(Key key);

    /**
     * Returns the stored keys.
     *
     * @return an unmodifiable snapshot
     */
    Set<Key> keys();

    /**
     * Returns whether nothing is stored.
     *
     * @return {@code true} if empty
     */
    boolean isEmpty();

    /** Removes everything. */
    void clear();

    /**
     * Converts the container to JSON, one member per key.
     *
     * @return the JSON form
     */
    JsonObject toJson();
}
