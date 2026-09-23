package dev.gulp.api.event.lifecycle;

import dev.gulp.api.event.Event;

/**
 * Fired when the window gains focus or the browser tab becomes visible.
 *
 * <pre>{@code
 * on(FocusGainedEvent.class, e -> engine().resume());
 * }</pre>
 */
public final class FocusGainedEvent extends Event {

    /** Creates the event; fired by the engine. */
    public FocusGainedEvent() {}
}
