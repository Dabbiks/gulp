package dev.gulp.api.event;

/**
 * Order in which handlers run: from {@link #LOWEST} first to {@link #MONITOR} last. Handlers of the same priority run
 * in registration order.
 *
 * <pre>{@code
 * on(EntityDamageEvent.class, EventPriority.LOWEST, e -> e.setDamage(e.damage() * armorFactor));
 * }</pre>
 */
public enum EventPriority {
    /** Runs first; use to set up defaults others may override. */
    LOWEST,
    /** Runs early. */
    LOW,
    /** The default. */
    NORMAL,
    /** Runs late. */
    HIGH,
    /** Runs last among handlers that may change the event; has the final say on cancellation. */
    HIGHEST,
    /**
     * Runs after all others and must only observe the outcome. A change of the cancellation state made here is undone
     * and logged.
     */
    MONITOR
}
