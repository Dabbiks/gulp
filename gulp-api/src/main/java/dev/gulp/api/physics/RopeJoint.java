package dev.gulp.api.physics;

/**
 * Keeps two anchor points no farther apart than a maximum length, like a rope; they may come closer freely.
 *
 * <pre>{@code
 * physics.rope(lamp, ceiling, lamp.position(), hook, 3f);
 * }</pre>
 */
public non-sealed interface RopeJoint extends Joint {

    /**
     * Returns the length of the rope.
     *
     * @return world units
     */
    float maxLength();

    /**
     * Changes the length of the rope.
     *
     * @param value world units
     * @return this joint
     */
    RopeJoint setMaxLength(float value);
}
