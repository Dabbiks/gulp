package dev.gulp.api.data;

import java.util.Set;

/**
 * A small key-value store for settings, separate from save slots. Changes are written automatically shortly after
 * they are made and when the game stops. The engine keeps its own settings here too: bus volumes under {@code
 * audio.*} and input bindings under {@code input.*}.
 *
 * <pre>{@code
 * float sensitivity = preferences().getFloat("controls.sensitivity", 1f);
 * preferences().set("controls.sensitivity", 1.5f);
 * }</pre>
 */
public interface Preferences {

    /**
     * Returns a text value.
     *
     * @param key the key
     * @param fallback returned when the key is missing
     * @return the value
     */
    String getString(String key, String fallback);

    /**
     * Returns a whole number.
     *
     * @param key the key
     * @param fallback returned when the key is missing or not a number
     * @return the value
     */
    int getInt(String key, int fallback);

    /**
     * Returns a number.
     *
     * @param key the key
     * @param fallback returned when the key is missing or not a number
     * @return the value
     */
    float getFloat(String key, float fallback);

    /**
     * Returns a flag.
     *
     * @param key the key
     * @param fallback returned when the key is missing or not a flag
     * @return the value
     */
    boolean getBoolean(String key, boolean fallback);

    /**
     * Stores a text value.
     *
     * @param key the key
     * @param value the value
     * @return this store
     */
    Preferences set(String key, String value);

    /**
     * Stores a whole number.
     *
     * @param key the key
     * @param value the value
     * @return this store
     */
    Preferences set(String key, int value);

    /**
     * Stores a number.
     *
     * @param key the key
     * @param value the value
     * @return this store
     */
    Preferences set(String key, float value);

    /**
     * Stores a flag.
     *
     * @param key the key
     * @param value the value
     * @return this store
     */
    Preferences set(String key, boolean value);

    /**
     * Returns whether a key is stored.
     *
     * @param key the key
     * @return {@code true} if stored
     */
    boolean has(String key);

    /**
     * Removes a key.
     *
     * @param key the key
     * @return this store
     */
    Preferences remove(String key);

    /**
     * Returns the stored keys.
     *
     * @return the keys, a snapshot
     */
    Set<String> keys();

    /** Writes pending changes now instead of shortly. */
    void save();
}
