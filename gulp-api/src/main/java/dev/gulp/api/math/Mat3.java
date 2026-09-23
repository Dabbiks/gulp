package dev.gulp.api.math;

/**
 * Immutable 3x3 matrix for 2D homogeneous transforms and projections, in row notation ({@code mRowColumn}).
 *
 * <pre>{@code
 * Mat3 model = Mat3.translation(10, 5).mul(Mat3.rotation(45)).mul(Mat3.scaling(2, 2));
 * Vec2 p = model.apply(Vec2.ONE);
 * Mat3 projection = Mat3.orthographic(0, 640, 0, 360);   // Y down: top edge 0, bottom edge 360
 * }</pre>
 *
 * @param m00 row 0, column 0
 * @param m01 row 0, column 1
 * @param m02 row 0, column 2
 * @param m10 row 1, column 0
 * @param m11 row 1, column 1
 * @param m12 row 1, column 2
 * @param m20 row 2, column 0
 * @param m21 row 2, column 1
 * @param m22 row 2, column 2
 */
public record Mat3(float m00, float m01, float m02, float m10, float m11, float m12, float m20, float m21, float m22) {

    /** The identity matrix. */
    public static final Mat3 IDENTITY = new Mat3(1, 0, 0, 0, 1, 0, 0, 0, 1);

    /**
     * Translation.
     *
     * @param x offset X
     * @param y offset Y
     * @return the matrix
     */
    public static Mat3 translation(float x, float y) {
        return new Mat3(1, 0, x, 0, 1, y, 0, 0, 1);
    }

    /**
     * Rotation.
     *
     * @param degrees the angle, positive towards +Y
     * @return the matrix
     */
    public static Mat3 rotation(float degrees) {
        float c = Mathf.cosDeg(degrees);
        float s = Mathf.sinDeg(degrees);
        return new Mat3(c, -s, 0, s, c, 0, 0, 0, 1);
    }

    /**
     * Scaling.
     *
     * @param sx scale X
     * @param sy scale Y
     * @return the matrix
     */
    public static Mat3 scaling(float sx, float sy) {
        return new Mat3(sx, 0, 0, 0, sy, 0, 0, 0, 1);
    }

    /**
     * Orthographic projection from a rectangle to clip space {@code -1..1}, with the top edge mapped to +1.
     *
     * @param left X of the left edge
     * @param right X of the right edge
     * @param top Y of the top edge
     * @param bottom Y of the bottom edge
     * @return the projection
     */
    public static Mat3 orthographic(float left, float right, float top, float bottom) {
        float sx = 2f / (right - left);
        float sy = 2f / (top - bottom);
        return new Mat3(sx, 0, -(right + left) / (right - left), 0, sy, -(top + bottom) / (top - bottom), 0, 0, 1);
    }

    /**
     * Converts an affine transform.
     *
     * @param affine the transform
     * @return the matrix
     */
    public static Mat3 of(Affine2 affine) {
        return new Mat3(affine.m00, affine.m01, affine.m02, affine.m10, affine.m11, affine.m12, 0, 0, 1);
    }

    /**
     * Matrix product {@code this * other}: applies {@code other} first.
     *
     * @param o the other matrix
     * @return the product
     */
    public Mat3 mul(Mat3 o) {
        return new Mat3(
                m00 * o.m00 + m01 * o.m10 + m02 * o.m20,
                m00 * o.m01 + m01 * o.m11 + m02 * o.m21,
                m00 * o.m02 + m01 * o.m12 + m02 * o.m22,
                m10 * o.m00 + m11 * o.m10 + m12 * o.m20,
                m10 * o.m01 + m11 * o.m11 + m12 * o.m21,
                m10 * o.m02 + m11 * o.m12 + m12 * o.m22,
                m20 * o.m00 + m21 * o.m10 + m22 * o.m20,
                m20 * o.m01 + m21 * o.m11 + m22 * o.m21,
                m20 * o.m02 + m21 * o.m12 + m22 * o.m22);
    }

    /**
     * Determinant.
     *
     * @return the determinant
     */
    public float determinant() {
        return m00 * (m11 * m22 - m12 * m21) - m01 * (m10 * m22 - m12 * m20) + m02 * (m10 * m21 - m11 * m20);
    }

    /**
     * Inverse.
     *
     * @return the inverse
     * @throws IllegalStateException if the matrix is singular
     */
    public Mat3 inverse() {
        float det = determinant();
        if (det == 0f) {
            throw new IllegalStateException("Matrix is not invertible");
        }
        float i = 1f / det;
        return new Mat3(
                (m11 * m22 - m12 * m21) * i,
                (m02 * m21 - m01 * m22) * i,
                (m01 * m12 - m02 * m11) * i,
                (m12 * m20 - m10 * m22) * i,
                (m00 * m22 - m02 * m20) * i,
                (m02 * m10 - m00 * m12) * i,
                (m10 * m21 - m11 * m20) * i,
                (m01 * m20 - m00 * m21) * i,
                (m00 * m11 - m01 * m10) * i);
    }

    /**
     * Transforms a point (w = 1).
     *
     * @param point the point
     * @return the transformed point
     */
    public Vec2 apply(Vec2 point) {
        float w = m20 * point.x() + m21 * point.y() + m22;
        return new Vec2((m00 * point.x() + m01 * point.y() + m02) / w, (m10 * point.x() + m11 * point.y() + m12) / w);
    }

    /**
     * Writes the matrix in column-major order, as GLSL expects.
     *
     * @param out an array of at least nine floats
     * @return {@code out}
     */
    public float[] toColumnMajor(float[] out) {
        out[0] = m00;
        out[1] = m10;
        out[2] = m20;
        out[3] = m01;
        out[4] = m11;
        out[5] = m21;
        out[6] = m02;
        out[7] = m12;
        out[8] = m22;
        return out;
    }
}
