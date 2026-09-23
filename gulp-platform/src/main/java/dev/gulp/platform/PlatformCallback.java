package dev.gulp.platform;

import org.jspecify.annotations.Nullable;

/**
 * Result of an asynchronous platform operation. The backend calls exactly one of the methods, exactly once, on the main
 * thread between frames.
 *
 * <pre>{@code
 * files.readAsset("coins/textures/player.png", new PlatformCallback<>() {
 *     public void success(ByteBuffer bytes) { ... }
 *     public void failure(Throwable error) { ... }
 * });
 * }</pre>
 *
 * @param <T> type of the result; {@code Void} operations pass {@code null}
 */
public interface PlatformCallback<T extends @Nullable Object> {

    /**
     * Called when the operation succeeds.
     *
     * @param result the result
     */
    void success(T result);

    /**
     * Called when the operation fails.
     *
     * @param error what went wrong
     */
    void failure(Throwable error);
}
