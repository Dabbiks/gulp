package dev.gulp.api.data;

import dev.gulp.api.scheduler.Promise;
import org.jspecify.annotations.Nullable;

/**
 * Configuration of the game ({@code config/game.yml}) or a module ({@code config/<id>.yml}) in a YAML subset: maps,
 * lists, scalars, comments and multi-line strings. Defaults come from {@code assets/<game id>/config/<name>.yml}; values
 * in the user file override them. Both files are loaded before {@code onLoad}.
 *
 * <pre>{@code
 * # assets/coins/config/game.yml
 * difficulty: normal
 * player:
 *   speed: 7.0
 *
 * float speed = config().getFloat("player.speed", 5f);
 * config().set("difficulty", "hard");
 * config().save();
 * }</pre>
 *
 * <p>Saving writes the merged values back as plain YAML; comments in the user file are not preserved.
 */
public interface Config extends ConfigSection {

    /**
     * Returns the file name without extension.
     *
     * @return {@code "game"} or the module id
     */
    String name();

    /**
     * Writes the current values to the user file.
     *
     * @return completes when the data is durable
     */
    Promise<@Nullable Void> save();

    /**
     * Reads both files again and fires {@link ConfigReloadEvent} when done.
     *
     * @return completes after the event was fired
     */
    Promise<@Nullable Void> reload();
}
