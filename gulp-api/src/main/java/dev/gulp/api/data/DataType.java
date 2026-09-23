package dev.gulp.api.data;

import dev.gulp.api.registry.Key;
import dev.gulp.api.spi.ApiSupport;
import java.util.List;
import java.util.Map;

/**
 * Type of a value stored in a {@link DataContainer}: a name and a codec.
 *
 * <pre>{@code
 * entity.data().set(key("kills"), DataType.INT, 3);
 * data().set(key("visited"), DataType.LIST(DataType.KEY), List.of(level1, level2));
 * data().set(key("upgrade"), DataType.of("upgrade", Codec.of(Upgrade.class)), upgrade);
 * }</pre>
 *
 * @param <T> the stored type
 */
public final class DataType<T> {

    /** A boolean. */
    public static final DataType<Boolean> BOOLEAN = new DataType<>("boolean", Codec.BOOLEAN);
    /** An int. */
    public static final DataType<Integer> INT = new DataType<>("int", Codec.INT);
    /** A long. */
    public static final DataType<Long> LONG = new DataType<>("long", Codec.LONG);
    /** A float. */
    public static final DataType<Float> FLOAT = new DataType<>("float", Codec.FLOAT);
    /** A double. */
    public static final DataType<Double> DOUBLE = new DataType<>("double", Codec.DOUBLE);
    /** A string. */
    public static final DataType<String> STRING = new DataType<>("string", Codec.STRING);
    /** A key. */
    public static final DataType<Key> KEY = new DataType<>("key", Codec.KEY);
    /** A nested container. */
    public static final DataType<DataContainer> CONTAINER = new DataType<>(
            "container",
            Codec.of(DataContainer::toJson, json -> ApiSupport.get().newDataContainer(json.asObject())));

    private final String name;
    private final Codec<T> codec;

    private DataType(String name, Codec<T> codec) {
        this.name = name;
        this.codec = codec;
    }

    /**
     * Creates a data type from a codec.
     *
     * @param <T> the stored type
     * @param name a descriptive name for error messages
     * @param codec the codec
     * @return the data type
     */
    public static <T> DataType<T> of(String name, Codec<T> codec) {
        return new DataType<>(name, codec);
    }

    /**
     * Returns a list type.
     *
     * @param <T> the element type
     * @param element the element type
     * @return the list type
     */
    public static <T> DataType<List<T>> LIST(DataType<T> element) {
        return new DataType<>("list<" + element.name + ">", Codec.listOf(element.codec));
    }

    /**
     * Returns a map type with string keys.
     *
     * @param <T> the value type
     * @param value the value type
     * @return the map type
     */
    public static <T> DataType<Map<String, T>> MAP(DataType<T> value) {
        return new DataType<>("map<" + value.name + ">", Codec.mapOf(value.codec));
    }

    /**
     * Returns the name.
     *
     * @return for example {@code "int"} or {@code "list<key>"}
     */
    public String name() {
        return name;
    }

    /**
     * Returns the codec.
     *
     * @return the codec
     */
    public Codec<T> codec() {
        return codec;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    @Override
    public String toString() {
        return name;
    }
}
