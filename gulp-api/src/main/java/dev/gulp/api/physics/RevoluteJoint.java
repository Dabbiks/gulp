package dev.gulp.api.physics;

/**
 * Pins two bodies together at one point around which they turn, like a hinge or an axle, with an optional angle range
 * and motor.
 *
 * <pre>{@code
 * RevoluteJoint hinge = physics.revolute(door, frame, new Vec2(4, 3));
 * hinge.enableLimit(0, 100);
 * hinge.enableMotor(-45, 50);
 * }</pre>
 */
public non-sealed interface RevoluteJoint extends Joint {

    /**
     * Returns the angle of the second body relative to the first, from when the joint was made.
     *
     * @return degrees
     */
    float angle();

    /**
     * Limits the relative angle.
     *
     * @param lowerDegrees the lowest angle
     * @param upperDegrees the highest angle
     * @return this joint
     */
    RevoluteJoint enableLimit(float lowerDegrees, float upperDegrees);

    /**
     * Removes the angle limit.
     *
     * @return this joint
     */
    RevoluteJoint disableLimit();

    /**
     * Drives the joint at a speed with at most a torque.
     *
     * @param degreesPerSecond the wanted relative speed
     * @param maxTorque the strongest torque the motor may use
     * @return this joint
     */
    RevoluteJoint enableMotor(float degreesPerSecond, float maxTorque);

    /**
     * Turns the motor off.
     *
     * @return this joint
     */
    RevoluteJoint disableMotor();
}
