package dev.gulp.api.nav;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityEvent;
import dev.gulp.api.math.Vec2;

/**
 * A {@link NavAgent} found no path to its target and stopped.
 *
 * <pre>{@code
 * on(NavPathFailedEvent.class, e -> e.entity().get(NavAgent.class).moveTo(home));
 * }</pre>
 */
public final class NavPathFailedEvent extends EntityEvent {

    private final Vec2 destination;

    /**
     * Creates the event.
     *
     * @param entity the agent's entity
     * @param destination the unreachable target
     */
    public NavPathFailedEvent(Entity entity, Vec2 destination) {
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
