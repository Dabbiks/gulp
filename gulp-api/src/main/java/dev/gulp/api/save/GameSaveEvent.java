package dev.gulp.api.save;

import dev.gulp.api.data.DataContainer;
import dev.gulp.api.event.Event;

/**
 * The game is being saved: put your own data into the slot's container. Called on the main thread before writing.
 *
 * <pre>{@code
 * on(GameSaveEvent.class, e -> e.data().set(key("coins"), DataType.INT, coins.get()));
 * }</pre>
 */
public final class GameSaveEvent extends Event {

    private final SaveSlot slot;
    private final DataContainer data;

    /**
     * Creates the event.
     *
     * @param slot the slot
     * @param data the game data of the slot
     */
    public GameSaveEvent(SaveSlot slot, DataContainer data) {
        this.slot = slot;
        this.data = data;
    }

    /**
     * Returns the slot.
     *
     * @return the slot being written
     */
    public SaveSlot slot() {
        return slot;
    }

    /**
     * Returns the game data of the slot.
     *
     * @return the container, written with the slot
     */
    public DataContainer data() {
        return data;
    }
}
