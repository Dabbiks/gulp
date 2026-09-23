package dev.gulp.api.math;

/**
 * Mutable 2D vector for hot loops: every operation changes this instance and returns it, without allocating.
 *
 * <pre>{@code
 * MutableVec2 position = new MutableVec2();
 * position.set(start).add(velocity.x() * dt, velocity.y() * dt).clampLength(100f);
 * Vec2 snapshot = position.toVec2();
 * }</pre>
 */
public final class MutableVec2 {

    /** X component. */
    public float x;
    /** Y component. */
    public float y;

    /** Creates {@code (0, 0)}. */
    public MutableVec2() {}

    /**
     * Creates a vector.
     *
     * @param x the X component
     * @param y the Y component
     */
    public MutableVec2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Sets both components.
     *
     * @param x the X component
     * @param y the Y component
     * @return this vector
     */
    public MutableVec2 set(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    /**
     * Copies a vector.
     *
     * @param other the vector
     * @return this vector
     */
    public MutableVec2 set(Vec2 other) {
        return set(other.x(), other.y());
    }

    /**
     * Copies a vector.
     *
     * @param other the vector
     * @return this vector
     */
    public MutableVec2 set(MutableVec2 other) {
        return set(other.x, other.y);
    }

    /**
     * Adds components.
     *
     * @param dx added to X
     * @param dy added to Y
     * @return this vector
     */
    public MutableVec2 add(float dx, float dy) {
        x += dx;
        y += dy;
        return this;
    }

    /**
     * Adds a vector.
     *
     * @param other the vector
     * @return this vector
     */
    public MutableVec2 add(Vec2 other) {
        return add(other.x(), other.y());
    }

    /**
     * Subtracts components.
     *
     * @param dx subtracted from X
     * @param dy subtracted from Y
     * @return this vector
     */
    public MutableVec2 sub(float dx, float dy) {
        x -= dx;
        y -= dy;
        return this;
    }

    /**
     * Multiplies by a scalar.
     *
     * @param factor the scalar
     * @return this vector
     */
    public MutableVec2 scale(float factor) {
        x *= factor;
        y *= factor;
        return this;
    }

    /**
     * Returns the length.
     *
     * @return the length
     */
    public float length() {
        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * Returns the squared length.
     *
     * @return the squared length
     */
    public float lengthSquared() {
        return x * x + y * y;
    }

    /**
     * Dot product with components.
     *
     * @param ox other X
     * @param oy other Y
     * @return the dot product
     */
    public float dot(float ox, float oy) {
        return x * ox + y * oy;
    }

    /**
     * Makes the length 1; a zero vector stays zero.
     *
     * @return this vector
     */
    public MutableVec2 normalize() {
        float length = length();
        if (length != 0f) {
            x /= length;
            y /= length;
        }
        return this;
    }

    /**
     * Rotates by an angle.
     *
     * @param degrees the angle, positive towards +Y
     * @return this vector
     */
    public MutableVec2 rotate(float degrees) {
        float cos = Mathf.cosDeg(degrees);
        float sin = Mathf.sinDeg(degrees);
        float nx = x * cos - y * sin;
        y = x * sin + y * cos;
        x = nx;
        return this;
    }

    /**
     * Interpolates towards a point.
     *
     * @param tx target X
     * @param ty target Y
     * @param t the factor
     * @return this vector
     */
    public MutableVec2 lerp(float tx, float ty, float t) {
        x += (tx - x) * t;
        y += (ty - y) * t;
        return this;
    }

    /**
     * Moves towards a point by at most a distance.
     *
     * @param tx target X
     * @param ty target Y
     * @param maxDelta the largest distance to move
     * @return this vector
     */
    public MutableVec2 moveToward(float tx, float ty, float maxDelta) {
        float dx = tx - x;
        float dy = ty - y;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance <= maxDelta || distance == 0f) {
            return set(tx, ty);
        }
        return add(dx / distance * maxDelta, dy / distance * maxDelta);
    }

    /**
     * Limits the length.
     *
     * @param maxLength the largest length
     * @return this vector
     */
    public MutableVec2 clampLength(float maxLength) {
        float lengthSquared = lengthSquared();
        if (lengthSquared > maxLength * maxLength) {
            scale(maxLength / (float) Math.sqrt(lengthSquared));
        }
        return this;
    }

    /**
     * Returns an immutable copy.
     *
     * @return the vector
     */
    public Vec2 toVec2() {
        return new Vec2(x, y);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
