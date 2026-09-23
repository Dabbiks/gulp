package dev.gulp.api.math;

/**
 * Position, rotation, scale and skew of an object; applied as skew, scale, rotation, then translation.
 *
 * <pre>{@code
 * Transform2D t = Transform2D.of(new Vec2(4, 2), 90f, Vec2.ONE);
 * Vec2 world = t.apply(new Vec2(1, 0));      // (4, 3)
 * Transform2D moved = t.withPosition(t.position().add(1, 0));
 * }</pre>
 *
 * @param position translation
 * @param rotation degrees, positive towards +Y
 * @param scale scale per axis
 * @param skew shear angles in degrees per axis
 */
public record Transform2D(Vec2 position, float rotation, Vec2 scale, Vec2 skew) {

    /** No transformation. */
    public static final Transform2D IDENTITY = new Transform2D(Vec2.ZERO, 0f, Vec2.ONE, Vec2.ZERO);

    /**
     * Creates a transform without skew.
     *
     * @param position translation
     * @param rotation degrees
     * @param scale scale per axis
     * @return the transform
     */
    public static Transform2D of(Vec2 position, float rotation, Vec2 scale) {
        return new Transform2D(position, rotation, scale, Vec2.ZERO);
    }

    /**
     * Replaces the position.
     *
     * @param newPosition the position
     * @return the new transform
     */
    public Transform2D withPosition(Vec2 newPosition) {
        return new Transform2D(newPosition, rotation, scale, skew);
    }

    /**
     * Replaces the rotation.
     *
     * @param degrees the rotation
     * @return the new transform
     */
    public Transform2D withRotation(float degrees) {
        return new Transform2D(position, degrees, scale, skew);
    }

    /**
     * Replaces the scale.
     *
     * @param newScale the scale
     * @return the new transform
     */
    public Transform2D withScale(Vec2 newScale) {
        return new Transform2D(position, rotation, newScale, skew);
    }

    /**
     * Converts to an affine matrix.
     *
     * @return a new affine transform
     */
    public Affine2 toAffine() {
        Affine2 affine = new Affine2().translate(position.x(), position.y()).rotate(rotation);
        affine.scale(scale.x(), scale.y());
        if (skew.x() != 0f || skew.y() != 0f) {
            affine.shear((float) Math.tan(Mathf.degToRad(skew.x())), (float) Math.tan(Mathf.degToRad(skew.y())));
        }
        return affine;
    }

    /**
     * Converts to a matrix.
     *
     * @return the matrix
     */
    public Mat3 toMat3() {
        return Mat3.of(toAffine());
    }

    /**
     * Transforms a point from local to parent coordinates.
     *
     * @param local the local point
     * @return the transformed point
     */
    public Vec2 apply(Vec2 local) {
        return toAffine().apply(local);
    }
}
