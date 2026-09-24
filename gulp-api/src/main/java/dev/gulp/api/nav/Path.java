package dev.gulp.api.nav;

import dev.gulp.api.math.Curve;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.world.MapObject;
import java.util.ArrayList;
import java.util.List;

/**
 * A polyline in world units with positions by distance: the result of path finding, or a route drawn in a map or built
 * from a curve, followed by {@link PathFollower} or {@link NavAgent}.
 *
 * <pre>{@code
 * Path patrol = Path.of(new Vec2(2, 5), new Vec2(9, 5), new Vec2(9, 1));
 * Vec2 halfway = patrol.pointAt(patrol.length() / 2);
 * Path rail = Path.fromObject(map.object("rail"));
 * }</pre>
 */
public final class Path {

    /** A path without points. */
    public static final Path EMPTY = new Path(List.of());

    private final List<Vec2> points;
    private final float[] cumulative;

    private Path(List<Vec2> points) {
        this.points = List.copyOf(points);
        cumulative = new float[this.points.size()];
        for (int i = 1; i < cumulative.length; i++) {
            cumulative[i] = cumulative[i - 1] + this.points.get(i - 1).distanceTo(this.points.get(i));
        }
    }

    /**
     * Returns a path through the given points.
     *
     * @param points the points
     * @return the path
     */
    public static Path of(Vec2... points) {
        return new Path(List.of(points));
    }

    /**
     * Returns a path through the given points.
     *
     * @param points the points
     * @return the path
     */
    public static Path of(List<Vec2> points) {
        return new Path(points);
    }

    /**
     * Samples a curve into a path.
     *
     * @param curve the curve
     * @param segments how many straight pieces, at least 1
     * @return the path
     */
    public static Path fromCurve(Curve curve, int segments) {
        int count = Math.max(1, segments);
        List<Vec2> points = new ArrayList<>(count + 1);
        for (int i = 0; i <= count; i++) {
            points.add(curve.atFraction(i / (float) count));
        }
        return new Path(points);
    }

    /**
     * Returns the polyline of a map object, or the outline of its rectangle when it has no points.
     *
     * @param object the object
     * @return the path
     */
    public static Path fromObject(MapObject object) {
        if (!object.points().isEmpty()) {
            return new Path(object.points());
        }
        float left = object.x() - object.width() / 2f;
        float top = object.y() - object.height() / 2f;
        float right = left + object.width();
        float bottom = top + object.height();
        return new Path(List.of(
                new Vec2(left, top),
                new Vec2(right, top),
                new Vec2(right, bottom),
                new Vec2(left, bottom),
                new Vec2(left, top)));
    }

    /**
     * Returns the points.
     *
     * @return the points
     */
    public List<Vec2> points() {
        return points;
    }

    /**
     * Returns whether the path has no points.
     *
     * @return {@code true} if empty
     */
    public boolean isEmpty() {
        return points.isEmpty();
    }

    /**
     * Returns the first point.
     *
     * @return the start
     * @throws IllegalStateException if empty
     */
    public Vec2 start() {
        requirePoints();
        return points.get(0);
    }

    /**
     * Returns the last point.
     *
     * @return the end
     * @throws IllegalStateException if empty
     */
    public Vec2 end() {
        requirePoints();
        return points.get(points.size() - 1);
    }

    /**
     * Returns the total length.
     *
     * @return world units
     */
    public float length() {
        return cumulative.length == 0 ? 0f : cumulative[cumulative.length - 1];
    }

    /**
     * Returns the point at a distance from the start.
     *
     * @param distance clamped to {@code 0..length()}
     * @return the point
     * @throws IllegalStateException if empty
     */
    public Vec2 pointAt(float distance) {
        requirePoints();
        if (points.size() == 1 || distance <= 0f) {
            return points.get(0);
        }
        if (distance >= length()) {
            return end();
        }
        int i = segmentAt(distance);
        float span = cumulative[i + 1] - cumulative[i];
        float t = span <= 0f ? 0f : (distance - cumulative[i]) / span;
        return points.get(i).lerp(points.get(i + 1), t);
    }

    /**
     * Returns the direction of travel at a distance from the start.
     *
     * @param distance clamped to {@code 0..length()}
     * @return a unit vector, or {@link Vec2#ZERO} for paths shorter than two distinct points
     */
    public Vec2 directionAt(float distance) {
        if (points.size() < 2) {
            return Vec2.ZERO;
        }
        int i = segmentAt(Math.max(0f, Math.min(length(), distance)));
        for (int k = i; k < points.size() - 1; k++) {
            Vec2 d = points.get(k + 1).sub(points.get(k));
            if (d.lengthSquared() > 0f) {
                return d.normalized();
            }
        }
        return Vec2.ZERO;
    }

    /**
     * Returns the distance along the path of the point closest to a position.
     *
     * @param position any point
     * @return the distance from the start of the closest point on the path
     */
    public float project(Vec2 position) {
        if (points.size() < 2) {
            return 0f;
        }
        float best = Float.MAX_VALUE;
        float along = 0f;
        for (int i = 0; i < points.size() - 1; i++) {
            Vec2 a = points.get(i);
            Vec2 ab = points.get(i + 1).sub(a);
            float lengthSquared = ab.lengthSquared();
            float t = lengthSquared == 0f
                    ? 0f
                    : Math.max(0f, Math.min(1f, position.sub(a).dot(ab) / lengthSquared));
            Vec2 closest = a.add(ab.scale(t));
            float d = closest.distanceSquaredTo(position);
            if (d < best) {
                best = d;
                along = cumulative[i] + (cumulative[i + 1] - cumulative[i]) * t;
            }
        }
        return along;
    }

    /**
     * Returns the path backwards.
     *
     * @return a new path
     */
    public Path reversed() {
        List<Vec2> copy = new ArrayList<>(points);
        java.util.Collections.reverse(copy);
        return new Path(copy);
    }

    private int segmentAt(float distance) {
        int low = 0;
        int high = cumulative.length - 2;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (cumulative[mid] <= distance) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return Math.max(0, low);
    }

    private void requirePoints() {
        if (points.isEmpty()) {
            throw new IllegalStateException("The path is empty");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Path path && path.points.equals(points);
    }

    @Override
    public int hashCode() {
        return points.hashCode();
    }

    @Override
    public String toString() {
        return "Path" + points;
    }
}
