package dev.gulp.core.physics;

/**
 * Narrow phase between placed convex pieces: contact manifolds by the separating axis test with reference face
 * clipping, exact closest features for rounded pieces, ray casts and point tests. Normals point from the first piece to
 * the second. Allocation-free: results go into caller-owned objects.
 */
final class Collide {

    /** Penetration the solver tolerates, and the scale of other tolerances. World units. */
    static final float SLOP = 0.005f;

    /** Contacts are kept up to this gap, so resting bodies do not flicker in and out of contact. */
    static final float SPECULATIVE = 4f * SLOP;

    private Collide() {}

    /**
     * Builds the contact manifold between two pieces, keeping points up to {@code margin} apart.
     *
     * @return {@code out}, with {@code count == 0} when farther apart than the margin
     */
    static Manifold collide(Placed a, Placed b, float margin, Manifold out) {
        out.count = 0;
        if (a.count == 1 && b.count == 1) {
            circles(a, b, margin, out);
        } else if (b.count == 1) {
            polygonCircle(a, b.x[0], b.y[0], b.radius, margin, out);
        } else if (a.count == 1) {
            polygonCircle(b, a.x[0], a.y[0], a.radius, margin, out);
            out.flip();
        } else {
            polygons(a, b, margin, out);
        }
        return out;
    }

    /** Returns whether two pieces overlap by more than a hair. */
    static boolean overlaps(Placed a, Placed b, Manifold scratch) {
        if (a.maxX < b.minX || b.maxX < a.minX || a.maxY < b.minY || b.maxY < a.minY) {
            return false;
        }
        collide(a, b, 0f, scratch);
        return scratch.count > 0 && scratch.minSeparation() < -SLOP * 0.1f;
    }

    private static void circles(Placed a, Placed b, float margin, Manifold out) {
        float dx = b.x[0] - a.x[0];
        float dy = b.y[0] - a.y[0];
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float s = distance - a.radius - b.radius;
        if (s > margin) {
            return;
        }
        float nx = distance > 1e-6f ? dx / distance : 0f;
        float ny = distance > 1e-6f ? dy / distance : -1f;
        out.nx = nx;
        out.ny = ny;
        float ax = a.x[0] + nx * a.radius;
        float ay = a.y[0] + ny * a.radius;
        float bx = b.x[0] - nx * b.radius;
        float by = b.y[0] - ny * b.radius;
        out.add((ax + bx) * 0.5f, (ay + by) * 0.5f, s, 0);
    }

    /** Polygon or segment {@code p} against a circle; the normal points from the polygon to the circle. */
    private static void polygonCircle(Placed p, float cx, float cy, float cr, float margin, Manifold out) {
        float total = p.radius + cr;
        if (p.count >= 3) {
            float best = -Float.MAX_VALUE;
            int bestEdge = 0;
            for (int i = 0; i < p.count; i++) {
                float s = p.nx[i] * (cx - p.x[i]) + p.ny[i] * (cy - p.y[i]);
                if (s > best) {
                    best = s;
                    bestEdge = i;
                }
            }
            if (best > total + margin) {
                return;
            }
            if (best <= 0f) {
                // Centre inside the core: push out through the nearest face.
                float nx = p.nx[bestEdge];
                float ny = p.ny[bestEdge];
                out.nx = nx;
                out.ny = ny;
                float fx = cx - nx * best + nx * p.radius;
                float fy = cy - ny * best + ny * p.radius;
                float bx = cx - nx * cr;
                float by = cy - ny * cr;
                out.add((fx + bx) * 0.5f, (fy + by) * 0.5f, best - total, bestEdge);
                return;
            }
        }
        // Closest point on the core boundary.
        float qx = p.x[0];
        float qy = p.y[0];
        float bestSq = Float.MAX_VALUE;
        int bestEdge = 0;
        int edges = p.count >= 3 ? p.count : 1;
        for (int i = 0; i < edges; i++) {
            int j = (i + 1) % p.count;
            float ex = p.x[j] - p.x[i];
            float ey = p.y[j] - p.y[i];
            float lengthSq = ex * ex + ey * ey;
            float t = lengthSq > 0f ? ((cx - p.x[i]) * ex + (cy - p.y[i]) * ey) / lengthSq : 0f;
            t = Math.max(0f, Math.min(1f, t));
            float sx = p.x[i] + ex * t;
            float sy = p.y[i] + ey * t;
            float dSq = (cx - sx) * (cx - sx) + (cy - sy) * (cy - sy);
            if (dSq < bestSq) {
                bestSq = dSq;
                qx = sx;
                qy = sy;
                bestEdge = i;
            }
        }
        float distance = (float) Math.sqrt(bestSq);
        float s = distance - total;
        if (s > margin) {
            return;
        }
        float nx;
        float ny;
        if (distance > 1e-6f) {
            nx = (cx - qx) / distance;
            ny = (cy - qy) / distance;
        } else {
            nx = p.nx[bestEdge];
            ny = p.ny[bestEdge];
        }
        out.nx = nx;
        out.ny = ny;
        float ax = qx + nx * p.radius;
        float ay = qy + ny * p.radius;
        float bx = cx - nx * cr;
        float by = cy - ny * cr;
        out.add((ax + bx) * 0.5f, (ay + by) * 0.5f, s, bestEdge);
    }

