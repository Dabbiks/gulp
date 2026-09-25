package dev.gulp.api.ui;

import dev.gulp.api.event.Cancellable;
import dev.gulp.api.event.Event;

/**
 * A screen is about to open (by {@code open} or {@code push}); cancelling keeps it closed.
 *
 * <pre>{@code
 * on(ScreenOpenEvent.class, e -> {
 *     if (e.screen() instanceof ShopScreen && inCombat) e.setCancelled(true);
 * });
 * }</pre>
 */
public final class ScreenOpenEvent extends Event implements Cancellable {

    private final Screen screen;
    private boolean cancelled;

    /**
     * Creates the event.
     *
     * @param screen the screen
     */
    public ScreenOpenEvent(Screen screen) {
        this.screen = screen;
    }

    /**
     * Returns the screen.
     *
     * @return the screen that opens
     */
    public Screen screen() {
        return screen;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }
}
