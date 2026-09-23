package dev.gulp.api.math;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Intersection tests between points, segments, rectangles, circles and polygons.
 *
 * <pre>{@code
 * if (Intersect.circleRect(explosion, crate.bounds())) crate.breakApart();
 * Vec2 hit = Intersect.segments(ray, wall);            // null when they do not cross
 * Vec2 push = Intersect.sat(playerShape, wallShape);   // minimum translation to separate, or null
 * }</pre>
 */
public final class Intersect {

    private Intersect() {}

    /**
     * Point inside a rectangle.
     *
     * @param point the point
     * @param rect the rectangle
     * @return {@code true} if inside
     */
    public static boolean pointRect(Vec2 point, Rect rect) {
        return rect.contains(point);
    }

    /**
     * Point inside a circle.
     *
     * @param point the point
     * @param circle the circle
     * @return {@code true} if inside
     */
    public static boolean pointCircle(Vec2 point, Circle circle) {
        return circle.contains(point);
    }

    /**
     * Point inside a polygon.
     *
     * @param point the point
     * @param polygon the polygon
     * @return {@code true} if inside
     */
    public static boolean pointPolygon(Vec2 point, Polygon polygon) {
        return polygon.contains(point);
    }

    /**
     * Two rectangles overlap.
     *
     * @param a first rectangle
     * @param b second rectangle
     * @return {@code true} if they overlap
     */
    public static boolean rects(Rect a, Rect b) {
        return a.overlaps(b);
    }

    /**
     * Two circles overlap.
     *
     * @param a first circle
     * @param b second circle
     * @return {@code true} if they overlap
     */
    public static boolean circles(Circle a, Circle b) {
        return a.overlaps(b);
    }

    /**
     * A circle and a rectangle overlap.
     *
     * @param circle the circle
     * @param rect the rectangle
     * @return {@code true} if they overlap
     */
    public static boolean circleRect(Circle circle, Rect rect) {
        Vec2 closest = rect.clamp(circle.center());
        return closest.distanceSquaredTo(circle.center()) < circle.radius() * circle.radius();
    }

    /**
     * A segment and a circle touch.
     *
     * @param segment the segment
     * @param circle the circle
     * @return {@code true} if they touch
     */
    public static boolean segmentCircle(Segment segment, Circle circle) {
        return segment.distanceTo(circle.center()) <= circle.radius();
    }

    /**
     * A segment and a rectangle touch.
     *
     * @param segment the segment
     * @param rect the rectangle
     * @return {@code true} if they touch
     */
    public static boolean segmentRect(Segment segment, Rect rect) {
        if (rect.contains(segment.a()) || rect.contains(segment.b())) {
            return true;
        }
        Vec2 tl = new Vec2(rect.x(), rect.y());
        Vec2 tr = new Vec2(rect.right(), rect.y());
        Vec2 br = new Vec2(rect.right(), rect.bottom());
        Vec2 bl = new Vec2(rect.x(), rect.bottom());
        return segments(segment, new Segment(tl, tr)) != null
                || segments(segment, new Segment(tr, br)) != null
                || segments(segment, new Segment(br, bl)) != null
                || segments(segment, new Segment(bl, tl)) != null;
    }

    /**
     * Crossing point of two segments.
     *
     * @param first first segment
     * @param second second segment
     * @return the intersection point, or {@code null} if they do not cross (parallel segments give {@code null})
     */
    public static @Nullable Vec2 segments(Segment first, Segment second) {
        Vec2 p = first.a();
        Vec2 r = first.b().sub(p);
        Vec2 q = second.a();
        Vec2 s = second.b().sub(q);
        float denominator = r.cross(s);
        if (denominator == 0f) {
            return null;
        }
        Vec2 qp = q.sub(p);
        float t = qp.cross(s) / denominator;
        float u = qp.cross(r) / denominator;
        if (t < 0f || t > 1f || u < 0f || u > 1f) {
            return null;
        }
        return p.add(r.scale(t));
    }

    /**
     * A circle and a polygon overlap.
     *
     * @param circle the circle
     * @param polygon the polygon
     * @return {@code true} if they overlap
     */
    public static boolean circlePolygon(Circle circle, Polygon polygon) {
        if (polygon.contains(circle.center())) {
            return true;
        }
        List<Vec2> v = polygon.vertices();
        for (int i = 0; i < v.size(); i++) {
            if (segmentCircle(new Segment(v.get(i), v.get((i + 1) % v.size())), circle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Two polygons overlap (any shape: edge crossing or containment).
     *
     * @param a first polygon
     * @param b second polygon
     * @return {@code true} if they overlap
     */
    public static boolean polygons(Polygon a, Polygon b) {
        if (a.contains(b.vertices().getFirst()) || b.contains(a.vertices().getFirst())) {
            return true;
        }
        List<Vec2> va = a.vertices();
        List<Vec2> vb = b.vertices();
        for (int i = 0; i < va.size(); i++) {
            Segment ea = new Segment(va.get(i), va.get((i + 1) % va.size()));
            for (int j = 0; j < vb.size(); j++) {
                if (segments(ea, new Segment(vb.get(j), vb.get((j + 1) % vb.size()))) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Separating axis test for two convex polygons.
     *
     * @param a first convex polygon
     * @param b second convex polygon
     * @return the minimum translation that moves {@code a} out of {@code b}, or {@code null} if they do not overlap
     */
    public static @Nullable Vec2 sat(Polygon a, Polygon b) {
        float bestDepth = Float.MAX_VALUE;
        Vec2 bestAxis = null;
        for (Polygon shape : new Polygon[] {a, b}) {
            List<Vec2> v = shape.vertices();
            for (int i = 0; i < v.size(); i++) {
                Vec2 axis =
                        v.get((i + 1) % v.size()).sub(v.get(i)).perpendicular().normalized();
                if (axis.lengthSquared() == 0f) {
                    continue;
                }
                float[] pa = project(a, axis);
                float[] pb = project(b, axis);
                float overlap = Math.min(pa[1], pb[1]) - Math.max(pa[0], pb[0]);
                if (overlap <= 0f) {
                    return null;
                }
                if (overlap < bestDepth) {
                    bestDepth = overlap;
                    bestAxis = axis;
                }
            }
        }
        if (bestAxis == null) {
            return null;
        }
        Vec2 direction = a.centroid().sub(b.centroid());
        if (direction.dot(bestAxis) < 0f) {
            bestAxis = bestAxis.scale(-1f);
        }
        return bestAxis.scale(bestDepth);
    }

    private static float[] project(Polygon polygon, Vec2 axis) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (Vec2 v : polygon.vertices()) {
            float p = v.dot(axis);
            min = Math.min(min, p);
            max = Math.max(max, p);
        }
        return new float[] {min, max};
    }
}
