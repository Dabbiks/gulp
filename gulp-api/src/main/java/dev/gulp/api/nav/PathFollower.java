package dev.gulp.api.nav;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.Vec2;

/**
 * Moves its entity along a {@link Path} at a constant speed: once, in a loop or back and forth, optionally turning to
 * face the direction of travel. Good for moving platforms, patrols and rails. Fires {@link PathEndEvent} at the ends.
 *
 * <pre>{@code
 * EntityType.builder(key("lift"))
 *         .component(() -> new PathFollower(Path.of(new Vec2(10, 8), new Vec2(10, 2))).speed(2).pingPong(true))
 *         .component(() -> new Collider())
 *         .build();
 * }</pre>
 */
public final class PathFollower extends Component {

    private Path path;
    private float speed = 2f;
    private boolean loop;
    private boolean pingPong;
    private boolean rotate;
    private float distance;
    private int direction = 1;
    private boolean running = true;

    /**
     * Creates a follower.
     *
     * @param path the path
     */
    public PathFollower(Path path) {
        this.path = path;
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
     * Switches to another path from its start.
     *
     * @param value the path
     * @return this follower
     */
    public PathFollower path(Path value) {
        path = value;
        distance = 0f;
        direction = 1;
        running = true;
        return this;
    }

    /**
     * Sets the speed.
     *
     * @param value units per second, {@code 2} by default
     * @return this follower
     */
    public PathFollower speed(float value) {
        speed = value;
        return this;
    }

    /**
     * Returns the speed.
     *
     * @return units per second
     */
    public float speed() {
        return speed;
    }

    /**
     * Starts again from the beginning at the end.
     *
     * @param value whether to loop
     * @return this follower
     */
    public PathFollower loop(boolean value) {
        loop = value;
        return this;
    }

    /**
     * Turns around at each end.
     *
     * @param value whether to go back and forth
     * @return this follower
     */
    public PathFollower pingPong(boolean value) {
        pingPong = value;
        return this;
    }

    /**
     * Turns the entity to face the direction of travel.
     *
     * @param value whether to rotate
     * @return this follower
     */
    public PathFollower rotate(boolean value) {
        rotate = value;
        return this;
    }

    /**
     * Returns the distance travelled along the path.
     *
     * @return world units from the start
     */
    public float distance() {
        return distance;
    }

    /**
     * Jumps to a distance along the path.
     *
     * @param value world units from the start
     * @return this follower
     */
    public PathFollower setDistance(float value) {
        distance = Math.max(0f, Math.min(path.length(), value));
        running = true;
        return this;
    }

    /**
     * Returns whether the follower still moves.
     *
     * @return {@code false} after reaching the end without looping
     */
    public boolean isRunning() {
        return running && !path.isEmpty();
    }

    @Override
    protected void onSpawn() {
        place();
    }

    @Override
    protected void onTick() {
        if (!isRunning()) {
            return;
        }
        float length = path.length();
        distance += direction * speed / engine().targetTps();
        Entity self = entity();
        if (distance >= length || distance <= 0f) {
            boolean atEnd = distance >= length;
            if (pingPong) {
                distance = atEnd ? 2 * length - distance : -distance;
                distance = Math.max(0f, Math.min(length, distance));
                direction = -direction;
                engine().events().call(new PathEndEvent(self, path, false));
            } else if (loop && atEnd) {
                distance = length <= 0f ? 0f : distance - length;
                engine().events().call(new PathEndEvent(self, path, false));
            } else {
                distance = atEnd ? length : 0f;
                running = false;
                engine().events().call(new PathEndEvent(self, path, true));
            }
        }
        place();
    }

    private void place() {
        if (path.isEmpty()) {
            return;
        }
        Entity self = entity();
        Vec2 at = path.pointAt(distance);
        self.setPosition(at);
        if (rotate) {
            Vec2 heading = path.directionAt(distance);
            if (!heading.equals(Vec2.ZERO)) {
                self.setRotation(direction > 0 ? heading.angle() : heading.angle() + 180f);
            }
        }
    }
}
