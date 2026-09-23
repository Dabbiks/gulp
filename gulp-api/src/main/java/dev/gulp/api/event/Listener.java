package dev.gulp.api.event;

/**
 * Marker for classes with {@link EventHandler} methods. {@code gulp-processor} generates direct dispatch code for each
 * listener class at compile time, so handler methods must not be {@code private} and the listener class must not be
 * anonymous or private.
 *
 * <pre>{@code
 * final class DamageListener implements Listener {
 *     @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
 *     void onDamage(EntityDamageEvent event) { ... }
 * }
 *
 * listen(new DamageListener());
 * }</pre>
 */
public interface Listener {}
