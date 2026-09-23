package dev.gulp.api.math;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple polygon given by its vertices in order, with an optional transform applied by {@link #transformed}.
 *
 * <pre>{@code
 * Polygon triangle = Polygon.of(0, 0, 2, 0, 1, 2);
 * Polygon placed = triangle.transformed(Transform2D.of(position, 45f, Vec2.ONE));
 * if (placed.contains(point)) ...
 * }</pre>
 *
 * @param vertices the vertices, at least three
 */
public record Polygon(List<Vec2> vertices) {

    /**
     * Copies and validates the vertices.
     *
     * @throws IllegalArgumentException if there are fewer than three
     */
    public Polygon {
        vertices = List.copyOf(vertices);
        if (vertices.size() < 3) {
            throw new IllegalArgumentException("A polygon needs at least three vertices, got " + vertices.size());
        }
    }

    /**
     * Creates a polygon from coordinate pairs.
     *
     * @param coordinates {@code x0, y0, x1, y1, ...}
     * @return the polygon
     * @throws IllegalArgumentException if the count is odd or below six
     */
    public static Polygon of(float... coordinates) {
        if (coordinates.length % 2 != 0) {
            throw new IllegalArgumentException("Coordinates come in pairs");
        }
        List<Vec2> points = new ArrayList<>();
        for (int i = 0; i < coordinates.length; i += 2) {
            points.add(new Vec2(coordinates[i], coordinates[i + 1]));
        }
        return new Polygon(points);
    }

    /**
     * Signed area: positive when vertices go clockwise on screen (Y down).
     *
     * @return the signed area
     */
    public float signedArea() {
        float sum = 0f;
        for (int i = 0, n = vertices.size(); i < n; i++) {
            Vec2 a = vertices.get(i);
            Vec2 b = vertices.get((i + 1) % n);
            sum += a.x() * b.y() - b.x() * a.y();
        }
        return sum / 2f;
    }

    /**
     * Area.
     *
     * @return the absolute area
     */
    public float area() {
        return Math.abs(signedArea());
    }

    /**
     * Center of mass.
     *
     * @return the centroid
     */
    public Vec2 centroid() {
        float area = signedArea();
        if (area == 0f) {
            float sx = 0f;
            float sy = 0f;
            for (Vec2 v : vertices) {
                sx += v.x();
                sy += v.y();
            }
            return new Vec2(sx / vertices.size(), sy / vertices.size());
        }
        float cx = 0f;
        float cy = 0f;
        for (int i = 0, n = vertices.size(); i < n; i++) {
            Vec2 a = vertices.get(i);
            Vec2 b = vertices.get((i + 1) % n);
            float cross = a.x() * b.y() - b.x() * a.y();
            cx += (a.x() + b.x()) * cross;
            cy += (a.y() + b.y()) * cross;
        }
        return new Vec2(cx / (6f * area), cy / (6f * area));
    }

    /**
     * Returns whether a point is inside (even-odd rule).
     *
     * @param point the point
     * @return {@code true} if inside
     */
    public boolean contains(Vec2 point) {
        boolean inside = false;
        for (int i = 0, j = vertices.size() - 1; i < vertices.size(); j = i++) {
            Vec2 a = vertices.get(i);
            Vec2 b = vertices.get(j);
            if ((a.y() > point.y()) != (b.y() > point.y())
                    && point.x() < (b.x() - a.x()) * (point.y() - a.y()) / (b.y() - a.y()) + a.x()) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * Returns whether the polygon is convex.
     *
     * @return {@code true} if convex
     */
    public boolean isConvex() {
        int sign = 0;
        for (int i = 0, n = vertices.size(); i < n; i++) {
            Vec2 a = vertices.get(i);
            Vec2 b = vertices.get((i + 1) % n);
            Vec2 c = vertices.get((i + 2) % n);
            float cross = b.sub(a).cross(c.sub(b));
            if (cross != 0f) {
                int s = cross > 0 ? 1 : -1;
                if (sign != 0 && s != sign) {
                    return false;
                }
                sign = s;
            }
        }
        return true;
    }

    /**
     * Bounding rectangle.
     *
     * @return the bounds
     */
    public Rect bounds() {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (Vec2 v : vertices) {
            minX = Math.min(minX, v.x());
            minY = Math.min(minY, v.y());
            maxX = Math.max(maxX, v.x());
            maxY = Math.max(maxY, v.y());
        }
        return new Rect(minX, minY, maxX - minX, maxY - minY);
    }

    /**
     * Applies a transform to every vertex.
     *
     * @param transform the transform
     * @return the transformed polygon
     */
    public Polygon transformed(Transform2D transform) {
        Affine2 matrix = transform.toAffine();
        List<Vec2> points = new ArrayList<>(vertices.size());
        for (Vec2 v : vertices) {
            points.add(matrix.apply(v));
        }
        return new Polygon(points);
    }
}
