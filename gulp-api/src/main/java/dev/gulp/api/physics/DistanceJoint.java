package dev.gulp.api.physics;

/**
 * Keeps two anchor points at a distance, rigidly or as a spring, optionally within a range.
 *
 * <pre>{@code
 * DistanceJoint spring = physics.distance(car, wheel, car.position(), wheel.position());
 * spring.setSpring(4f, 0.5f);
 * }</pre>
 */
public non-sealed interface DistanceJoint extends Joint {

    /**
     * Returns the rest length.
     *
     * @return world units
     */
    float length();

    /**
     * Sets the rest length.
     *
     * @param value world units
     * @return this joint
     */
    DistanceJoint setLength(float value);

    /**
     * Allows the distance to vary within a range; a spring still pulls towards the rest length.
     *
     * @param min shortest distance
     * @param max longest distance
     * @return this joint
     */
    DistanceJoint setRange(float min, float max);

    /**
     * Makes the joint springy.
     *
     * @param frequencyHz oscillations per second, {@code 0} for rigid
     * @param dampingRatio {@code 0} bounces forever, {@code 1} stops without overshooting
     * @return this joint
     */
    DistanceJoint setSpring(float frequencyHz, float dampingRatio);
}
