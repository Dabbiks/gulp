package dev.gulp.api.event;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method of a {@link Listener} as an event handler. The method takes exactly one parameter, the event type
 * (or a base class of events), and returns {@code void}.
 *
 * <pre>{@code
 * @EventHandler(priority = EventPriority.MONITOR)
 * void log(ModuleEnableEvent event) { logger.info("Enabled " + event.moduleId()); }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface EventHandler {

    /**
     * Returns when the handler runs relative to others.
     *
     * @return the priority, {@link EventPriority#NORMAL} by default
     */
    EventPriority priority() default EventPriority.NORMAL;

    /**
     * Returns whether the handler is skipped for events already cancelled by earlier handlers.
     *
     * @return {@code false} by default
     */
    boolean ignoreCancelled() default false;
}
