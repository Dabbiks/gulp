package dev.gulp.api.physics;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.event.Cancellable;
import dev.gulp.api.event.Event;
import dev.gulp.api.math.Vec2;
import org.jspecify.annotations.Nullable;

/**
 * Two rigid bodies (or a body and a tile) are about to be pushed apart in this tick. Cancelling it lets them pass
 * through each other for this tick, for example to make a one-way platform from a body.
 *
 * <pre>{@code
 * on(PreCollideEvent.class, e -> {
 *     if (e.a().tags().has("ghost") || (e.b() != null && e.b().tags().has("ghost"))) { e.setCancelled(true); }
 * });
 * }</pre>
 */
public final class PreCollideEvent extends Event implements Cancellable {

    private final Entity a;
    private final @Nullable Entity b;
    private final Vec2 normal;
    private boolean cancelled;

    /**
     * Creates the event.
     *
     * @param a the first entity
     * @param b the second entity, or {@code null} for tiles
     * @param normal unit normal from {@code b} towards {@code a}
     */
    public PreCollideEvent(Entity a, @Nullable Entity b, Vec2 normal) {
        this.a = a;
        this.b = b;
        this.normal = normal;
    }

    /**
     * Returns the first entity.
     *
     * @return the entity
     */
    public Entity a() {
        return a;
    }

    /**
     * Returns the second entity.
     *
     * @return the entity, or {@code null} for tiles
     */
    public @Nullable Entity b() {
        return b;
    }

    /**
     * Returns the contact normal.
     *
     * @return unit normal from {@code b} towards {@code a}
     */
    public Vec2 normal() {
        return normal;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }
}
