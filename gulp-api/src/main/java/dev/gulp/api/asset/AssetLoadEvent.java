package dev.gulp.api.asset;

import dev.gulp.api.event.Event;

/**
 * Fired after an asset finished loading.
 *
 * <pre>{@code
 * on(AssetLoadEvent.class, e -> logger().debug("Loaded " + e.key()));
 * }</pre>
 */
public final class AssetLoadEvent extends Event {

    private final AssetKey<?> key;

    /**
     * Creates the event; fired by the engine.
     *
     * @param key the loaded asset
     */
    public AssetLoadEvent(AssetKey<?> key) {
        this.key = key;
    }

    /**
     * Returns the loaded asset.
     *
     * @return the key
     */
    public AssetKey<?> key() {
        return key;
    }
}
