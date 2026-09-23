package dev.gulp.api.math;

/**
 * Mutable 2D affine transform (a 2x3 matrix) for hot paths such as the sprite batcher; operations change this instance
 * and do not allocate.
 *
 * <pre>
 * x' = m00 * x + m01 * y + m02
 * y' = m10 * x + m11 * y + m12
 * </pre>
 *
 * <pre>{@code
 * Affine2 t = new Affine2().translate(10, 5).rotate(30).scale(2, 2);
 * float sx = t.transformX(1, 0), sy = t.transformY(1, 0);
 * }</pre>
 */
public final class Affine2 {

    /** Row 0, column 0. */
    public float m00 = 1f;
    /** Row 0, column 1. */
    public float m01;
    /** Row 0, column 2 (translation X). */
    public float m02;
    /** Row 1, column 0. */
    public float m10;
    /** Row 1, column 1. */
    public float m11 = 1f;
    /** Row 1, column 2 (translation Y). */
    public float m12;

    /** Creates the identity transform. */
    public Affine2() {}

    /**
     * Resets to identity.
     *
     * @return this transform
     */
    public Affine2 identity() {
        m00 = 1f;
        m01 = 0f;
        m02 = 0f;
        m10 = 0f;
        m11 = 1f;
        m12 = 0f;
        return this;
    }

    /**
     * Copies another transform.
     *
     * @param other the transform
     * @return this transform
     */
    public Affine2 set(Affine2 other) {
        m00 = other.m00;
        m01 = other.m01;
        m02 = other.m02;
        m10 = other.m10;
        m11 = other.m11;
        m12 = other.m12;
        return this;
    }

    /**
     * Sets translation, rotation and scale (applied as scale, then rotation, then translation).
     *
     * @param x translation X
     * @param y translation Y
     * @param degrees rotation
     * @param scaleX scale X
     * @param scaleY scale Y
     * @return this transform
     */
    public Affine2 setTo(float x, float y, float degrees, float scaleX, float scaleY) {
        float cos = Mathf.cosDeg(degrees);
        float sin = Mathf.sinDeg(degrees);
        m00 = cos * scaleX;
        m01 = -sin * scaleY;
        m02 = x;
        m10 = sin * scaleX;
        m11 = cos * scaleY;
        m12 = y;
        return this;
    }

    /**
     * Appends a translation (in local coordinates).
     *
     * @param x offset X
     * @param y offset Y
     * @return this transform
     */
    public Affine2 translate(float x, float y) {
        m02 += m00 * x + m01 * y;
        m12 += m10 * x + m11 * y;
        return this;
    }

    /**
     * Appends a rotation.
     *
     * @param degrees the angle, positive towards +Y
     * @return this transform
     */
    public Affine2 rotate(float degrees) {
        if (degrees == 0f) {
            return this;
        }
        float cos = Mathf.cosDeg(degrees);
        float sin = Mathf.sinDeg(degrees);
        float a = m00 * cos + m01 * sin;
        float b = -m00 * sin + m01 * cos;
        float c = m10 * cos + m11 * sin;
        float d = -m10 * sin + m11 * cos;
        m00 = a;
        m01 = b;
        m10 = c;
        m11 = d;
        return this;
    }

    /**
     * Appends a scale.
     *
     * @param sx scale X
     * @param sy scale Y
     * @return this transform
     */
    public Affine2 scale(float sx, float sy) {
        m00 *= sx;
        m10 *= sx;
        m01 *= sy;
        m11 *= sy;
        return this;
    }

    /**
     * Appends a shear.
     *
     * @param shearX X shear factor
     * @param shearY Y shear factor
     * @return this transform
     */
    public Affine2 shear(float shearX, float shearY) {
        float a = m00 + m01 * shearY;
        float b = m00 * shearX + m01;
        float c = m10 + m11 * shearY;
        float d = m10 * shearX + m11;
        m00 = a;
        m01 = b;
        m10 = c;
        m11 = d;
        return this;
    }

    /**
     * Appends another transform: the result applies {@code other} first, then this one.
     *
     * @param other the transform
     * @return this transform
     */
    public Affine2 mul(Affine2 other) {
        float a = m00 * other.m00 + m01 * other.m10;
        float b = m00 * other.m01 + m01 * other.m11;
        float c = m00 * other.m02 + m01 * other.m12 + m02;
        float d = m10 * other.m00 + m11 * other.m10;
        float e = m10 * other.m01 + m11 * other.m11;
        float f = m10 * other.m02 + m11 * other.m12 + m12;
        m00 = a;
        m01 = b;
        m02 = c;
        m10 = d;
        m11 = e;
        m12 = f;
        return this;
    }

    /**
     * Determinant of the linear part.
     *
     * @return the determinant
     */
    public float determinant() {
        return m00 * m11 - m01 * m10;
    }

    /**
     * Inverts this transform.
     *
     * @return this transform
     * @throws IllegalStateException if it cannot be inverted
     */
    public Affine2 invert() {
        float det = determinant();
        if (det == 0f) {
            throw new IllegalStateException("Transform is not invertible");
        }
        float inv = 1f / det;
        float a = m11 * inv;
        float b = -m01 * inv;
        float c = (m01 * m12 - m11 * m02) * inv;
        float d = -m10 * inv;
        float e = m00 * inv;
        float f = (m10 * m02 - m00 * m12) * inv;
        m00 = a;
        m01 = b;
        m02 = c;
        m10 = d;
        m11 = e;
        m12 = f;
        return this;
    }

    /**
     * Transforms X of a point.
     *
     * @param x point X
     * @param y point Y
     * @return transformed X
     */
    public float transformX(float x, float y) {
        return m00 * x + m01 * y + m02;
    }

    /**
     * Transforms Y of a point.
     *
     * @param x point X
     * @param y point Y
     * @return transformed Y
     */
    public float transformY(float x, float y) {
        return m10 * x + m11 * y + m12;
    }

    /**
     * Transforms a point.
     *
     * @param point the point
     * @return the transformed point
     */
    public Vec2 apply(Vec2 point) {
        return new Vec2(transformX(point.x(), point.y()), transformY(point.x(), point.y()));
    }

    /**
     * Returns the average scale, used to size anti-aliasing fringes.
     *
     * @return {@code sqrt(|determinant|)}
     */
    public float averageScale() {
        return (float) Math.sqrt(Math.abs(determinant()));
    }

    /**
     * Returns whether the transform has no rotation or shear.
     *
     * @return {@code true} if axis-aligned
     */
    public boolean isAxisAligned() {
        return m01 == 0f && m10 == 0f;
    }

    @Override
    public String toString() {
        return "[" + m00 + ", " + m01 + ", " + m02 + "; " + m10 + ", " + m11 + ", " + m12 + "]";
    }
}
