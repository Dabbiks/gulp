package dev.gulp.api.event;

/**
 * An event whose default outcome handlers can prevent.
 *
 * <pre>{@code
 * on(EntityDamageEvent.class, e -> { if (godMode) e.setCancelled(true); });
 *
 * if (!events().call(new EntityDamageEvent(entity, 5)).isCancelled()) applyDamage();
 * }</pre>
 */
public interface Cancellable {

    /**
     * Returns whether a handler cancelled the event.
     *
     * @return {@code true} if cancelled
     */
    boolean isCancelled();

    /**
     * Cancels or un-cancels the event.
     *
     * @param cancelled the new state
     */
    void setCancelled(boolean cancelled);
}
