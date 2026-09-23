package dev.gulp.api.asset;

import dev.gulp.api.event.Event;

/**
 * Fired when an asset could not be loaded; the error is also logged.
 *
 * <pre>{@code
 * on(AssetLoadFailedEvent.class, e -> showError("Missing " + e.key()));
 * }</pre>
 */
public final class AssetLoadFailedEvent extends Event {

    private final AssetKey<?> key;
    private final Throwable error;

    /**
     * Creates the event; fired by the engine.
     *
     * @param key the asset
     * @param error what went wrong
     */
    public AssetLoadFailedEvent(AssetKey<?> key, Throwable error) {
        this.key = key;
        this.error = error;
    }

    /**
     * Returns the asset.
     *
     * @return the key
     */
    public AssetKey<?> key() {
        return key;
    }

    /**
     * Returns what went wrong.
     *
     * @return the error
     */
    public Throwable error() {
        return error;
    }
}
