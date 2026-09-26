package dev.gulp.api.save;

import dev.gulp.api.data.DataContainer;
import dev.gulp.api.event.Event;

/**
 * A save was loaded: worlds, entities and module data are in place; read your own data from the slot's container.
 *
 * <pre>{@code
 * on(GameLoadEvent.class, e -> coins.set(e.data().getOrDefault(key("coins"), DataType.INT, 0)));
 * }</pre>
 */
public final class GameLoadEvent extends Event {

    private final SaveSlot slot;
    private final DataContainer data;
    private final int version;

    /**
     * Creates the event.
     *
     * @param slot the slot
     * @param data the game data of the slot, after migrations
     * @param version the version the slot was written with
     */
    public GameLoadEvent(SaveSlot slot, DataContainer data, int version) {
        this.slot = slot;
        this.data = data;
        this.version = version;
    }

    /**
     * Returns the slot.
     *
     * @return the slot read
     */
    public SaveSlot slot() {
        return slot;
    }

    /**
     * Returns the game data of the slot.
     *
     * @return the container
     */
    public DataContainer data() {
        return data;
    }

    /**
     * Returns the version the slot was written with, before migrations.
     *
     * @return the save version
     */
    public int savedVersion() {
        return version;
    }
}
