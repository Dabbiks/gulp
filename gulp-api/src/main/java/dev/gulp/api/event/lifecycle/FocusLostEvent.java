package dev.gulp.api.event.lifecycle;

import dev.gulp.api.event.Event;

/**
 * Fired when the window loses focus or the browser tab is hidden. On the web, save state here: closing the tab does not guarantee {@code onStop}.
 *
 * <pre>{@code
 * on(FocusLostEvent.class, e -> engine().pause());
 * }</pre>
 */
public final class FocusLostEvent extends Event {

    /** Creates the event; fired by the engine. */
    public FocusLostEvent() {}
}