    private static final int[] EDGE = new int[1];

    /** Smallest distance of {@code b}'s vertices in front of {@code a}'s faces, maximised over the faces. */
    private static float maxSeparation(Placed a, Placed b, int[] edgeOut) {
        float best = -Float.MAX_VALUE;
        int bestEdge = 0;
        int edges = a.edges();
        for (int i = 0; i < edges; i++) {
            float nx = a.nx[i];
            float ny = a.ny[i];
            float ax = a.x[i % a.count];
            float ay = a.y[i % a.count];
            float s = Float.MAX_VALUE;
            for (int j = 0; j < b.count; j++) {
                s = Math.min(s, nx * (b.x[j] - ax) + ny * (b.y[j] - ay));
            }
            if (s > best) {
                best = s;
                bestEdge = i;
            }
        }
        edgeOut[0] = bestEdge;
        return best;
    }

    private static void polygons(Placed a, Placed b, float margin, Manifold out) {
        float sepA = maxSeparation(a, b, EDGE);
        int edgeA = EDGE[0];
        float sepB = maxSeparation(b, a, EDGE);
        int edgeB = EDGE[0];
        float total = a.radius + b.radius;
        if (sepA > total + margin || sepB > total + margin) {
            return;
        }
        float faceSeparation = Math.max(sepA, sepB);
        if (total > 0f && faceSeparation > 0.1f * SLOP) {
            // Rounded cores apart: the closest features decide; corner-to-corner needs its own normal.
            if (closestFeatures(a, b)) {
                float distance = closestDistance;
                if (distance > total + margin) {
                    return;
                }
                if (distance > faceSeparation + 0.1f * SLOP && distance > 1e-6f) {
                    float nx = (closestBX - closestAX) / distance;
                    float ny = (closestBY - closestAY) / distance;
                    out.nx = nx;
                    out.ny = ny;
                    float ax = closestAX + nx * a.radius;
                    float ay = closestAY + ny * a.radius;
                    float bx = closestBX - nx * b.radius;
                    float by = closestBY - ny * b.radius;
                    out.add((ax + bx) * 0.5f, (ay + by) * 0.5f, distance - total, 1 << 20);
                    return;
                }
            }
        }
        boolean flip = sepB > sepA + 0.1f * SLOP;
        Placed ref = flip ? b : a;
        Placed inc = flip ? a : b;
        int refEdge = flip ? edgeB : edgeA;
        float nx = ref.nx[refEdge];
        float ny = ref.ny[refEdge];
        int i1 = refEdge % ref.count;
        int i2 = (refEdge + 1) % ref.count;
        float v1x = ref.x[i1];
        float v1y = ref.y[i1];
        float v2x = ref.x[i2];
        float v2y = ref.y[i2];
        // Incident edge: the one facing the reference normal the most.
        int incEdge = 0;
        float lowest = Float.MAX_VALUE;
        int incEdges = inc.edges();
        for (int k = 0; k < incEdges; k++) {
            float d = nx * inc.nx[k] + ny * inc.ny[k];
            if (d < lowest) {
                lowest = d;
                incEdge = k;
            }
        }
        int k1 = incEdge % inc.count;
        int k2 = (incEdge + 1) % inc.count;
        float w1x = inc.x[k1];
        float w1y = inc.y[k1];
        float w2x = inc.x[k2];
        float w2y = inc.y[k2];
        float tx = v2x - v1x;
        float ty = v2y - v1y;
        float length = (float) Math.sqrt(tx * tx + ty * ty);
        if (length < 1e-9f) {
            return;
        }
        tx /= length;
        ty /= length;
        float lower = tx * v1x + ty * v1y;
        float upper = tx * v2x + ty * v2y;
        float a1 = tx * w1x + ty * w1y;
        float a2 = tx * w2x + ty * w2y;
        // Clip the incident edge to the side planes of the reference edge.
        float c1x = w1x;
        float c1y = w1y;
        float c2x = w2x;
        float c2y = w2y;
        if (a1 < lower && a2 >= lower) {
            float t = (lower - a1) / (a2 - a1);
            c1x = w1x + (w2x - w1x) * t;
            c1y = w1y + (w2y - w1y) * t;
        } else if (a2 < lower && a1 >= lower) {
            float t = (lower - a2) / (a1 - a2);
            c2x = w2x + (w1x - w2x) * t;
            c2y = w2y + (w1y - w2y) * t;
        } else if (a1 < lower && a2 < lower) {
            return;
        }
        float b1 = tx * c1x + ty * c1y;
        float b2 = tx * c2x + ty * c2y;
        if (b1 > upper && b2 <= upper) {
            float t = (b1 - upper) / (b1 - b2);
            c1x = c1x + (c2x - c1x) * t;
            c1y = c1y + (c2y - c1y) * t;
        } else if (b2 > upper && b1 <= upper) {
            float t = (b2 - upper) / (b2 - b1);
            c2x = c2x + (c1x - c2x) * t;
            c2y = c2y + (c1y - c2y) * t;
        } else if (b1 > upper && b2 > upper) {
            return;
        }
        float rRef = ref.radius;
        float rInc = inc.radius;
        int flipBit = flip ? 1 << 16 : 0;
        for (int p = 0; p < 2; p++) {
            float qx = p == 0 ? c1x : c2x;
            float qy = p == 0 ? c1y : c2y;
            float depth = nx * (qx - v1x) + ny * (qy - v1y);
            float s = depth - total;
            if (s > margin) {
                continue;
            }
            float incX = qx - nx * rInc;
            float incY = qy - ny * rInc;
            float refX = qx - nx * depth + nx * rRef;
            float refY = qy - ny * depth + ny * rRef;
            out.add((incX + refX) * 0.5f, (incY + refY) * 0.5f, s, flipBit | refEdge << 8 | incEdge << 4 | p);
        }
        out.nx = flip ? -nx : nx;
        out.ny = flip ? -ny : ny;
    }

