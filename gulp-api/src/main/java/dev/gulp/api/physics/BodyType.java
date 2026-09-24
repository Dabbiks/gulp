package dev.gulp.api.physics;

/**
 * How a {@link Body} moves.
 *
 * <pre>{@code
 * crate.add(new Body(BodyType.DYNAMIC).density(2f));
 * }</pre>
 */
public enum BodyType {
    /** Moved by forces, gravity and contacts. */
    DYNAMIC,
    /** Moved only by its velocity; pushes dynamic bodies and ignores forces. */
    KINEMATIC,
    /** Never moves. */
    STATIC
}
