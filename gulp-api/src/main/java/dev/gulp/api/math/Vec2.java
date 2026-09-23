package dev.gulp.api.math;

/**
 * Immutable 2D vector. The Y axis points down, in the world and in the UI, so {@link #UP} is {@code (0, -1)}. Angles
 * are in degrees, measured from the +X axis towards +Y (clockwise on screen).
 *
 * <pre>{@code
 * Vec2 velocity = direction.normalized().scale(speed);
 * Vec2 next = position.add(velocity.scale(dt));
 * float distance = position.distanceTo(target);
 * }</pre>
 *
 * <p>For hot loops without allocation use {@link MutableVec2}.
 *
 * @param x the X component
 * @param y the Y component
 */
public record Vec2(float x, float y) {

    /** {@code (0, 0)}. */
    public static final Vec2 ZERO = new Vec2(0f, 0f);
    /** {@code (1, 1)}. */
    public static final Vec2 ONE = new Vec2(1f, 1f);
    /** {@code (0, -1)}: up on screen. */
    public static final Vec2 UP = new Vec2(0f, -1f);
    /** {@code (0, 1)}: down on screen. */
    public static final Vec2 DOWN = new Vec2(0f, 1f);
    /** {@code (-1, 0)}. */
    public static final Vec2 LEFT = new Vec2(-1f, 0f);
    /** {@code (1, 0)}. */
    public static final Vec2 RIGHT = new Vec2(1f, 0f);

    /**
     * Creates a vector.
     *
     * @param x the X component
     * @param y the Y component
     * @return the vector
     */
    public static Vec2 of(float x, float y) {
        return new Vec2(x, y);
    }

    /**
     * Creates a unit vector pointing at an angle.
     *
     * @param degrees the angle from +X towards +Y
     * @return the unit vector
     */
    public static Vec2 fromAngle(float degrees) {
        return new Vec2(Mathf.cosDeg(degrees), Mathf.sinDeg(degrees));
    }

    /**
     * Adds a vector.
     *
     * @param other the vector
     * @return the sum
     */
    public Vec2 add(Vec2 other) {
        return new Vec2(x + other.x, y + other.y);
    }

    /**
     * Adds components.
     *
     * @param dx added to X
     * @param dy added to Y
     * @return the sum
     */
    public Vec2 add(float dx, float dy) {
        return new Vec2(x + dx, y + dy);
    }

    /**
     * Subtracts a vector.
     *
     * @param other the vector
     * @return the difference
     */
    public Vec2 sub(Vec2 other) {
        return new Vec2(x - other.x, y - other.y);
    }

    /**
     * Multiplies by a scalar.
     *
     * @param factor the scalar
     * @return the scaled vector
     */
    public Vec2 scale(float factor) {
        return new Vec2(x * factor, y * factor);
    }

    /**
     * Multiplies component-wise.
     *
     * @param sx X factor
     * @param sy Y factor
     * @return the scaled vector
     */
    public Vec2 scale(float sx, float sy) {
        return new Vec2(x * sx, y * sy);
    }

    /**
     * Dot product.
     *
     * @param other the vector
     * @return the dot product
     */
    public float dot(Vec2 other) {
        return x * other.x + y * other.y;
    }

