package dev.gulp.api.event;

/**
 * An event about one object (an entity, a widget). Handlers can subscribe to the events of a single target only, and
 * those subscriptions end when the target is released — the equivalent of signals in node-based engines.
 *
 * <pre>{@code
 * // in the entity implementation (stage 6)
 * public <E extends Event> Subscription on(Class<E> type, Consumer<? super E> handler) {
 *     return events().on(type, this, EventPriority.NORMAL, handler, owner);
 * }
 * }</pre>
 */
public interface TargetedEvent {

    /**
     * Returns the object this event is about.
     *
     * @return the target
     */
    Object target();
}
