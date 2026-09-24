package dev.gulp.core.physics;

import dev.gulp.api.math.Vec2;
import dev.gulp.api.physics.Shape;
import java.util.ArrayList;
import java.util.List;

/** Turns API shapes into convex pieces; concave polygons are triangulated and the triangles merged back into convex parts. */
final class Shapes {

    private Shapes() {}

    static List<Convex> pieces(Shape shape, float offsetX, float offsetY) {
        List<Convex> result = new ArrayList<>();
        switch (shape) {
            case Shape.Box box -> {
                float hw = box.width() / 2f;
                float hh = box.height() / 2f;
                result.add(new Convex(
                        new float[] {offsetX - hw, offsetX + hw, offsetX + hw, offsetX - hw},
                        new float[] {offsetY - hh, offsetY - hh, offsetY + hh, offsetY + hh},
                        4,
                        0f));
            }
            case Shape.Circle circle ->
                result.add(new Convex(new float[] {offsetX}, new float[] {offsetY}, 1, circle.radius()));
            case Shape.Capsule capsule -> {
                float half = capsule.height() / 2f - capsule.radius();
                if (half <= 1e-5f) {
                    result.add(new Convex(new float[] {offsetX}, new float[] {offsetY}, 1, capsule.radius()));
                } else {
                    result.add(new Convex(
                            new float[] {offsetX, offsetX},
                            new float[] {offsetY - half, offsetY + half},
                            2,
                            capsule.radius()));
                }
            }
            case Shape.Polygon polygon -> {
                for (List<Vec2> part : convexParts(polygon.points())) {
                    result.add(polygonPiece(part, offsetX, offsetY));
                }
            }
            case Shape.Segment segment -> result.add(segmentPiece(segment.a(), segment.b(), offsetX, offsetY));
            case Shape.Chain chain -> {
                List<Vec2> points = chain.points();
                int segments = chain.loop() ? points.size() : points.size() - 1;
                for (int i = 0; i < segments; i++) {
                    Vec2 a = points.get(i);
                    Vec2 b = points.get((i + 1) % points.size());
                    if (!a.equals(b)) {
                        result.add(segmentPiece(a, b, offsetX, offsetY));
                    }
                }
            }
        }
        return result;
    }

    static List<Convex> box(float width, float height, float offsetX, float offsetY) {
        return pieces(new Shape.Box(Math.max(width, 1e-3f), Math.max(height, 1e-3f)), offsetX, offsetY);
    }

    private static Convex segmentPiece(Vec2 a, Vec2 b, float offsetX, float offsetY) {
        return new Convex(
                new float[] {a.x() + offsetX, b.x() + offsetX}, new float[] {a.y() + offsetY, b.y() + offsetY}, 2, 0f);
    }

    static Convex polygonPiece(List<Vec2> points, float offsetX, float offsetY) {
        float[] x = new float[points.size()];
        float[] y = new float[points.size()];
        for (int i = 0; i < x.length; i++) {
            x[i] = points.get(i).x() + offsetX;
            y[i] = points.get(i).y() + offsetY;
        }
        return new Convex(x, y, x.length, 0f);
    }

    /** Splits a simple polygon into convex parts of at most {@link Convex#MAX_VERTICES} vertices. */
    static List<List<Vec2>> convexParts(List<Vec2> outline) {
        List<Vec2> points = withoutDuplicates(outline);
        if (points.size() < 3) {
            throw new IllegalArgumentException("A polygon needs three distinct points");
        }
        if (selfIntersecting(points)) {
            throw new IllegalArgumentException("The polygon intersects itself and cannot be split");
        }
        if (signedArea(points) < 0f) {
            points = new ArrayList<>(points);
            java.util.Collections.reverse(points);
        }
        List<List<Vec2>> parts = new ArrayList<>();
        if (isConvex(points)) {
            parts.add(points);
        } else {
            parts = merge(triangulate(points));
        }
        List<List<Vec2>> limited = new ArrayList<>();
        for (List<Vec2> part : parts) {
            if (part.size() <= Convex.MAX_VERTICES) {
                limited.add(part);
                continue;
            }
            // Fan the large convex part into pieces sharing vertex 0.
            int start = 1;
            while (start < part.size() - 1) {
                int end = Math.min(part.size() - 1, start + Convex.MAX_VERTICES - 2);
                List<Vec2> fan = new ArrayList<>();
                fan.add(part.get(0));
                for (int i = start; i <= end; i++) {
                    fan.add(part.get(i));
                }
                limited.add(fan);
                start = end;
            }
        }
        return limited;
    }

