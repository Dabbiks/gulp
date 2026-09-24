package dev.gulp.api.ai;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.nav.Path;
import dev.gulp.api.physics.Body;
import dev.gulp.api.physics.CollisionMask;
import dev.gulp.api.physics.Mover;
import dev.gulp.api.physics.RayHit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Steering behaviours combined with weights: each behaviour asks for a velocity change, the weighted sum is limited to
 * {@link #maxForce(float)} and the velocity to {@link #maxSpeed(float)}. With a {@link Mover} the entity moves through
 * {@link Mover#moveAndSlide} (collisions included); without one it sets the position.
 *
 * <pre>{@code
 * EntityType.builder(key("bird"))
 *         .component(() -> new Steering().maxSpeed(4)
 *                 .wander(0.5f)
 *                 .separation(1.5f, 1.2f)
 *                 .alignment(0.8f, 3f)
 *                 .cohesion(0.6f, 3f))
 *         .build();
 * }</pre>
 */
public final class Steering extends Component {

    @FunctionalInterface
    private interface Behaviour {
        Vec2 desired(Steering self, Vec2 position);
    }

    private record Weighted(Behaviour behaviour, float weight) {}

    private final List<Weighted> behaviours = new ArrayList<>();
    private float maxSpeed = 3f;
    private float maxForce = 12f;
    private Vec2 velocity = Vec2.ZERO;
    private Vec2 lastForce = Vec2.ZERO;
    private float wanderAngle;

    /** Creates a steering component without behaviours. */
    public Steering() {}

    /**
     * Sets the top speed.
     *
     * @param value units per second, {@code 3} by default
     * @return this component
     */
    public Steering maxSpeed(float value) {
        maxSpeed = value;
        return this;
    }

    /**
     * Returns the top speed.
     *
     * @return units per second
     */
    public float maxSpeed() {
        return maxSpeed;
    }

    /**
     * Sets how quickly the velocity may change; lower values turn and brake more slowly.
     *
     * @param value units per second squared, {@code 12} by default
     * @return this component
     */
    public Steering maxForce(float value) {
        maxForce = value;
        return this;
    }

    /**
     * Returns the current velocity.
     *
     * @return units per second
     */
    public Vec2 velocity() {
        return velocity;
    }

    /**
     * Replaces the velocity.
     *
     * @param value units per second
     * @return this component
     */
    public Steering setVelocity(Vec2 value) {
        velocity = value;
        return this;
    }

    /**
     * Returns the combined steering force of the last tick, for debugging.
     *
     * @return units per second squared
     */
    public Vec2 lastForce() {
        return lastForce;
    }

    /**
     * Removes all behaviours.
     *
     * @return this component
     */
    public Steering clear() {
        behaviours.clear();
        return this;
    }

    /**
     * Heads straight for a moving point at full speed.
     *
     * @param target the point, asked every tick
     * @param weight the weight
     * @return this component
     */
    public Steering seek(Supplier<Vec2> target, float weight) {
        return add((self, p) -> p.directionTo(target.get()).scale(maxSpeed), weight);
    }

    /**
     * Heads straight for an entity.
     *
     * @param target the entity
     * @param weight the weight
     * @return this component
     */
    public Steering seek(Entity target, float weight) {
        return seek(target::position, weight);
    }

    /**
     * Runs away from a point while it is closer than {@code panicDistance}.
     *
     * @param threat the point, asked every tick
     * @param weight the weight
     * @param panicDistance how close is too close
     * @return this component
     */
    public Steering flee(Supplier<Vec2> threat, float weight, float panicDistance) {
        return add(
                (self, p) -> {
                    Vec2 from = threat.get();
                    return p.distanceTo(from) > panicDistance
                            ? velocity
                            : from.directionTo(p).scale(maxSpeed);
                },
                weight);
    }

    /**
     * Heads for a point and slows down within {@code slowRadius} to stop on it.
     *
     * @param target the point, asked every tick
     * @param weight the weight
     * @param slowRadius where braking starts
     * @return this component
     */
    public Steering arrive(Supplier<Vec2> target, float weight, float slowRadius) {
        return add(
                (self, p) -> {
                    Vec2 to = target.get().sub(p);
                    float d = to.length();
                    if (d < 1e-3f) {
                        return Vec2.ZERO;
                    }
                    float speed = d < slowRadius ? maxSpeed * d / slowRadius : maxSpeed;
                    return to.scale(speed / d);
                },
                weight);
    }

    /**
     * Wanders around smoothly at random.
     *
     * @param weight the weight
     * @return this component
     */
    public Steering wander(float weight) {
        return add(
                (self, p) -> {
                    wanderAngle += (world().rng().nextFloat() - 0.5f) * 60f;
                    Vec2 ahead = velocity.lengthSquared() > 1e-6f ? velocity.normalized() : Vec2.fromAngle(wanderAngle);
                    Vec2 circle = ahead.scale(2f).add(Vec2.fromAngle(wanderAngle));
                    return circle.normalized().scale(maxSpeed);
                },
                weight);
    }

    /**
     * Heads for where an entity will be, judging by its velocity.
     *
     * @param target the entity
     * @param weight the weight
     * @return this component
     */
    public Steering pursue(Entity target, float weight) {
        return add((self, p) -> p.directionTo(predict(target, p)).scale(maxSpeed), weight);
    }

    /**
     * Runs from where an entity will be, while it is closer than {@code panicDistance}.
     *
     * @param threat the entity
     * @param weight the weight
     * @param panicDistance how close is too close
     * @return this component
     */
    public Steering evade(Entity threat, float weight, float panicDistance) {
        return add(
                (self, p) -> {
                    if (p.distanceTo(threat.position()) > panicDistance) {
                        return velocity;
                    }
                    return predict(threat, p).directionTo(p).scale(maxSpeed);
                },
                weight);
    }

    /**
     * Keeps away from other steering entities within a radius.
     *
     * @param weight the weight
     * @param radius the personal space
     * @return this component
     */
    public Steering separation(float weight, float radius) {
        return add(
                (self, p) -> {
                    Vec2 push = Vec2.ZERO;
                    for (Entity other : neighbours(p, radius)) {
                        Vec2 away = p.sub(other.position());
                        float d = Math.max(away.length(), 1e-3f);
                        push = push.add(away.scale((radius - d) / (radius * d)));
                    }
                    return push.equals(Vec2.ZERO) ? velocity : push.normalized().scale(maxSpeed);
                },
                weight);
    }

    /**
     * Moves towards the centre of other steering entities within a radius.
     *
     * @param weight the weight
     * @param radius the neighbourhood
     * @return this component
     */
    public Steering cohesion(float weight, float radius) {
        return add(
                (self, p) -> {
                    List<Entity> near = neighbours(p, radius);
                    if (near.isEmpty()) {
                        return velocity;
                    }
                    float x = 0f;
                    float y = 0f;
                    for (Entity other : near) {
                        x += other.x();
                        y += other.y();
                    }
                    return p.directionTo(new Vec2(x / near.size(), y / near.size()))
                            .scale(maxSpeed);
                },
                weight);
    }

    /**
     * Matches the heading of other steering entities within a radius.
     *
     * @param weight the weight
     * @param radius the neighbourhood
     * @return this component
     */
    public Steering alignment(float weight, float radius) {
        return add(
                (self, p) -> {
                    Vec2 sum = Vec2.ZERO;
                    for (Entity other : neighbours(p, radius)) {
                        sum = sum.add(other.get(Steering.class).velocity());
                    }
                    return sum.equals(Vec2.ZERO) ? velocity : sum.normalized().scale(maxSpeed);
                },
                weight);
    }

    /**
     * Follows a path, heading for a point a little ahead of the closest point on it.
     *
     * @param path the path
     * @param weight the weight
     * @param lookAhead how far ahead along the path to aim
     * @return this component
     */
    public Steering followPath(Path path, float weight, float lookAhead) {
        return add(
                (self, p) -> {
                    if (path.isEmpty()) {
                        return Vec2.ZERO;
                    }
                    Vec2 aim = path.pointAt(path.project(p) + lookAhead);
                    return p.distanceTo(aim) < 1e-3f
                            ? Vec2.ZERO
                            : p.directionTo(aim).scale(maxSpeed);
                },
                weight);
    }

    /**
     * Turns away from walls, tiles and colliders ahead, found with rays along the velocity.
     *
     * @param weight the weight
     * @param lookAhead how far ahead to look
     * @return this component
     */
    public Steering avoidObstacles(float weight, float lookAhead) {
        return add(
                (self, p) -> {
                    if (velocity.lengthSquared() < 1e-6f) {
                        return velocity;
                    }
                    Vec2 heading = velocity.normalized();
                    Vec2 best = Vec2.ZERO;
                    float nearest = Float.MAX_VALUE;
                    for (float angle : new float[] {0f, -30f, 30f}) {
                        Vec2 ray = heading.rotated(angle).scale(lookAhead);
                        RayHit hit = world().physics().raycast(p, p.add(ray), CollisionMask.ALL);
                        if (hit != null && hit.entity() != entity() && hit.fraction() < nearest) {
                            nearest = hit.fraction();
                            best = hit.normal();
                        }
                    }
                    return best.equals(Vec2.ZERO) ? velocity : best.scale(maxSpeed);
                },
                weight);
    }

    private Steering add(Behaviour behaviour, float weight) {
        behaviours.add(new Weighted(behaviour, weight));
        return this;
    }

    private List<Entity> neighbours(Vec2 position, float radius) {
        List<Entity> result = new ArrayList<>();
        for (Entity other :
                world().query().with(Steering.class).near(position, radius).list()) {
            if (other != entity()) {
                result.add(other);
            }
        }
        return result;
    }

    private static Vec2 predict(Entity target, Vec2 from) {
        Vec2 speed = target.find(Steering.class)
                .map(Steering::velocity)
                .or(() -> target.find(Mover.class).map(Mover::velocity))
                .or(() -> target.find(Body.class).map(Body::velocity))
                .orElse(Vec2.ZERO);
        float lead = Math.min(1f, from.distanceTo(target.position()) / 4f);
        return target.position().add(speed.scale(lead));
    }

    @Override
    protected void onTick() {
        Entity self = entity();
        Vec2 position = self.position();
        float dt = 1f / engine().targetTps();
        float fx = 0f;
        float fy = 0f;
        for (int i = 0; i < behaviours.size(); i++) {
            Weighted w = behaviours.get(i);
            Vec2 wanted = w.behaviour.desired(this, position);
            // Each behaviour asks to reach its velocity within this tick; maxForce limits how fast that happens.
            fx += (wanted.x() - velocity.x()) * w.weight / dt;
            fy += (wanted.y() - velocity.y()) * w.weight / dt;
        }
        lastForce = new Vec2(fx, fy).clampLength(maxForce);
        velocity = velocity.add(lastForce.scale(dt)).clampLength(maxSpeed);
        Mover mover = self.find(Mover.class).orElse(null);
        if (mover != null) {
            mover.moveAndSlide(velocity);
            velocity = mover.velocity();
        } else {
            self.setPosition(position.x() + velocity.x() * dt, position.y() + velocity.y() * dt);
        }
    }
}