    /**
     * 2D cross product (Z component of the 3D cross product).
     *
     * @param other the vector
     * @return {@code x * other.y - y * other.x}
     */
    public float cross(Vec2 other) {
        return x * other.y - y * other.x;
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
     * Returns the squared length, cheaper for comparisons.
     *
     * @return the squared length
     */
    public float lengthSquared() {
        return x * x + y * y;
    }

    /**
     * Returns a unit vector in the same direction.
     *
     * @return the normalized vector, or {@link #ZERO} for a zero vector
     */
    public Vec2 normalized() {
        float length = length();
        return length == 0f ? ZERO : new Vec2(x / length, y / length);
    }

    /**
     * Replaces X.
     *
     * @param newX the X component
     * @return the new vector
     */
    public Vec2 withX(float newX) {
        return new Vec2(newX, y);
    }

    /**
     * Replaces Y.
     *
     * @param newY the Y component
     * @return the new vector
     */
    public Vec2 withY(float newY) {
        return new Vec2(x, newY);
    }

    /**
     * Distance to a point.
     *
     * @param other the point
     * @return the distance
     */
    public float distanceTo(Vec2 other) {
        float dx = other.x - x;
        float dy = other.y - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Squared distance to a point.
     *
     * @param other the point
     * @return the squared distance
     */
    public float distanceSquaredTo(Vec2 other) {
        float dx = other.x - x;
        float dy = other.y - y;
        return dx * dx + dy * dy;
    }

    /**
     * Unit vector pointing at a point.
     *
     * @param other the point
     * @return the direction, or {@link #ZERO} if the points coincide
     */
    public Vec2 directionTo(Vec2 other) {
        return other.sub(this).normalized();
    }

    /**
     * Angle of this vector.
     *
     * @return degrees from +X towards +Y, {@code -180..180}
     */
    public float angle() {
        return Mathf.radToDeg((float) Math.atan2(y, x));
    }

    /**
     * Signed angle from this vector to another.
     *
     * @param other the vector
     * @return degrees, {@code -180..180}
     */
    public float angleTo(Vec2 other) {
        return Mathf.radToDeg((float) Math.atan2(cross(other), dot(other)));
    }

    /**
     * Rotates by an angle.
     *
     * @param degrees the angle, positive towards +Y
     * @return the rotated vector
     */
    public Vec2 rotated(float degrees) {
        float cos = Mathf.cosDeg(degrees);
        float sin = Mathf.sinDeg(degrees);
        return new Vec2(x * cos - y * sin, x * sin + y * cos);
    }

    /**
     * Interpolates towards a vector.
     *
     * @param to the target
     * @param t the factor
     * @return the interpolated vector
     */
    public Vec2 lerp(Vec2 to, float t) {
        return new Vec2(x + (to.x - x) * t, y + (to.y - y) * t);
    }

    /**
     * Moves towards a point by at most a distance.
     *
     * @param target the point
     * @param maxDelta the largest distance to move
     * @return the new position, never overshooting
     */
    public Vec2 moveToward(Vec2 target, float maxDelta) {
        float distance = distanceTo(target);
        if (distance <= maxDelta || distance == 0f) {
            return target;
        }
        float t = maxDelta / distance;
        return lerp(target, t);
    }

    /**
     * Limits the length.
     *
     * @param maxLength the largest length
     * @return this vector or a shortened copy
     */
    public Vec2 clampLength(float maxLength) {
        float lengthSquared = lengthSquared();
        if (lengthSquared <= maxLength * maxLength) {
            return this;
        }
        return scale(maxLength / (float) Math.sqrt(lengthSquared));
    }

    /**
     * Perpendicular vector, rotated 90 degrees towards +Y.
     *
     * @return {@code (-y, x)}
     */
    public Vec2 perpendicular() {
        return new Vec2(-y, x);
    }

    /**
     * Reflects off a surface.
     *
     * @param normal the unit surface normal
     * @return the reflected vector
     */
    public Vec2 reflect(Vec2 normal) {
        float d = 2f * dot(normal);
        return new Vec2(x - d * normal.x, y - d * normal.y);
    }

    /**
     * Rounds both components to multiples of a step.
     *
     * @param step the step
     * @return the snapped vector
     */
    public Vec2 snapped(float step) {
        return new Vec2(Mathf.snapped(x, step), Mathf.snapped(y, step));
    }

    /**
     * Returns whether both components are within {@link Mathf#EPSILON} of another vector.
     *
     * @param other the vector
     * @return {@code true} if nearly equal
     */
    public boolean nearlyEquals(Vec2 other) {
        return Mathf.nearlyEqual(x, other.x) && Mathf.nearlyEqual(y, other.y);
    }

    /**
     * Returns a mutable copy.
     *
     * @return the copy
     */
    public MutableVec2 toMutable() {
        return new MutableVec2(x, y);
    }

    /**
     * Returns {@code (x, y)}.
     *
     * @return the text form
     */
    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
