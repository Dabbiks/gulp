package dev.gulp.api.nav;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.physics.Mover;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Walks its entity to a target along a path from the world's {@link NavGrid}, searching again when the grid changes or,
 * when chasing an entity, every {@link #repathInterval(int)} ticks. With a {@link Mover} (usually {@code topDown}) it
 * moves through {@link Mover#moveAndSlide}, so it collides; without one it sets the position. Nearby agents keep apart
 * when {@link #avoidance(float)} is on.
 *
 * <pre>{@code
 * EntityType.builder(key("zombie"))
 *         .component(() -> new Mover().topDown(true))
 *         .component(() -> new NavAgent().speed(2.5f).avoidance(0.8f))
 *         .build();
 * zombie.get(NavAgent.class).follow(player);
 * }</pre>
 */
public final class NavAgent extends Component {

    private float speed = 3f;
    private float arrivalDistance = 0.15f;
    private int repathInterval = 30;
    private float avoidance;
    private PathOptions options = PathOptions.DEFAULT;

    private @Nullable Vec2 target;
    private @Nullable Entity chase;
    private Path path = Path.EMPTY;
    private int waypoint;
    private boolean requesting;
    private int requestId;
    private long revision = -1;
    private int sinceRepath;
    private boolean arrived;
    private Vec2 velocity = Vec2.ZERO;

    /** Creates an idle agent. */
    public NavAgent() {}

    /**
     * Starts walking to a point.
     *
     * @param point world units
     * @return this agent
     */
    public NavAgent moveTo(Vec2 point) {
        chase = null;
        target = point;
        arrived = false;
        request();
        return this;
    }

    /**
     * Keeps walking towards an entity until {@link #stop()}; arriving fires {@link NavTargetReachedEvent} once per
     * approach.
     *
     * @param entity the entity to chase
     * @return this agent
     */
    public NavAgent follow(Entity entity) {
        chase = entity;
        target = entity.position();
        arrived = false;
        request();
        return this;
    }

    /** Stops and forgets the target. */
    public void stop() {
        target = null;
        chase = null;
        path = Path.EMPTY;
        requesting = false;
        requestId++;
        velocity = Vec2.ZERO;
    }

    /**
     * Returns whether the agent has a target it has not reached.
     *
     * @return {@code true} while walking or waiting for a path
     */
    public boolean isMoving() {
        return target != null && !arrived;
    }

    /**
     * Returns the target.
     *
     * @return world units, or {@code null} when idle
     */
    public @Nullable Vec2 target() {
        return target;
    }

    /**
     * Returns the current path.
     *
     * @return the path, empty while idle or searching
     */
    public Path path() {
        return path;
    }

    /**
     * Returns the velocity chosen in the last tick.
     *
     * @return units per second
     */
    public Vec2 velocity() {
        return velocity;
    }

    /**
     * Returns the walking speed.
     *
     * @return units per second
     */
    public float speed() {
        return speed;
    }

    /**
     * Sets the walking speed.
     *
     * @param value units per second, {@code 3} by default
     * @return this agent
     */
    public NavAgent speed(float value) {
        speed = value;
        return this;
    }

    /**
     * Sets how close counts as arrived.
     *
     * @param value world units, {@code 0.15} by default
     * @return this agent
     */
    public NavAgent arrivalDistance(float value) {
        arrivalDistance = value;
        return this;
    }

    /**
     * Sets how often a chased target's path is searched again.
     *
     * @param ticks {@code 30} by default
     * @return this agent
     */
    public NavAgent repathInterval(int ticks) {
        repathInterval = Math.max(1, ticks);
        return this;
    }

    /**
     * Keeps other agents at a distance.
     *
     * @param radius world units, {@code 0} (off) by default
     * @return this agent
     */
    public NavAgent avoidance(float radius) {
        avoidance = radius;
        return this;
    }

    /**
     * Sets how paths are searched.
     *
     * @param value the options
     * @return this agent
     */
    public NavAgent options(PathOptions value) {
        options = value;
        return this;
    }

    private void request() {
        if (!isAttached() || !entity().isSpawned() || target == null) {
            return;
        }
        NavGrid grid = world().navGrid();
        int id = ++requestId;
        requesting = true;
        sinceRepath = 0;
        Vec2 goal = target;
        grid.requestPath(entity().position(), goal, options)
                .thenSync(found -> {
                    if (id == requestId) {
                        requesting = false;
                        path = found;
                        waypoint = found.points().size() > 1 ? 1 : 0;
                        revision = grid.revision();
                    }
                })
                .onFailure(error -> {
                    if (id == requestId && isAttached()) {
                        Entity self = entity();
                        stop();
                        engine().events().call(new NavPathFailedEvent(self, goal));
                    }
                });
    }

    @Override
    protected void onSpawn() {
        if (target != null) {
            request();
        }
    }

    @Override
    protected void onTick() {
        Vec2 goal = target;
        if (goal == null) {
            return;
        }
        Entity self = entity();
        Entity chased = chase;
        if (chased != null) {
            if (chased.isRemoved()) {
                stop();
                return;
            }
            goal = chased.position();
            target = goal;
        }
        sinceRepath++;
        NavGrid grid = world().navGrid();
        if (!requesting && sinceRepath >= repathInterval && (chased != null || grid.revision() != revision)) {
            request();
        }
        Vec2 position = self.position();
        float dt = 1f / engine().targetTps();
        if (position.distanceTo(goal) <= arrivalDistance) {
            drive(Vec2.ZERO);
            if (!arrived) {
                arrived = true;
                engine().events().call(new NavTargetReachedEvent(self, goal));
            }
            if (chase == null && target == goal) {
                target = null;
                path = Path.EMPTY;
            }
            return;
        }
        arrived = false;
        List<Vec2> points = path.points();
        if (points.isEmpty()) {
            drive(Vec2.ZERO);
            return;
        }
        float reach = Math.max(arrivalDistance, speed * dt);
        while (waypoint < points.size() - 1 && position.distanceTo(points.get(waypoint)) <= reach) {
            waypoint++;
        }
        Vec2 next = waypoint < points.size() - 1 ? points.get(waypoint) : goal;
        float distance = position.distanceTo(next);
        Vec2 desired = distance <= 0f ? Vec2.ZERO : position.directionTo(next).scale(Math.min(speed, distance / dt));
        if (avoidance > 0f) {
            desired = desired.add(separation(position).scale(speed)).clampLength(speed);
        }
        drive(desired);
    }

    private Vec2 separation(Vec2 position) {
        float sx = 0f;
        float sy = 0f;
        for (Entity other :
                world().query().with(NavAgent.class).near(position, avoidance).list()) {
            if (other == entity()) {
                continue;
            }
            Vec2 away = position.sub(other.position());
            float d = away.length();
            if (d < 1e-4f) {
                away = Vec2.fromAngle(entity().runtimeId() * 137.5f);
                d = 1e-4f;
            }
            float weight = (avoidance - d) / avoidance;
            sx += away.x() / d * weight;
            sy += away.y() / d * weight;
        }
        return new Vec2(sx, sy);
    }

    private void drive(Vec2 wanted) {
        velocity = wanted;
        Entity self = entity();
        Mover mover = self.find(Mover.class).orElse(null);
        if (mover != null) {
            mover.moveAndSlide(mover.isTopDown() ? wanted : mover.velocity().withX(wanted.x()));
        } else if (!wanted.equals(Vec2.ZERO)) {
            float dt = 1f / engine().targetTps();
            self.setPosition(self.x() + wanted.x() * dt, self.y() + wanted.y() * dt);
        }
    }
}
