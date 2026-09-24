package dev.gulp.api.nav;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityEvent;
import dev.gulp.api.math.Vec2;

/**
 * A {@link NavAgent} arrived at its target.
 *
 * <pre>{@code
 * guard.on(NavTargetReachedEvent.class, e -> guard.get(NavAgent.class).moveTo(nextWaypoint()));
 * }</pre>
 */
public final class NavTargetReachedEvent extends EntityEvent {

    private final Vec2 destination;

    /**
     * Creates the event.
     *
     * @param entity the agent's entity
     * @param destination the target reached
     */
    public NavTargetReachedEvent(Entity entity, Vec2 destination) {
        super(entity);
        this.destination = destination;
    }

    /**
     * Returns the target point.
     *
     * @return world units
     */
    public Vec2 destination() {
        return destination;
    }
}
