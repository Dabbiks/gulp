package dev.gulp.api.physics;

import dev.gulp.api.math.Vec2;

/**
 * Pulls a point of a body towards a target with a limited force, for dragging bodies with the mouse or a finger.
 *
 * <pre>{@code
 * MouseJoint drag = physics.mouse(crate, input().mouseWorld());
 * every(1, () -> drag.setTarget(input().mouseWorld()));
 * }</pre>
 */
public non-sealed interface MouseJoint extends Joint {

    /**
     * Returns the target.
     *
     * @return world units
     */
    Vec2 target();

    /**
     * Moves the target.
     *
     * @param value world units
     * @return this joint
     */
    MouseJoint setTarget(Vec2 value);

    /**
     * Limits the pulling force.
     *
     * @param value the strongest force, {@code 1000 * mass} by default
     * @return this joint
     */
    MouseJoint setMaxForce(float value);

    /**
     * Sets how softly the point follows the target.
     *
     * @param frequencyHz oscillations per second, {@code 5} by default
     * @param dampingRatio {@code 0.7} by default
     * @return this joint
     */
    MouseJoint setSpring(float frequencyHz, float dampingRatio);
}
