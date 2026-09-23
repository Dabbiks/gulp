package dev.gulp.api.math;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Polygon algorithms: ear-clipping triangulation, convex hull, line simplification and convex decomposition.
 *
 * <pre>{@code
 * int[] triangles = Geometry.triangulate(level.outline());       // indices, three per triangle
 * List<Vec2> hull = Geometry.convexHull(points);
 * List<Vec2> simpler = Geometry.simplify(path, 0.1f);
 * List<Polygon> convexParts = Geometry.decompose(concave);
 * }</pre>
 */
public final class Geometry {

    private Geometry() {}

    /**
     * Triangulates a simple polygon by ear clipping.
     *
     * @param polygon the polygon, in either winding
     * @return vertex indices, three per triangle; {@code (n - 2) * 3} values
     */
    public static int[] triangulate(Polygon polygon) {
        List<Vec2> v = polygon.vertices();
        int n = v.size();
        List<Integer> remaining = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            remaining.add(i);
        }
        float winding = Math.signum(polygon.signedArea());
        if (winding == 0f) {
            winding = 1f;
        }
        int[] result = new int[(n - 2) * 3];
        int out = 0;
        int guard = 0;
        while (remaining.size() > 3 && guard++ < n * n) {
            boolean clipped = false;
            for (int i = 0; i < remaining.size(); i++) {
                int prev = remaining.get((i + remaining.size() - 1) % remaining.size());
                int curr = remaining.get(i);
                int next = remaining.get((i + 1) % remaining.size());
                if (isEar(v, remaining, prev, curr, next, winding)) {
                    result[out++] = prev;
                    result[out++] = curr;
                    result[out++] = next;
                    remaining.remove(i);
                    clipped = true;
                    break;
                }
            }
            if (!clipped) {
                // Degenerate input: clip the first vertex to make progress.
                result[out++] = remaining.get(remaining.size() - 1);
                result[out++] = remaining.get(0);
                result[out++] = remaining.get(1);
                remaining.remove(0);
            }
        }
        result[out++] = remaining.get(0);
        result[out++] = remaining.get(1);
        result[out] = remaining.get(2);
        return result;
    }

    private static boolean isEar(List<Vec2> v, List<Integer> remaining, int prev, int curr, int next, float winding) {
        Vec2 a = v.get(prev);
        Vec2 b = v.get(curr);
        Vec2 c = v.get(next);
        if (b.sub(a).cross(c.sub(b)) * winding <= 0f) {
            return false;
        }
        for (int index : remaining) {
            if (index == prev || index == curr || index == next) {
                continue;
            }
            if (inTriangle(v.get(index), a, b, c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean inTriangle(Vec2 p, Vec2 a, Vec2 b, Vec2 c) {
        float d1 = b.sub(a).cross(p.sub(a));
        float d2 = c.sub(b).cross(p.sub(b));
        float d3 = a.sub(c).cross(p.sub(c));
        boolean negative = d1 < 0 || d2 < 0 || d3 < 0;
        boolean positive = d1 > 0 || d2 > 0 || d3 > 0;
        return !(negative && positive);
    }

    /**
     * Convex hull (monotone chain).
     *
     * @param points the points
     * @return hull vertices in order, without collinear points; fewer than three for degenerate input
     */
    public static List<Vec2> convexHull(List<Vec2> points) {
        List<Vec2> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingDouble(Vec2::x).thenComparingDouble(Vec2::y));
        if (sorted.size() < 3) {
            return sorted;
        }
        List<Vec2> hull = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            int start = hull.size();
            for (Vec2 p : sorted) {
                while (hull.size() >= start + 2
                        && hull.get(hull.size() - 1)
                                        .sub(hull.get(hull.size() - 2))
                                        .cross(p.sub(hull.get(hull.size() - 1)))
                                <= 0f) {
                    hull.removeLast();
                }
                hull.add(p);
            }
            hull.removeLast();
            java.util.Collections.reverse(sorted);
        }
        return hull;
    }

    /**
     * Simplifies a polyline (Ramer-Douglas-Peucker).
     *
     * @param points the polyline
     * @param tolerance the largest allowed distance from the original line
     * @return the simplified polyline, keeping the end points
     */
    public static List<Vec2> simplify(List<Vec2> points, float tolerance) {
        if (points.size() < 3) {
            return List.copyOf(points);
        }
        boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[points.size() - 1] = true;
        simplify(points, 0, points.size() - 1, tolerance, keep);
        List<Vec2> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (keep[i]) {
                result.add(points.get(i));
            }
        }
        return result;
    }

    private static void simplify(List<Vec2> points, int first, int last, float tolerance, boolean[] keep) {
        Segment segment = new Segment(points.get(first), points.get(last));
        float worst = -1f;
        int index = -1;
        for (int i = first + 1; i < last; i++) {
            float distance = segment.distanceTo(points.get(i));
            if (distance > worst) {
                worst = distance;
                index = i;
            }
        }
        if (index >= 0 && worst > tolerance) {
            keep[index] = true;
            simplify(points, first, index, tolerance, keep);
            simplify(points, index, last, tolerance, keep);
        }
    }

    /**
     * Splits a simple polygon into convex parts: triangulates it, then merges neighbouring pieces while they stay
     * convex (Hertel-Mehlhorn).
     *
     * @param polygon the polygon
     * @return convex polygons covering it
     */
    public static List<Polygon> decompose(Polygon polygon) {
        if (polygon.isConvex()) {
            return List.of(polygon);
        }
        List<Vec2> v = polygon.vertices();
        int[] triangles = triangulate(polygon);
        List<List<Integer>> parts = new ArrayList<>();
        for (int i = 0; i < triangles.length; i += 3) {
            parts.add(new ArrayList<>(List.of(triangles[i], triangles[i + 1], triangles[i + 2])));
        }
        boolean merged = true;
        while (merged) {
            merged = false;
            outer:
            for (int i = 0; i < parts.size(); i++) {
                for (int j = i + 1; j < parts.size(); j++) {
                    List<Integer> joined = join(parts.get(i), parts.get(j));
                    if (joined != null && toPolygon(v, joined).isConvex()) {
                        parts.set(i, joined);
                        parts.remove(j);
                        merged = true;
                        break outer;
                    }
                }
            }
        }
        List<Polygon> result = new ArrayList<>();
        for (List<Integer> part : parts) {
            result.add(toPolygon(v, part));
        }
        return result;
    }

    /** Joins two index loops that share an edge; {@code null} if they do not. */
    private static List<Integer> join(List<Integer> a, List<Integer> b) {
        for (int i = 0; i < a.size(); i++) {
            int a0 = a.get(i);
            int a1 = a.get((i + 1) % a.size());
            for (int j = 0; j < b.size(); j++) {
                int b0 = b.get(j);
                int b1 = b.get((j + 1) % b.size());
                if (a0 == b1 && a1 == b0) {
                    List<Integer> joined = new ArrayList<>();
                    for (int k = 0; k < a.size(); k++) {
                        joined.add(a.get((i + 1 + k) % a.size()));
                    }
                    // joined starts at a1 and ends at a0; insert b's vertices between a0 and a1.
                    for (int k = 2; k < b.size(); k++) {
                        joined.add(b.get((j + k) % b.size()));
                    }
                    return joined;
                }
            }
        }
        return null;
    }

    private static Polygon toPolygon(List<Vec2> vertices, List<Integer> indices) {
        List<Vec2> points = new ArrayList<>(indices.size());
        for (int index : indices) {
            points.add(vertices.get(index));
        }
        return new Polygon(points);
    }
}
