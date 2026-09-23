package dev.gulp.core.data;

import dev.gulp.api.data.Codec;
import dev.gulp.api.data.CodecException;
import dev.gulp.api.data.ConfigSection;
import dev.gulp.api.data.JsonArray;
import dev.gulp.api.data.JsonBoolean;
import dev.gulp.api.data.JsonNumber;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonString;
import dev.gulp.api.data.JsonValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Typed getters shared by configs and their sections, on top of {@link #getValue(String)}. */
abstract class AbstractSection implements ConfigSection {

    @Override
    public boolean contains(String path) {
        return getValue(path) != null;
    }

    @Override
    public Set<String> keys() {
        JsonValue self = path().isEmpty() ? rootValue() : rootValue(path());
        return self instanceof JsonObject object ? Set.copyOf(object.names()) : Set.of();
    }

    abstract JsonValue rootValue();

    @Nullable JsonValue rootValue(String absolutePath) {
        return ConfigImpl.walk(rootValue(), absolutePath);
    }

    @Override
    public String getString(String path, String defaultValue) {
        JsonValue value = getValue(path);
        return switch (value) {
            case JsonString s -> s.value();
            case JsonNumber n -> n.toString();
            case JsonBoolean b -> Boolean.toString(b.value());
            case null, default -> defaultValue;
        };
    }

    @Override
    public int getInt(String path, int defaultValue) {
        return getValue(path) instanceof JsonNumber n ? n.intValue() : defaultValue;
    }

    @Override
    public long getLong(String path, long defaultValue) {
        return getValue(path) instanceof JsonNumber n ? n.longValue() : defaultValue;
    }

    @Override
    public float getFloat(String path, float defaultValue) {
        return getValue(path) instanceof JsonNumber n ? (float) n.doubleValue() : defaultValue;
    }

    @Override
    public double getDouble(String path, double defaultValue) {
        return getValue(path) instanceof JsonNumber n ? n.doubleValue() : defaultValue;
    }

    @Override
    public boolean getBoolean(String path, boolean defaultValue) {
        return getValue(path) instanceof JsonBoolean b ? b.value() : defaultValue;
    }

    @Override
    public <T> List<T> getList(String path, Codec<T> element) {
        JsonValue value = getValue(path);
        if (!(value instanceof JsonArray array)) {
            return List.of();
        }
        List<T> result = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            try {
                result.add(element.decode(array.get(i)));
            } catch (CodecException e) {
                throw e.at(path + "[" + i + "]");
            }
        }
        return List.copyOf(result);
    }

    @Override
    public <T> @Nullable T get(String path, Codec<T> codec) {
        JsonValue value = getValue(path);
        if (value == null) {
            return null;
        }
        try {
            return codec.decode(value);
        } catch (CodecException e) {
            throw e.at(path);
        }
    }

    @Override
    public <T> T get(String path, Codec<T> codec, T defaultValue) {
        T value = get(path, codec);
        return value == null ? defaultValue : value;
    }

    @Override
    public <T> void set(String path, T value, Codec<T> codec) {
        set(path, codec.encode(value));
    }
}
