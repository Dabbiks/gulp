package dev.gulp.core.data;

import dev.gulp.api.data.CodecException;
import dev.gulp.api.data.DataContainer;
import dev.gulp.api.data.DataType;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonValue;
import dev.gulp.api.registry.Key;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * {@link DataContainer} keeping values as written; values loaded from JSON stay raw until first read with a type.
 */
public final class DataContainerImpl implements DataContainer {

    /** A stored value: the type it was written or last read with, and the value (raw JSON if type is null). */
    private record Entry(@Nullable DataType<?> type, Object value) {}

    private final Map<Key, Entry> entries = new LinkedHashMap<>();

    /**
     * Creates a container from JSON.
     *
     * @param json member names are keys; values stay raw until read
     * @throws IllegalArgumentException if a member name is not a valid key
     */
    public DataContainerImpl(JsonObject json) {
        for (Map.Entry<String, JsonValue> member : json.members().entrySet()) {
            entries.put(Key.parse(member.getKey()), new Entry(null, member.getValue()));
        }
    }

    @Override
    public <T> void set(Key key, DataType<T> type, T value) {
        entries.put(key, new Entry(type, value));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(Key key, DataType<T> type) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.type == type) {
            return (T) entry.value;
        }
        JsonValue json = toJson(entry);
        T converted;
        try {
            converted = type.codec().decode(json);
        } catch (CodecException e) {
            throw new CodecException("Value at " + key + " cannot be read as " + type + ": " + e.getMessage(), e);
        }
        entries.put(key, new Entry(type, converted));
        return converted;
    }

    @Override
    public <T> T getOrDefault(Key key, DataType<T> type, T defaultValue) {
        T value = get(key, type);
        return value == null ? defaultValue : value;
    }

    @Override
    public boolean has(Key key) {
        return entries.containsKey(key);
    }

    @Override
    public boolean remove(Key key) {
        return entries.remove(key) != null;
    }

    @Override
    public Set<Key> keys() {
        return Set.copyOf(entries.keySet());
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public void clear() {
        entries.clear();
    }

    @Override
    public JsonObject toJson() {
        JsonObject.Builder builder = JsonObject.builder();
        for (Map.Entry<Key, Entry> entry : entries.entrySet()) {
            builder.put(entry.getKey().toString(), toJson(entry.getValue()));
        }
        return builder.build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static JsonValue toJson(Entry entry) {
        if (entry.type == null) {
            return (JsonValue) entry.value;
        }
        return ((DataType) entry.type).codec().encode(entry.value);
    }

    @Override
    public String toString() {
        return toJson().toString();
    }
}
