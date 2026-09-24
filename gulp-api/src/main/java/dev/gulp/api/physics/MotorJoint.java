package dev.gulp.api.physics;

import dev.gulp.api.math.Vec2;

/**
 * Drives the second body towards an offset and angle relative to the first with limited force and torque, for
 * controlled bodies that should still react to hits.
 *
 * <pre>{@code
 * MotorJoint arm = physics.motor(shoulder, hand);
 * arm.setLinearOffset(new Vec2(1, 0)).setMaxForce(200);
 * }</pre>
 */
public non-sealed interface MotorJoint extends Joint {

    /**
     * Sets the wanted position of the second body in the first body's frame.
     *
     * @param offset world units
     * @return this joint
     */
    MotorJoint setLinearOffset(Vec2 offset);

    /**
     * Sets the wanted angle of the second body relative to the first.
     *
     * @param degrees the angle
     * @return this joint
     */
    MotorJoint setAngularOffset(float degrees);

    /**
     * Limits the force.
     *
     * @param value the strongest force
     * @return this joint
     */
    MotorJoint setMaxForce(float value);

    /**
     * Limits the torque.
     *
     * @param value the strongest torque
     * @return this joint
     */
    MotorJoint setMaxTorque(float value);

    /**
     * Sets how much of the position error is corrected each step.
     *
     * @param value {@code 0..1}, {@code 0.3} by default
     * @return this joint
     */
    MotorJoint setCorrectionFactor(float value);
}