    private static float closestDistance;
    private static float closestAX;
    private static float closestAY;
    private static float closestBX;
    private static float closestBY;

    /** Closest points between the cores of two separated pieces, by vertex-to-edge tests. */
    private static boolean closestFeatures(Placed a, Placed b) {
        float best = Float.MAX_VALUE;
        best = vertexEdge(a, b, best, false);
        best = vertexEdge(b, a, best, true);
        if (best == Float.MAX_VALUE) {
            return false;
        }
        closestDistance = (float) Math.sqrt(best);
        return true;
    }

    private static float vertexEdge(Placed vertices, Placed edges, float best, boolean swapped) {
        int edgeCount = edges.count >= 3 ? edges.count : edges.count - 1;
        for (int i = 0; i < vertices.count; i++) {
            float px = vertices.x[i];
            float py = vertices.y[i];
            for (int e = 0; e < Math.max(1, edgeCount); e++) {
                int j = edges.count == 1 ? 0 : (e + 1) % edges.count;
                float ex = edges.x[j] - edges.x[e];
                float ey = edges.y[j] - edges.y[e];
                float lengthSq = ex * ex + ey * ey;
                float t = lengthSq > 0f ? ((px - edges.x[e]) * ex + (py - edges.y[e]) * ey) / lengthSq : 0f;
                t = Math.max(0f, Math.min(1f, t));
                float qx = edges.x[e] + ex * t;
                float qy = edges.y[e] + ey * t;
                float dSq = (px - qx) * (px - qx) + (py - qy) * (py - qy);
                if (dSq < best) {
                    best = dSq;
                    if (swapped) {
                        closestAX = qx;
                        closestAY = qy;
                        closestBX = px;
                        closestBY = py;
                    } else {
                        closestAX = px;
                        closestAY = py;
                        closestBX = qx;
                        closestBY = qy;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Signed distance between two pieces: negative when overlapping (by the deepest overlap), the exact gap otherwise.
     * The normal points from {@code a} to {@code b}; {@code out} holds one point.
     *
     * @return {@code out}
     */
    static Manifold distance(Placed a, Placed b, Manifold out) {
        if (a.count == 1 || b.count == 1) {
            return collide(a, b, Float.MAX_VALUE, out);
        }
        float sepA = maxSeparation(a, b, EDGE);
        float sepB = maxSeparation(b, a, EDGE);
        if (Math.max(sepA, sepB) <= 0f) {
            collide(a, b, 0f, out);
            if (out.count > 0) {
                return out;
            }
        }
        out.count = 0;
        if (!closestFeatures(a, b)) {
            return out;
        }
        float d = closestDistance;
        float nx;
        float ny;
        if (d > 1e-6f) {
            nx = (closestBX - closestAX) / d;
            ny = (closestBY - closestAY) / d;
        } else {
            nx = 0f;
            ny = -1f;
        }
        out.nx = nx;
        out.ny = ny;
        out.add((closestAX + closestBX) * 0.5f, (closestAY + closestBY) * 0.5f, d - a.radius - b.radius, 0);
        return out;
    }

    // ------------------------------------------------------------------ queries

    /** Result of {@link #raycast}. */
    static float rayFraction;

    static float rayNormalX;
    static float rayNormalY;

    /**
     * Casts a ray from {@code (ox, oy)} along {@code (dx, dy)} (the full length is fraction 1) against a piece.
     *
     * @return whether it hits before {@code maxFraction}; the hit is in {@link #rayFraction} and the normal fields
     */
    static boolean raycast(Placed p, float ox, float oy, float dx, float dy, float maxFraction) {
        if (p.count == 1) {
            return rayCircle(p.x[0], p.y[0], p.radius, ox, oy, dx, dy, maxFraction);
        }
        if (p.count == 2) {
            if (p.radius <= 0f) {
                return raySegment(p, ox, oy, dx, dy, maxFraction);
            }
            boolean hit = false;
            float best = maxFraction;
            float bnx = 0f;
            float bny = 0f;
            for (int i = 0; i < 2; i++) {
                if (rayCircle(p.x[i], p.y[i], p.radius, ox, oy, dx, dy, best)) {
                    hit = true;
                    best = rayFraction;
                    bnx = rayNormalX;
                    bny = rayNormalY;
                }
            }
            // The straight sides: the segment pushed out by the radius on both sides.
            for (int side = 0; side < 2; side++) {
                float offX = p.nx[side] * p.radius;
                float offY = p.ny[side] * p.radius;
                if (rayLine(
                        p.x[0] + offX,
                        p.y[0] + offY,
                        p.x[1] + offX,
                        p.y[1] + offY,
                        p.nx[side],
                        p.ny[side],
                        ox,
                        oy,
                        dx,
                        dy,
                        best)) {
                    hit = true;
                    best = rayFraction;
                    bnx = rayNormalX;
                    bny = rayNormalY;
                }
            }
            rayFraction = best;
            rayNormalX = bnx;
            rayNormalY = bny;
            return hit;
        }
        float lower = 0f;
        float upper = maxFraction;
        int index = -1;
        for (int i = 0; i < p.count; i++) {
            float numerator = p.nx[i] * (p.x[i] - ox) + p.ny[i] * (p.y[i] - oy);
            float denominator = p.nx[i] * dx + p.ny[i] * dy;
            if (denominator == 0f) {
                if (numerator < 0f) {
                    return false;
                }
            } else if (denominator < 0f && numerator < lower * denominator) {
                lower = numerator / denominator;
                index = i;
            } else if (denominator > 0f && numerator < upper * denominator) {
                upper = numerator / denominator;
            }
            if (upper < lower) {
                return false;
            }
        }
        if (index < 0) {
            return false;
        }
        rayFraction = lower;
        rayNormalX = p.nx[index];
        rayNormalY = p.ny[index];
        return true;
    }

    private static boolean rayCircle(
            float cx, float cy, float r, float ox, float oy, float dx, float dy, float maxFraction) {
        float sx = ox - cx;
        float sy = oy - cy;
        float a = dx * dx + dy * dy;
        if (a == 0f) {
            return false;
        }
        float b = sx * dx + sy * dy;
        float c = sx * sx + sy * sy - r * r;
        if (c < 0f) {
            return false;
        }
        float disc = b * b - a * c;
        if (disc < 0f) {
            return false;
        }
        float t = (-b - (float) Math.sqrt(disc)) / a;
        if (t < 0f || t > maxFraction) {
            return false;
        }
        rayFraction = t;
        float hx = sx + dx * t;
        float hy = sy + dy * t;
        float length = (float) Math.sqrt(hx * hx + hy * hy);
        rayNormalX = length > 0f ? hx / length : 0f;
        rayNormalY = length > 0f ? hy / length : -1f;
        return true;
    }

    private static boolean raySegment(Placed p, float ox, float oy, float dx, float dy, float maxFraction) {
        float nx = p.nx[0];
        float ny = p.ny[0];
        float denominator = nx * dx + ny * dy;
        if (denominator > 0f) {
            nx = -nx;
            ny = -ny;
            denominator = -denominator;
        }
        return rayLine(p.x[0], p.y[0], p.x[1], p.y[1], nx, ny, ox, oy, dx, dy, maxFraction);
    }

    /** One-sided line piece with normal {@code n}; hits only from the front. */
    private static boolean rayLine(
            float ax,
            float ay,
            float bx,
            float by,
            float nx,
            float ny,
            float ox,
            float oy,
            float dx,
            float dy,
            float maxFraction) {
        float denominator = nx * dx + ny * dy;
        if (denominator >= 0f) {
            return false;
        }
        float t = (nx * (ax - ox) + ny * (ay - oy)) / denominator;
        if (t < 0f || t > maxFraction) {
            return false;
        }
        float hx = ox + dx * t;
        float hy = oy + dy * t;
        float ex = bx - ax;
        float ey = by - ay;
        float lengthSq = ex * ex + ey * ey;
        float u = lengthSq > 0f ? ((hx - ax) * ex + (hy - ay) * ey) / lengthSq : 0f;
        if (u < 0f || u > 1f) {
            return false;
        }
        rayFraction = t;
        rayNormalX = nx;
        rayNormalY = ny;
        return true;
    }

    /** Returns whether a point lies inside a piece. */
    static boolean contains(Placed p, float px, float py) {
        if (p.count >= 3) {
            for (int i = 0; i < p.count; i++) {
                if (p.nx[i] * (px - p.x[i]) + p.ny[i] * (py - p.y[i]) > p.radius) {
                    return false;
                }
            }
            return true;
        }
        float qx = p.x[0];
        float qy = p.y[0];
        if (p.count == 2) {
            float ex = p.x[1] - p.x[0];
            float ey = p.y[1] - p.y[0];
            float lengthSq = ex * ex + ey * ey;
            float t = lengthSq > 0f ? ((px - p.x[0]) * ex + (py - p.y[0]) * ey) / lengthSq : 0f;
            t = Math.max(0f, Math.min(1f, t));
            qx += ex * t;
            qy += ey * t;
        }
        return (px - qx) * (px - qx) + (py - qy) * (py - qy) <= p.radius * p.radius;
    }
}
