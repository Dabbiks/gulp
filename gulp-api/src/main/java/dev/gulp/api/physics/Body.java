package dev.gulp.api.physics;

import dev.gulp.api.entity.Component;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.spi.PhysicsAccess;
import org.jspecify.annotations.Nullable;

/**
 * A rigid body simulated by the world's physics: gravity, forces, contacts with friction and bounce, joints, sleeping.
 * The shape comes from {@link #shape(Shape)}, else from the entity's {@link Collider} (with its layer and mask), else a
 * box of the entity's size. The body drives the entity's position and rotation; moving the entity by hand teleports the
 * body.
 *
 * <p>Units: world units, seconds, degrees; the mass of a shape is its area times {@link #density()}.
 *
 * <pre>{@code
 * EntityType crate = EntityType.builder(key("crate"))
 *         .size(1, 1)
 *         .component(() -> new Body(BodyType.DYNAMIC).friction(0.8f))
 *         .build();
 * world.spawn(crate, 5, 2).get(Body.class).applyImpulse(new Vec2(4, -6));
 * }</pre>
 */
public final class Body extends Component {

    private BodyType type;
    private @Nullable Shape shape;
    private float density = 1f;
    private float mass;
    private float friction = 0.6f;
    private float restitution;
    private float linearDamping;
    private float angularDamping = 0.05f;
    private boolean fixedRotation;
    private boolean bullet;
    private float gravityScale = 1f;
    private boolean sleepingAllowed = true;
    private PhysicsAccess.@Nullable BodyHandle handle;
    private Vec2 pendingVelocity = Vec2.ZERO;
    private float pendingAngularVelocity;

    /**
     * Creates a body.
     *
     * @param type how it moves
     */
    public Body(BodyType type) {
        this.type = type;
    }

    /**
     * Returns how the body moves.
     *
     * @return the type
     */
    public BodyType type() {
        return type;
    }

    /**
     * Changes how the body moves.
     *
     * @param value the type
     * @return this body
     */
    public Body type(BodyType value) {
        type = value;
        return refresh();
    }

    /**
     * Returns the shape set on the body.
     *
     * @return the shape, or {@code null} to use the collider's or the entity's size
     */
    public @Nullable Shape shape() {
        return shape;
    }

    /**
     * Sets the shape, relative to the entity position.
     *
     * @param value the shape, or {@code null}
     * @return this body
     */
    public Body shape(@Nullable Shape value) {
        shape = value;
        return refresh();
    }

    /**
     * Returns the density.
     *
     * @return mass per square unit, {@code 1} by default
     */
    public float density() {
        return density;
    }

    /**
     * Sets the density; the mass follows from the area.
     *
     * @param value mass per square unit
     * @return this body
     */
    public Body density(float value) {
        density = value;
        return refresh();
    }

    /**
     * Returns the mass: the fixed one, or for a simulated dynamic body the one computed from its shape.
     *
     * @return the mass, {@code 0} for static and kinematic bodies
     */
    public float mass() {
        PhysicsAccess.BodyHandle current = handle;
        return current != null ? current.mass() : mass;
    }

    /**
     * Fixes the mass regardless of the shape's area.
     *
     * @param value the mass, or {@code 0} to derive it from the density
     * @return this body
     */
    public Body mass(float value) {
        mass = value;
        return refresh();
    }

    /**
     * Returns the fixed mass.
     *
     * @return the mass set with {@link #mass(float)}, or {@code 0}
     */
    public float fixedMass() {
        return mass;
    }

    /**
     * Returns the friction.
     *
     * @return {@code 0.6} by default; contacts use the geometric mean of both
     */
    public float friction() {
        return friction;
    }

    /**
     * Sets the friction.
     *
     * @param value {@code 0} for ice, {@code 1} for rubber
     * @return this body
     */
    public Body friction(float value) {
        friction = value;
        return this;
    }

    /**
     * Returns the bounciness.
     *
     * @return {@code 0} by default; contacts use the larger of both
     */
    public float restitution() {
        return restitution;
    }

    /**
     * Sets the bounciness.
     *
     * @param value {@code 0} stops, {@code 1} bounces back fully
     * @return this body
     */
    public Body restitution(float value) {
        restitution = value;
        return this;
    }

    /**
     * Returns the linear damping.
     *
     * @return per second
     */
    public float linearDamping() {
        return linearDamping;
    }

    /**
     * Slows the body down over time, like air drag.
     *
     * @param value per second
     * @return this body
     */
    public Body linearDamping(float value) {
        linearDamping = value;
        return this;
    }

    /**
     * Returns the angular damping.
     *
     * @return per second
     */
    public float angularDamping() {
        return angularDamping;
    }

    /**
     * Slows the rotation down over time.
     *
     * @param value per second
     * @return this body
     */
    public Body angularDamping(float value) {
        angularDamping = value;
        return this;
    }

    /**
     * Returns whether the body never rotates.
     *
     * @return {@code true} if rotation is locked
     */
    public boolean isFixedRotation() {
        return fixedRotation;
    }

    /**
     * Locks the rotation, for characters.
     *
     * @param value whether locked
     * @return this body
     */
    public Body fixedRotation(boolean value) {
        fixedRotation = value;
        return refresh();
    }

