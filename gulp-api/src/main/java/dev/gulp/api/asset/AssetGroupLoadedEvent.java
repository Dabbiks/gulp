package dev.gulp.api.asset;

import dev.gulp.api.event.Event;

/**
 * Fired after every asset of a group loaded.
 *
 * <pre>{@code
 * on(AssetGroupLoadedEvent.class, e -> {
 *     if (e.group().equals("level1")) startLevel();
 * });
 * }</pre>
 */
public final class AssetGroupLoadedEvent extends Event {

    private final String group;

    /**
     * Creates the event; fired by the engine.
     *
     * @param group the group name
     */
    public AssetGroupLoadedEvent(String group) {
        this.group = group;
    }

    /**
     * Returns the group name.
     *
     * @return the name
     */
    public String group() {
        return group;
    }
}
