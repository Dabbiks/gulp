package dev.gulp.api.event.lifecycle;

import dev.gulp.api.event.Event;
import dev.gulp.api.spi.EventAccess;

/** Refills the reused tick events for the engine. */
final class TickHooks implements EventAccess.Hooks {

    static final TickHooks INSTANCE = new TickHooks();

    private TickHooks() {}

    @Override
    public void setTick(Event event, long tick) {
        if (event instanceof TickStartEvent start) {
            start.tick = tick;
        } else if (event instanceof TickEndEvent end) {
            end.tick = tick;
        } else {
            throw new IllegalArgumentException("Not a tick event: " + event.eventName());
        }
    }
}
