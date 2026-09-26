package dev.gulp.api.save;

import dev.gulp.api.scheduler.Promise;

/**
 * One save slot. Saving captures the game state at once (on the main thread) and writes it in the background; loading
 * replaces the persistent worlds and entities, the module data and the game data, then fires {@link GameLoadEvent}.
 *
 * <pre>{@code
 * SaveSlot slot = saves().slot("slot1");
 * slot.save().thenSync(done -> ui().toast("Saved"));
 * slot.exists().thenSync(exists -> continueButton.enabled(exists));
 * slot.load().onFailure(error -> ui().toast("Cannot load: " + error.getMessage(), "danger", 4f));
 * slot.exportFile();   // a download on the web, a file in the exports folder on desktop
 * }</pre>
 */
public interface SaveSlot {

    /**
     * Returns the name.
     *
     * @return the slot name
     */
    String name();

    /**
     * Saves the game into this slot, replacing what it held.
     *
     * @return completes when the files are written
     */
    Promise<Void> save();

    /**
     * Saves with a title shown in slot lists.
     *
     * @param title for example the level name
     * @return completes when the files are written
     */
    Promise<Void> save(String title);

    /**
     * Loads the game from this slot.
     *
     * @return completes after {@link GameLoadEvent}; fails if the slot does not exist or is damaged
     */
    Promise<Void> load();

    /**
     * Deletes the slot.
     *
     * @return completes when the files are gone
     */
    Promise<Void> delete();

    /**
     * Returns whether the slot has a save.
     *
     * @return {@code true} if it does
     */
    Promise<Boolean> exists();

    /**
     * Reads the metadata without loading the game.
     *
     * @return title, date, play time, version and thumbnail
     */
    Promise<SaveMetadata> metadata();

    /**
     * Packs the slot into one file's bytes.
     *
     * @return the bytes, readable by {@link #importData}
     */
    Promise<byte[]> exportData();

    /**
     * Replaces the slot with exported bytes.
     *
     * @param data bytes from {@link #exportData}
     * @return completes when written; fails if the bytes are not a Gulp save
     */
    Promise<Void> importData(byte[] data);

    /**
     * Offers the slot as a file: a download on the web, {@code exports/<slot>.gulpsave} in the user data folder on
     * desktop.
     *
     * @return completes when the file is offered or written
     */
    Promise<Void> exportFile();

    /**
     * Replaces the slot with a file chosen by the player: an upload dialog on the web, {@code
     * imports/<slot>.gulpsave} in the user data folder on desktop.
     *
     * @return completes when imported; fails if cancelled or not a Gulp save
     */
    Promise<Void> importFile();
}
