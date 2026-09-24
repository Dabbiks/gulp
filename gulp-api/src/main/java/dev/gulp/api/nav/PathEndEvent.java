package dev.gulp.api.nav;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityEvent;

/**
 * A {@link PathFollower} reached an end of its path: the last point, or either end when looping or ping-ponging.
 *
 * <pre>{@code
 * platform.on(PathEndEvent.class, e -> playSound(CLUNK));
 * }</pre>
 */
public final class PathEndEvent extends EntityEvent {

    private final Path path;
    private final boolean finished;

    /**
     * Creates the event.
     *
     * @param entity the follower's entity
     * @param path the path
     * @param finished whether the follower stopped, as opposed to looping on
     */
    public PathEndEvent(Entity entity, Path path, boolean finished) {
        super(entity);
        this.path = path;
        this.finished = finished;
    }

    /**
     * Returns the path.
     *
     * @return the path
     */
    public Path path() {
        return path;
    }

    /**
     * Returns whether the follower stopped at this end.
     *
     * @return {@code true} unless looping or ping-ponging
     */
    public boolean isFinished() {
        return finished;
    }
}
