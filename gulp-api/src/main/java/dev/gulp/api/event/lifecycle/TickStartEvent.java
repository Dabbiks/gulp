package dev.gulp.api.event.lifecycle;

import dev.gulp.api.event.Event;

/**
 * Fired at the start of every game tick, after input is collected and before scheduled tasks run. Created only when someone listens.
 *
 * <pre>{@code
 * on(TickStartEvent.class, e -> ai.plan(e.tick()));
 * }</pre>
 */
public final class TickStartEvent extends Event {

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
    public TickStartEvent(long tick) {
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
