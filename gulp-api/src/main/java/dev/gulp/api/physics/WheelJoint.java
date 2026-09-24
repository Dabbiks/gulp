package dev.gulp.api.physics;

/**
 * A wheel on a suspension: the second body turns freely and moves along an axis of the first on a spring, with an
 * optional motor turning it.
 *
 * <pre>{@code
 * WheelJoint rear = physics.wheel(car, wheel, wheel.position(), Vec2.UP);
 * rear.setSpring(5f, 0.7f).enableMotor(720, 40);
 * }</pre>
 */
public non-sealed interface WheelJoint extends Joint {

    /**
     * Sets the suspension spring.
     *
     * @param frequencyHz oscillations per second, {@code 0} for rigid
     * @param dampingRatio {@code 0} to {@code 1}
     * @return this joint
     */
    WheelJoint setSpring(float frequencyHz, float dampingRatio);

    /**
     * Drives the wheel at a speed with at most a torque.
     *
     * @param degreesPerSecond the wanted spin
     * @param maxTorque the strongest torque
     * @return this joint
     */
    WheelJoint enableMotor(float degreesPerSecond, float maxTorque);

    /**
     * Turns the motor off.
     *
     * @return this joint
     */
    WheelJoint disableMotor();
}
