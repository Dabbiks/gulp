package dev.gulp.api.physics;

/**
 * Lets the second body slide along an axis of the first without turning, like a piston or a lift, with an optional
 * range and motor.
 *
 * <pre>{@code
 * PrismaticJoint lift = physics.prismatic(platform, shaft, platform.position(), Vec2.UP);
 * lift.enableLimit(0, 6).enableMotor(2, 500);
 * }</pre>
 */
public non-sealed interface PrismaticJoint extends Joint {

    /**
     * Returns how far the second body moved along the axis since the joint was made.
     *
     * @return world units
     */
    float translation();

    /**
     * Limits the translation.
     *
     * @param lower the lowest translation
     * @param upper the highest translation
     * @return this joint
     */
    PrismaticJoint enableLimit(float lower, float upper);

    /**
     * Removes the limit.
     *
     * @return this joint
     */
    PrismaticJoint disableLimit();

    /**
     * Drives the joint at a speed with at most a force.
     *
     * @param speed units per second along the axis
     * @param maxForce the strongest force the motor may use
     * @return this joint
     */
    PrismaticJoint enableMotor(float speed, float maxForce);

    /**
     * Turns the motor off.
     *
     * @return this joint
     */
    PrismaticJoint disableMotor();
}
