package dev.gulp.api.event.lifecycle;

import dev.gulp.api.event.Event;

/**
 * Fired at the end of every game tick, after removed entities are gone. Created only when someone listens.
 *
 * <pre>{@code
 * on(TickEndEvent.class, e -> checkWinCondition());
 * }</pre>
 */
public final class TickEndEvent extends Event {

    static {
        dev.gulp.api.spi.EventAccess.install(TickHooks.INSTANCE);
    }

    /** Set again for every tick: the engine reuses one instance, so keep the number, not the event. */
    long tick;

    /**
     * Creates the event; fired by the engine.
     *
     * @param tick the tick number
     */
    public TickEndEvent(long tick) {
        this.tick = tick;
    }

    /**
     * Returns the tick number.
     *
     * @return the value of {@code Engine.tick()} for this tick
     */
    public long tick() {
        return tick;
    }
}
