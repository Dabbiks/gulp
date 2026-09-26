package dev.gulp.api.spi;

import dev.gulp.api.event.Event;
import org.jspecify.annotations.Nullable;

/**
 * Lets the engine reuse the events it fires every tick (section 20.5, pools of frequent events): one
 * {@link dev.gulp.api.event.lifecycle.TickStartEvent} and one {@link dev.gulp.api.event.lifecycle.TickEndEvent} are
 * refilled instead of created. Not for game code.
 *
 * <pre>{@code
 * EventAccess.setTick(tickEnd, tick);
 * events.call(tickEnd);
 * }</pre>
 */
public final class EventAccess {

    /** Implemented inside the tick events. */
    public interface Hooks {
        /**
         * Sets the tick number of a tick event.
         *
         * @param event a tick start or end event
         * @param tick the tick number
         */
        void setTick(Event event, long tick);
    }

    private static @Nullable Hooks hooks;

    private EventAccess() {}

    /**
     * Installs the hooks; called by the tick events.
     *
     * @param installed the hooks
     */
    public static void install(Hooks installed) {
        if (hooks == null) {
            hooks = installed;
        }
    }

    /**
     * Sets the tick number of a reused tick event.
     *
     * @param event the event; creating one installed the hooks
     * @param tick the tick number
     */
    public static void setTick(Event event, long tick) {
        Hooks current = hooks;
        if (current == null) {
            throw new IllegalStateException("Event hooks are not installed");
        }
        current.setTick(event, tick);
    }
}
