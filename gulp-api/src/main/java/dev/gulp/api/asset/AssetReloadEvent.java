package dev.gulp.api.asset;

import dev.gulp.api.event.Event;

/**
 * Fired after a loaded asset was replaced in place because its file changed (hot reload in development, from stage 4)
 * or a resource pack changed it. Handles stay valid; the event lets the game refresh derived state.
 *
 * <pre>{@code
 * on(AssetReloadEvent.class, e -> logger().info("Reloaded " + e.key()));
 * }</pre>
 */
public final class AssetReloadEvent extends Event {

    private final AssetKey<?> key;

    /**
     * Creates the event; fired by the engine.
     *
     * @param key the reloaded asset
     */
    public AssetReloadEvent(AssetKey<?> key) {
        this.key = key;
    }

    /**
     * Returns the reloaded asset.
     *
     * @return the key
     */
    public AssetKey<?> key() {
        return key;
    }
}
