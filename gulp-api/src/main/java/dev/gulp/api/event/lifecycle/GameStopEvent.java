package dev.gulp.api.event.lifecycle;

import dev.gulp.api.event.Event;

/**
 * Fired once when the game stops, before modules are disabled.
 *
 * <pre>{@code
 * on(GameStopEvent.class, e -> saveProgress());
 * }</pre>
 */
public final class GameStopEvent extends Event {

    /** Creates the event; fired by the engine. */
    public GameStopEvent() {}
}
