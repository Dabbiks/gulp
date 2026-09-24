package dev.gulp.core.data;

import dev.gulp.api.Logger;
import dev.gulp.api.data.JsonBoolean;
import dev.gulp.api.data.JsonNumber;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonString;
import dev.gulp.api.data.JsonValue;
import dev.gulp.api.data.Preferences;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformFiles;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Preferences kept in {@code preferences.json} in the user data folder (IndexedDB on the web). Loaded with the configs
 * before {@code onLoad}; changes are written half a second after the last one, and at exit.
 */
public final class PreferencesImpl implements Preferences {

    /** User data file name. */
    public static final String FILE = "preferences.json";

    private static final long SAVE_DELAY_NANOS = 500_000_000L;

    private final PlatformFiles files;
    private final Logger logger;
    private final Map<String, JsonValue> values = new LinkedHashMap<>();
    private boolean dirty;
    private long dirtySince;
    private long now;
    private boolean writing;

    /**
     * Creates empty preferences.
     *
     * @param files user data storage
     * @param logger receives read and write problems
     */
    public PreferencesImpl(PlatformFiles files, Logger logger) {
        this.files = files;
        this.logger = logger;
    }

    /**
     * Reads the stored preferences; a missing or broken file leaves them empty.
     *
     * @param done called on the main thread when finished
     */
    public void load(Runnable done) {
        files.readUserData(FILE, new PlatformCallback<>() {
            @Override
            public void success(ByteBuffer bytes) {
                byte[] data = new byte[bytes.remaining()];
                bytes.duplicate().get(data);
                try {
                    JsonValue parsed = JsonReader.parse(new String(data, StandardCharsets.UTF_8));
                    if (parsed instanceof JsonObject object) {
                        values.putAll(object.members());
                    }
                } catch (RuntimeException error) {
                    logger.warn("Ignoring unreadable " + FILE + ": " + error.getMessage());
                }
                done.run();
            }

            @Override
            public void failure(Throwable error) {
                done.run();
            }
        });
    }

    /**
     * Writes pending changes once they are old enough. Called every frame.
     *
     * @param nanoTime the frame time
     */
    public void update(long nanoTime) {
        now = nanoTime;
        if (dirty && !writing && nanoTime - dirtySince >= SAVE_DELAY_NANOS) {
            write();
        }
    }

    /** Writes pending changes at exit. */
    public void flush() {
        if (dirty) {
            write();
        }
    }

    private void write() {
        dirty = false;
        writing = true;
        byte[] bytes = JsonWriter.write(new JsonObject(new LinkedHashMap<>(values)), true)
                .getBytes(StandardCharsets.UTF_8);
        files.writeUserData(FILE, ByteBuffer.wrap(bytes), new PlatformCallback<>() {
            @Override
            public void success(@Nullable Void result) {
                writing = false;
            }

            @Override
            public void failure(Throwable error) {
                writing = false;
                logger.warn("Could not save " + FILE + ": " + error.getMessage());
            }
        });
    }

    private void changed() {
        if (!dirty) {
            dirty = true;
            dirtySince = now;
        }
    }

    private static String checkKey(String key) {
        if (key.isBlank()) {
            throw new IllegalArgumentException("Preference key must not be blank");
        }
        return key;
    }

    /**
     * Returns whether changes wait to be written.
     *
     * @return {@code true} if unsaved
     */
    public boolean isDirty() {
        return dirty;
    }

    @Override
    public String getString(String key, String fallback) {
        JsonValue value = values.get(key);
        return value instanceof JsonString text ? text.value() : fallback;
    }

    @Override
    public int getInt(String key, int fallback) {
        JsonValue value = values.get(key);
        return value instanceof JsonNumber number ? number.intValue() : fallback;
    }

    @Override
    public float getFloat(String key, float fallback) {
        JsonValue value = values.get(key);
        return value instanceof JsonNumber number ? (float) number.doubleValue() : fallback;
    }

    @Override
    public boolean getBoolean(String key, boolean fallback) {
        JsonValue value = values.get(key);
        return value instanceof JsonBoolean flag ? flag.value() : fallback;
    }

    private Preferences put(String key, JsonValue value) {
        JsonValue old = values.put(checkKey(key), value);
        if (!value.equals(old)) {
            changed();
        }
        return this;
    }

    @Override
    public Preferences set(String key, String value) {
        return put(key, new JsonString(value));
    }

    @Override
    public Preferences set(String key, int value) {
        return put(key, JsonNumber.of(value));
    }

    @Override
    public Preferences set(String key, float value) {
        return put(key, JsonNumber.of(Double.parseDouble(Float.toString(value))));
    }

    @Override
    public Preferences set(String key, boolean value) {
        return put(key, JsonBoolean.of(value));
    }

    @Override
    public boolean has(String key) {
        return values.containsKey(key);
    }

    @Override
    public Preferences remove(String key) {
        if (values.remove(key) != null) {
            changed();
        }
        return this;
    }

    @Override
    public Set<String> keys() {
        return new LinkedHashSet<>(values.keySet());
    }

    @Override
    public void save() {
        if (dirty && !writing) {
            write();
        }
    }
}
