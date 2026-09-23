package dev.gulp.api.event;

/**
 * A registered handler or listener. Subscriptions end automatically when their owner is disabled.
 *
 * <pre>{@code
 * Subscription sub = on(TickEndEvent.class, e -> tutorial.update());
 * ...
 * sub.cancel();
 * }</pre>
 */
public interface Subscription {

    /** Removes the handlers from the next event call onwards. Safe to call more than once. */
    void cancel();

    /**
     * Returns whether the handlers still receive events.
     *
     * @return {@code false} after {@link #cancel()} or after the owner was disabled
     */
    boolean isActive();
}
