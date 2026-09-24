package dev.gulp.core.physics;

/**
 * A convex piece of a shape in local coordinates: 1 vertex (circle), 2 vertices (segment, or capsule with a radius) or
 * 3..8 vertices (polygon), plus a rounding radius. Edge {@code i} runs from vertex {@code i} to vertex {@code i + 1}
 * with the outward normal {@code i}; a segment has two edges, one per side.
 */
final class Convex {

    static final int MAX_VERTICES = 8;

    final int count;
    final float[] x;
    final float[] y;
    final float[] nx;
    final float[] ny;
    final float radius;
    /** Area, centroid and polar moment about the centroid, for density 1. */
    final float area;

    final float centroidX;
    final float centroidY;
    final float inertia;

    Convex(float[] x, float[] y, int count, float radius) {
        if (count < 1 || count > MAX_VERTICES) {
            throw new IllegalArgumentException("A convex piece has 1.." + MAX_VERTICES + " vertices: " + count);
        }
        this.count = count;
        this.x = java.util.Arrays.copyOf(x, count);
        this.y = java.util.Arrays.copyOf(y, count);
        this.radius = radius;
        int edges = count == 1 ? 0 : count;
        nx = new float[Math.max(1, edges)];
        ny = new float[Math.max(1, edges)];
        float cx = 0f;
        float cy = 0f;
        for (int i = 0; i < count; i++) {
            cx += this.x[i];
            cy += this.y[i];
        }
        cx /= count;
        cy /= count;
        for (int i = 0; i < edges; i++) {
            int j = (i + 1) % count;
            float ex = this.x[j] - this.x[i];
            float ey = this.y[j] - this.y[i];
            float length = (float) Math.sqrt(ex * ex + ey * ey);
            float px = length > 0f ? ey / length : 0f;
            float py = length > 0f ? -ex / length : 0f;
            if (count >= 3) {
                float mx = (this.x[i] + this.x[j]) * 0.5f - cx;
                float my = (this.y[i] + this.y[j]) * 0.5f - cy;
                if (px * mx + py * my < 0f) {
                    px = -px;
                    py = -py;
                }
            } else if (i == 1) {
                // The back side of a segment faces the other way.
                px = -nx[0];
                py = -ny[0];
            }
            nx[i] = px;
            ny[i] = py;
        }
        // Mass properties.
        if (count == 1) {
            area = (float) (Math.PI * radius * radius);
            centroidX = this.x[0];
            centroidY = this.y[0];
            inertia = area * 0.5f * radius * radius;
        } else if (count == 2) {
            float ex = this.x[1] - this.x[0];
            float ey = this.y[1] - this.y[0];
            float length = (float) Math.sqrt(ex * ex + ey * ey);
            float rect = 2f * radius * length;
            float circle = (float) (Math.PI * radius * radius);
            area = rect + circle;
            centroidX = cx;
            centroidY = cy;
            inertia = rect * (length * length + 4f * radius * radius) / 12f
                    + circle * (0.5f * radius * radius + 0.25f * length * length);
        } else {
            // Triangle fan around the vertex average; signed sums work for either winding.
            float signedArea = 0f;
            float sx = 0f;
            float sy = 0f;
            float ix = 0f;
            for (int i = 0; i < count; i++) {
                int j = (i + 1) % count;
                float e1x = this.x[i] - cx;
                float e1y = this.y[i] - cy;
                float e2x = this.x[j] - cx;
                float e2y = this.y[j] - cy;
                float d = e1x * e2y - e1y * e2x;
                float triangle = 0.5f * d;
                signedArea += triangle;
                sx += triangle * (e1x + e2x) / 3f;
                sy += triangle * (e1y + e2y) / 3f;
                ix += d / 12f * (e1x * e1x + e2x * e1x + e2x * e2x + e1y * e1y + e2y * e1y + e2y * e2y);
            }
            float ox = signedArea != 0f ? sx / signedArea : 0f;
            float oy = signedArea != 0f ? sy / signedArea : 0f;
            area = Math.abs(signedArea);
            centroidX = cx + ox;
            centroidY = cy + oy;
            // Inertia about the vertex average, moved to the centroid.
            inertia = Math.abs(ix) - area * (ox * ox + oy * oy);
        }
    }

    /** Number of edges: 0 for a circle, 2 for a segment, {@code count} for a polygon. */
    int edges() {
        return count == 1 ? 0 : count;
    }

    /** Largest distance of the piece from the origin, radius included. */
    float extent() {
        float best = 0f;
        for (int i = 0; i < count; i++) {
            best = Math.max(best, (float) Math.sqrt(x[i] * x[i] + y[i] * y[i]));
        }
        return best + radius;
    }

    /** Returns this piece moved by an offset. */
    Convex translated(float dx, float dy) {
        float[] tx = new float[count];
        float[] ty = new float[count];
        for (int i = 0; i < count; i++) {
            tx[i] = x[i] + dx;
            ty[i] = y[i] + dy;
        }
        return new Convex(tx, ty, count, radius);
    }
}
