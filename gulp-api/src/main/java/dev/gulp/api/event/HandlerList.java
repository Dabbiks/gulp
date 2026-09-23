package dev.gulp.api.event;

/**
 * Read-only view of the handlers that receive one event class, including handlers registered for its base classes.
 *
 * <pre>{@code
 * HandlerList handlers = events().handlers(EntityDamageEvent.class);
 * logger().debug(handlers.size() + " damage handlers");
 * }</pre>
 */
public interface HandlerList {

    /**
     * Returns the number of handlers that would run for an event of this class (not counting handlers bound to a
     * specific target).
     *
     * @return the handler count
     */
    int size();

    /**
     * Returns whether no handler would run.
     *
     * @return {@code true} if {@link #size()} is zero
     */
    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns the number of handlers with a given priority.
     *
     * @param priority the priority
     * @return the handler count
     */
    int size(EventPriority priority);
}
