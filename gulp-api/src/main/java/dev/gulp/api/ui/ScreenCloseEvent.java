package dev.gulp.api.ui;

import dev.gulp.api.event.Event;

/**
 * A screen closed (popped, replaced or closed directly).
 *
 * <pre>{@code
 * on(ScreenCloseEvent.class, e -> {
 *     if (e.screen() instanceof SettingsScreen) preferences().save();
 * });
 * }</pre>
 */
public final class ScreenCloseEvent extends Event {

    private final Screen screen;

    /**
     * Creates the event.
     *
     * @param screen the screen
     */
    public ScreenCloseEvent(Screen screen) {
        this.screen = screen;
    }

    /**
     * Returns the screen.
     *
     * @return the screen that closed
     */
    public Screen screen() {
        return screen;
    }
}