    /**
     * Returns whether continuous collision detection is on.
     *
     * @return {@code true} for bullets
     */
    public boolean isBullet() {
        return bullet;
    }

    /**
     * Stops a fast body from passing through thin obstacles, at some cost.
     *
     * @param value whether to sweep the body's motion
     * @return this body
     */
    public Body bullet(boolean value) {
        bullet = value;
        return this;
    }

    /**
     * Returns the gravity scale.
     *
     * @return {@code 1} by default
     */
    public float gravityScale() {
        return gravityScale;
    }

    /**
     * Scales the world gravity for this body.
     *
     * @param value {@code 0} to float
     * @return this body
     */
    public Body gravityScale(float value) {
        gravityScale = value;
        return this;
    }

    /**
     * Returns whether the body may fall asleep when it rests.
     *
     * @return {@code true} by default
     */
    public boolean isSleepingAllowed() {
        return sleepingAllowed;
    }

    /**
     * Allows or forbids sleeping.
     *
     * @param value whether allowed
     * @return this body
     */
    public Body sleepingAllowed(boolean value) {
        sleepingAllowed = value;
        return this;
    }

    /**
     * Returns the velocity.
     *
     * @return units per second
     */
    public Vec2 velocity() {
        PhysicsAccess.BodyHandle current = handle;
        return current != null ? new Vec2(current.velocityX(), current.velocityY()) : pendingVelocity;
    }

    /**
     * Sets the velocity and wakes the body.
     *
     * @param value units per second
     * @return this body
     */
    public Body setVelocity(Vec2 value) {
        PhysicsAccess.BodyHandle current = handle;
        if (current != null) {
            current.setVelocity(value.x(), value.y());
        } else {
            pendingVelocity = value;
        }
        return this;
    }

    /**
     * Returns the angular velocity.
     *
     * @return degrees per second, clockwise positive
     */
    public float angularVelocity() {
        PhysicsAccess.BodyHandle current = handle;
        return current != null ? current.angularVelocity() : pendingAngularVelocity;
    }

    /**
     * Sets the angular velocity and wakes the body.
     *
     * @param degreesPerSecond the velocity
     * @return this body
     */
    public Body setAngularVelocity(float degreesPerSecond) {
        PhysicsAccess.BodyHandle current = handle;
        if (current != null) {
            current.setAngularVelocity(degreesPerSecond);
        } else {
            pendingAngularVelocity = degreesPerSecond;
        }
        return this;
    }

    /**
     * Pushes the body's centre of mass during the next step.
     *
     * @param force the force
     * @return this body
     */
    public Body applyForce(Vec2 force) {
        PhysicsAccess.BodyHandle current = live();
        current.applyForce(force.x(), force.y(), Float.NaN, Float.NaN);
        return this;
    }

    /**
     * Pushes the body at a world point during the next step, which may also turn it.
     *
     * @param force the force
     * @param point where it acts, in world units
     * @return this body
     */
    public Body applyForce(Vec2 force, Vec2 point) {
        live().applyForce(force.x(), force.y(), point.x(), point.y());
        return this;
    }

    /**
     * Changes the velocity at once, as by a hit, at the centre of mass.
     *
     * @param impulse the impulse (mass times velocity change)
     * @return this body
     */
    public Body applyImpulse(Vec2 impulse) {
        live().applyImpulse(impulse.x(), impulse.y(), Float.NaN, Float.NaN);
        return this;
    }

    /**
     * Changes the velocity and spin at once, as by a hit at a world point.
     *
     * @param impulse the impulse
     * @param point where it acts
     * @return this body
     */
    public Body applyImpulse(Vec2 impulse, Vec2 point) {
        live().applyImpulse(impulse.x(), impulse.y(), point.x(), point.y());
        return this;
    }

    /**
     * Turns the body during the next step.
     *
     * @param torque the torque, clockwise positive
     * @return this body
     */
    public Body applyTorque(float torque) {
        live().applyTorque(torque);
        return this;
    }

    /**
     * Returns whether the body is simulated this tick.
     *
     * @return {@code false} while asleep or not in a world
     */
    public boolean isAwake() {
        PhysicsAccess.BodyHandle current = handle;
        return current != null && current.isAwake();
    }

    /** Wakes the body and the bodies resting on it. */
    public void wakeUp() {
        PhysicsAccess.BodyHandle current = handle;
        if (current != null) {
            current.setAwake(true);
        }
    }

    private PhysicsAccess.BodyHandle live() {
        PhysicsAccess.BodyHandle current = handle;
        if (current == null) {
            throw new IllegalStateException("Forces and impulses need a spawned body");
        }
        return current;
    }

    private Body refresh() {
        PhysicsAccess.BodyHandle current = handle;
        if (current != null) {
            current.refresh();
        }
        return this;
    }

    @Override
    protected void onSpawn() {
        handle = PhysicsAccess.backend().attachBody(this);
        handle.setVelocity(pendingVelocity.x(), pendingVelocity.y());
        handle.setAngularVelocity(pendingAngularVelocity);
    }

    @Override
    protected void onRemove() {
        if (handle != null) {
            pendingVelocity = velocity();
            pendingAngularVelocity = angularVelocity();
            handle = null;
            PhysicsAccess.backend().detach(this);
        }
    }
}
