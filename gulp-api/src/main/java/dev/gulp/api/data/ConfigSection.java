package dev.gulp.api.data;

import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A map inside a {@link Config}. Paths separate nested sections with dots: {@code "enemies.slime.hp"}.
 *
 * <pre>{@code
 * ConfigSection slime = config().getSection("enemies.slime");
 * int hp = slime.getInt("hp", 20);
 * List<String> drops = slime.getList("drops", Codec.STRING);
 * }</pre>
 */
public interface ConfigSection {

    /**
     * Returns the path of this section from the config root.
     *
     * @return the path, empty for the root
     */
    String path();

    /**
     * Returns the direct child names.
     *
     * @return the names in file order
     */
    Set<String> keys();

    /**
     * Returns whether a value exists at a path.
     *
     * @param path the dotted path
     * @return {@code true} if present
     */
    boolean contains(String path);

    /**
     * Returns the raw value at a path.
     *
     * @param path the dotted path
     * @return the value, or {@code null} if absent
     */
    @Nullable JsonValue getValue(String path);

    /**
     * Returns a string; numbers and booleans are converted to text.
     *
     * @param path the dotted path
     * @param defaultValue returned when absent or not a scalar
     * @return the value
     */
    String getString(String path, String defaultValue);

    /**
     * Returns an int.
     *
     * @param path the dotted path
     * @param defaultValue returned when absent or not a number
     * @return the value
     */
    int getInt(String path, int defaultValue);

    /**
     * Returns a long.
     *
     * @param path the dotted path
     * @param defaultValue returned when absent or not a number
     * @return the value
     */
    long getLong(String path, long defaultValue);

    /**
     * Returns a float.
     *
     * @param path the dotted path
     * @param defaultValue returned when absent or not a number
     * @return the value
     */
    float getFloat(String path, float defaultValue);

    /**
     * Returns a double.
     *
     * @param path the dotted path
     * @param defaultValue returned when absent or not a number
     * @return the value
     */
    double getDouble(String path, double defaultValue);

    /**
     * Returns a boolean.
     *
     * @param path the dotted path
     * @param defaultValue returned when absent or not a boolean
     * @return the value
     */
    boolean getBoolean(String path, boolean defaultValue);

    /**
     * Returns a list.
     *
     * @param <T> the element type
     * @param path the dotted path
     * @param element the element codec
     * @return the decoded list, or an empty list when absent
     * @throws CodecException if an element has the wrong shape
     */
    <T> List<T> getList(String path, Codec<T> element);

    /**
     * Returns a nested section.
     *
     * @param path the dotted path
     * @return the section, or {@code null} if absent or not a map
     */
    @Nullable ConfigSection getSection(String path);

    /**
     * Decodes the value at a path.
     *
     * @param <T> the value type
     * @param path the dotted path
     * @param codec the codec
     * @return the value, or {@code null} if absent
     * @throws CodecException if the value has the wrong shape
     */
    <T> @Nullable T get(String path, Codec<T> codec);

    /**
     * Decodes the value at a path, or returns a default.
     *
     * @param <T> the value type
     * @param path the dotted path
     * @param codec the codec
     * @param defaultValue returned when absent
     * @return the value or the default
     * @throws CodecException if the value has the wrong shape
     */
    <T> T get(String path, Codec<T> codec, T defaultValue);

    /**
     * Sets a value; intermediate sections are created. Accepts what {@link JsonValue#of(Object)} accepts; {@code null}
     * removes the value.
     *
     * @param path the dotted path
     * @param value the value
     */
    void set(String path, @Nullable Object value);

    /**
     * Sets a value through a codec.
     *
     * @param <T> the value type
     * @param path the dotted path
     * @param value the value
     * @param codec the codec
     */
    <T> void set(String path, T value, Codec<T> codec);
}
