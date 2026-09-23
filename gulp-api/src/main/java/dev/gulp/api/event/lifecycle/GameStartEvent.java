package dev.gulp.api.event.lifecycle;

import dev.gulp.api.event.Event;

/**
 * Fired once after {@code Game.onStart()} and after the default modules were enabled.
 *
 * <pre>{@code
 * on(GameStartEvent.class, e -> logger().info("Game started"));
 * }</pre>
 */
public final class GameStartEvent extends Event {

    /** Creates the event; fired by the engine. */
    public GameStartEvent() {}
}
