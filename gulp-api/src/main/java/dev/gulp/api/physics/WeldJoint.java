package dev.gulp.api.physics;

/**
 * Glues two bodies together at a point, keeping their relative position and angle.
 *
 * <pre>{@code
 * physics.weld(sword, hand, hand.position());
 * }</pre>
 */
public non-sealed interface WeldJoint extends Joint {

    /**
     * Makes the weld bend like a spring.
     *
     * @param frequencyHz oscillations per second, {@code 0} for rigid
     * @param dampingRatio {@code 0} to {@code 1}
     * @return this joint
     */
    WeldJoint setSpring(float frequencyHz, float dampingRatio);
}
