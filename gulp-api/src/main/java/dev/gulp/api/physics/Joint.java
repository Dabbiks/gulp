package dev.gulp.api.physics;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.Vec2;

/**
 * A constraint between two bodies, created by the world's {@link Physics}. Removing either entity removes the joint.
 *
 * <pre>{@code
 * RevoluteJoint hinge = world.physics().revolute(door, wall, new Vec2(3, 2));
 * hinge.enableLimit(-90, 0);
 * }</pre>
 */
public sealed interface Joint
        permits DistanceJoint, RopeJoint, RevoluteJoint, PrismaticJoint, WeldJoint, WheelJoint, MouseJoint, MotorJoint {

    /**
     * Returns the first entity.
     *
     * @return the entity
     */
    Entity entityA();

    /**
     * Returns the second entity.
     *
     * @return the entity; for a mouse joint the same as {@link #entityA()}
     */
    Entity entityB();

    /**
     * Returns the anchor on the first body, in world units.
     *
     * @return the anchor
     */
    Vec2 anchorA();

    /**
     * Returns the anchor on the second body, in world units.
     *
     * @return the anchor
     */
    Vec2 anchorB();

    /**
     * Returns whether the joined bodies still collide with each other.
     *
     * @return {@code false} by default
     */
    boolean collideConnected();

    /**
     * Lets the joined bodies collide with each other or not.
     *
     * @param value whether they collide
     * @return this joint
     */
    Joint setCollideConnected(boolean value);

    /**
     * Returns the force the joint applied in the last step.
     *
     * @return the magnitude of the reaction force
     */
    float reactionForce();

    /**
     * Returns whether the joint is still in its world.
     *
     * @return {@code false} after removal
     */
    boolean isValid();

    /** Removes the joint and wakes its bodies. */
    void remove();
}
