package dev.gulp.api.event;

import dev.gulp.api.Owner;
import java.util.function.Consumer;

/**
 * The event bus. Handlers run synchronously on the main thread, ordered by {@link EventPriority}.
 *
 * <pre>{@code
 * Subscription sub = events().on(ScoreChangedEvent.class, EventPriority.NORMAL, false, e -> hud.update(e.score()), this);
 *
 * if (events().hasListeners(ScoreChangedEvent.class)) {
 *     events().call(new ScoreChangedEvent(score));
 * }
 * }</pre>
 *
 * <p>Rules: an exception in a handler is logged with the owner's name and the remaining handlers still run;
 * registrations changed during a call take effect from the next call; calling an event from inside a handler is
 * allowed up to a depth of 64.
 */
public interface Events {

    /**
     * Delivers an event to its handlers.
     *
     * @param <E> the event type
     * @param event the event
     * @return the same event, to check {@link Cancellable#isCancelled()} or read results
     * @throws IllegalStateException if events are nested deeper than 64 calls
     */
    <E extends Event> E call(E event);

    /**
     * Returns whether any handler would receive an event of this class. Use it to skip creating events nobody listens
     * to.
     *
     * @param type the event class
     * @return {@code true} if at least one handler is registered for it or a base class
     */
    boolean hasListeners(Class<? extends Event> type);

    /**
     * Returns the handlers that receive an event class.
     *
     * @param type the event class
     * @return a live view
     */
    HandlerList handlers(Class<? extends Event> type);

    /**
     * Registers every {@link EventHandler} method of a listener.
     *
     * @param listener the listener; its class must have been processed by {@code gulp-processor}
     * @param owner the owner; the subscription ends when it is disabled
     * @return one subscription for all handlers of the listener
     * @throws IllegalStateException if no generated dispatch code exists for the listener class, or the owner is not
     *     enabled
     */
    Subscription register(Listener listener, Owner owner);

    /**
     * Registers a handler for an event class.
     *
     * @param <E> the event type
     * @param type the event class; subclasses are delivered too
     * @param priority when the handler runs
     * @param ignoreCancelled whether to skip events already cancelled
     * @param handler the handler
     * @param owner the owner; the subscription ends when it is disabled
     * @return the subscription
     */
    <E extends Event> Subscription on(
            Class<E> type, EventPriority priority, boolean ignoreCancelled, Consumer<? super E> handler, Owner owner);

    /**
     * Registers a handler for events of one target only. The event class must implement {@link TargetedEvent}.
     *
     * @param <E> the event type
     * @param type the event class; subclasses are delivered too
     * @param target the target whose events are delivered
     * @param priority when the handler runs
     * @param handler the handler
     * @param owner the owner; the subscription ends when it is disabled or the target is released
     * @return the subscription
     */
    <E extends Event> Subscription on(
            Class<E> type, Object target, EventPriority priority, Consumer<? super E> handler, Owner owner);

    /**
     * Ends all subscriptions bound to a target, for example when an entity is removed.
     *
     * @param target the target
     */
    void release(Object target);
}
