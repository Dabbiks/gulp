package dev.gulp.api.event.lifecycle;

import dev.gulp.api.event.Event;

/**
 * Fired when game time is paused with {@code Engine.pause()}.
 *
 * <pre>{@code
 * on(PauseEvent.class, e -> music.duck());
 * }</pre>
 */
public final class PauseEvent extends Event {

    /** Creates the event; fired by the engine. */
    public PauseEvent() {}
}