    /** Whether two edges that do not share a vertex cross each other. */
    private static boolean selfIntersecting(List<Vec2> points) {
        int n = points.size();
        for (int i = 0; i < n; i++) {
            Vec2 a1 = points.get(i);
            Vec2 a2 = points.get((i + 1) % n);
            for (int j = i + 2; j < n; j++) {
                if (i == 0 && j == n - 1) {
                    continue;
                }
                Vec2 b1 = points.get(j);
                Vec2 b2 = points.get((j + 1) % n);
                float d1 = cross(a1, a2, b1);
                float d2 = cross(a1, a2, b2);
                float d3 = cross(b1, b2, a1);
                float d4 = cross(b1, b2, a2);
                if (((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) && ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Vec2> withoutDuplicates(List<Vec2> outline) {
        List<Vec2> points = new ArrayList<>();
        for (Vec2 p : outline) {
            if (points.isEmpty() || points.get(points.size() - 1).distanceSquaredTo(p) > 1e-10f) {
                points.add(p);
            }
        }
        while (points.size() > 1 && points.get(0).distanceSquaredTo(points.get(points.size() - 1)) <= 1e-10f) {
            points.remove(points.size() - 1);
        }
        return points;
    }

    static float signedArea(List<Vec2> points) {
        float area = 0f;
        for (int i = 0; i < points.size(); i++) {
            Vec2 a = points.get(i);
            Vec2 b = points.get((i + 1) % points.size());
            area += a.x() * b.y() - b.x() * a.y();
        }
        return area * 0.5f;
    }

    private static float cross(Vec2 o, Vec2 a, Vec2 b) {
        return (a.x() - o.x()) * (b.y() - o.y()) - (a.y() - o.y()) * (b.x() - o.x());
    }

    /** For positive winding: every turn is a left turn (or straight). */
    static boolean isConvex(List<Vec2> points) {
        for (int i = 0; i < points.size(); i++) {
            if (cross(points.get(i), points.get((i + 1) % points.size()), points.get((i + 2) % points.size()))
                    < -1e-7f) {
                return false;
            }
        }
        return true;
    }

    /** Ear clipping of a simple polygon with positive winding. */
    private static List<List<Vec2>> triangulate(List<Vec2> polygon) {
        List<Vec2> remaining = new ArrayList<>(polygon);
        List<List<Vec2>> triangles = new ArrayList<>();
        int guard = 0;
        while (remaining.size() > 3 && guard++ < 10_000) {
            int n = remaining.size();
            boolean clipped = false;
            for (int i = 0; i < n; i++) {
                Vec2 prev = remaining.get((i + n - 1) % n);
                Vec2 current = remaining.get(i);
                Vec2 next = remaining.get((i + 1) % n);
                if (cross(prev, current, next) <= 1e-9f) {
                    continue;
                }
                boolean inside = false;
                for (int k = 0; k < n && !inside; k++) {
                    Vec2 p = remaining.get(k);
                    if (p == prev || p == current || p == next) {
                        continue;
                    }
                    inside = cross(prev, current, p) >= 0f
                            && cross(current, next, p) >= 0f
                            && cross(next, prev, p) >= 0f;
                }
                if (!inside) {
                    triangles.add(List.of(prev, current, next));
                    remaining.remove(i);
                    clipped = true;
                    break;
                }
            }
            if (!clipped) {
                throw new IllegalArgumentException("The polygon intersects itself and cannot be split");
            }
        }
        triangles.add(new ArrayList<>(remaining));
        return triangles;
    }

    /** Merges neighbouring convex parts while the union stays convex and small enough (Hertel-Mehlhorn). */
    private static List<List<Vec2>> merge(List<List<Vec2>> triangles) {
        List<List<Vec2>> parts = new ArrayList<>(triangles);
        boolean merged = true;
        while (merged) {
            merged = false;
            outer:
            for (int i = 0; i < parts.size(); i++) {
                for (int j = i + 1; j < parts.size(); j++) {
                    List<Vec2> union = union(parts.get(i), parts.get(j));
                    if (union != null && union.size() <= Convex.MAX_VERTICES && isConvex(union)) {
                        parts.set(i, union);
                        parts.remove(j);
                        merged = true;
                        break outer;
                    }
                }
            }
        }
        return parts;
    }

    /** Joins two parts sharing an edge, or returns {@code null}. */
    private static List<Vec2> union(List<Vec2> a, List<Vec2> b) {
        for (int i = 0; i < a.size(); i++) {
            Vec2 a1 = a.get(i);
            Vec2 a2 = a.get((i + 1) % a.size());
            for (int j = 0; j < b.size(); j++) {
                Vec2 b1 = b.get(j);
                Vec2 b2 = b.get((j + 1) % b.size());
                if (a1.equals(b2) && a2.equals(b1)) {
                    List<Vec2> result = new ArrayList<>();
                    for (int k = 0; k < a.size(); k++) {
                        result.add(a.get((i + 1 + k) % a.size()));
                    }
                    // result starts at a2 and ends at a1; insert b's other vertices after a1.
                    for (int k = 2; k < b.size(); k++) {
                        result.add(b.get((j + k) % b.size()));
                    }
                    return result;
                }
            }
        }
        return null;
    }
}
