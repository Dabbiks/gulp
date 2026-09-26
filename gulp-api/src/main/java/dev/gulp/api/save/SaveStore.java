package dev.gulp.api.save;

import dev.gulp.api.data.DataContainer;
import dev.gulp.api.scheduler.Promise;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Save games ({@code saves()}). A slot holds everything persistent at the moment of saving:
 * <ul>
 *   <li>worlds created or loaded with {@code WorldSettings.persistent(true)}, their data and the chunks changed since
 *       the map was loaded or generated (in region files of 32 × 32 chunks), including tile states;
 *   <li>their persistent entities (type, position, rotation, scale, name, tags, data) with the {@code @Save} fields of
 *       components marked {@code @ComponentInfo(persistent = true)};
 *   <li>the {@code data()} of every module and anything the game adds in {@link GameSaveEvent}.
 * </ul>
 * Files are a tagged binary format, compressed, written atomically (a temporary file and a rename on desktop, an
 * IndexedDB transaction on the web). A slot saved by an older {@code GameSettings.saveVersion} goes through the
 * registered {@link #migration} steps in order when loaded.
 *
 * <pre>{@code
 * saves().migration(1, data -> data.set(key("gold"), DataType.INT, data.getOrDefault(key("coins"), DataType.INT, 0)));
 * saves().autosave("auto", Duration.ofMinutes(5));
 * saves().slot("slot1").save().thenSync(done -> ui().toast("Saved"));
 * saves().list().thenSync(slots -> slots.forEach(meta -> logger().info(meta.slot() + " " + meta.savedAt())));
 * }</pre>
 */
public interface SaveStore {

    /**
     * Returns a slot by name; the slot need not exist yet.
     *
     * @param name the slot name, {@code [a-z0-9_-]+}
     * @return the slot
     * @throws IllegalArgumentException if the name has other characters
     */
    SaveSlot slot(String name);

    /**
     * Lists the saved slots.
     *
     * @return the metadata of every slot, newest first
     */
    Promise<List<SaveMetadata>> list();

    /**
     * Registers a step that upgrades save data written by one version to the next.
     *
     * @param fromVersion the version the step reads; it produces {@code fromVersion + 1}
     * @param step changes the game data of the slot (the container given to {@link GameLoadEvent})
     */
    void migration(int fromVersion, Consumer<DataContainer> step);

    /**
     * Returns the save version written now.
     *
     * @return {@code GameSettings.saveVersion}, 1 by default
     */
    int version();

    /**
     * Saves to a slot every interval of real time, and on the web whenever the tab is hidden.
     *
     * @param slot the slot name
     * @param interval how often
     */
    void autosave(String slot, Duration interval);

    /** Stops autosaving. */
    void stopAutosave();

    /**
     * Returns the autosave slot.
     *
     * @return the slot name, or {@code null} when autosave is off
     */
    @Nullable String autosaveSlot();

    /**
     * Returns whether a save or load is running.
     *
     * @return {@code true} until its promise completes
     */
    boolean isBusy();
}
