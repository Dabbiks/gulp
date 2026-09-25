package dev.gulp.core.world;

import dev.gulp.api.entity.component.Occluder;
import dev.gulp.api.graphics.BlendMode;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Material;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.physics.Shape;
import dev.gulp.api.render.Light;
import dev.gulp.core.graphics.DrawImpl;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Draws the lights of a world into its light map. Every light with shadows gets a 1D shadow map on the CPU: the
 * distance to the nearest occluder edge for each of {@value #BINS} directions, from {@link Occluder} shapes and the
 * exposed edges of solid collision tiles. The lit area is drawn as a fan of triangles whose shader fades the colour
 * with distance. Does not allocate after warm-up.
 */
final class LightRenderer {

    static final int BINS = 720;

    /** How far light reaches into the edge that stops it, so the faces of occluders are lit. */
    private static final float PENETRATION = 0.3f;

    private static final float STEP = (float) (2.0 * Math.PI / BINS);
    private static final float[] COS = new float[BINS + 1];
    private static final float[] SIN = new float[BINS + 1];

    static {
        for (int i = 0; i <= BINS; i++) {
            COS[i] = (float) Math.cos(i * STEP);
            SIN[i] = (float) Math.sin(i * STEP);
        }
    }

    private final float[] distance = new float[BINS + 1];
    private float[] segments = new float[256];
    private int segmentCount;
    private boolean[] solid = new boolean[256];
    private final float[] corners = new float[32];
    private @Nullable Material material;

    /**
     * Draws every enabled light that reaches the view into the bound light map, additively.
     *
     * @param world the world
     * @param lighting its lighting
     * @param draw drawing in world units
     * @param view visible area as min x, min y, max x, max y
     */
    void draw(WorldImpl world, LightingImpl lighting, DrawImpl draw, float[] view) {
        Material additive = material;
        if (additive == null) {
            additive =
                    Material.DEFAULT.withShader(draw.graphics().lightShader()).withBlend(BlendMode.ADD);
            material = additive;
        }
        draw.material(additive);
        draw.whiteTexture();
        List<Light> lights = lighting.lights;
        for (int i = 0; i < lights.size(); i++) {
            Light light = lights.get(i);
            if (!light.isEnabled() || light.intensity() <= 0f) {
                continue;
            }
            Color c = light.color();
            float k = light.intensity();
            int abgr = draw.packColor(c.r() * k, c.g() * k, c.b() * k, 1f);
            draw.rawParams(Math.round(Math.clamp(light.falloff() / 4f, 0f, 1f) * 255f) << 24);
            if (light.type() == Light.Type.DIRECTIONAL) {
                draw.rawTriangle(view[0], view[1], 0f, 0f, view[2], view[1], 0f, 0f, view[2], view[3], 0f, 0f, abgr);
                draw.rawTriangle(view[0], view[1], 0f, 0f, view[2], view[3], 0f, 0f, view[0], view[3], 0f, 0f, abgr);
                continue;
            }
            float lx = light.position().x();
            float ly = light.position().y();
            float r = light.radius();
            if (lx + r < view[0] || lx - r > view[2] || ly + r < view[1] || ly - r > view[3] || r <= 0f) {
                continue;
            }
            shadowMap(world, lighting, light);
            fan(draw, light, lx, ly, r, abgr);
        }
        draw.rawParams(0);
        draw.material(Material.DEFAULT);
    }

    /**
     * Fills the 1D shadow map of a light: how far it reaches in each direction.
     *
     * @param world the world
     * @param lighting its lighting
     * @param light the light
     */
    void shadowMap(WorldImpl world, LightingImpl lighting, Light light) {
        float lx = light.position().x();
        float ly = light.position().y();
        float r = light.radius();
        Arrays.fill(distance, r);
        if (light.castsShadows()) {
            segmentCount = 0;
            if (lighting.tileShadows()) {
                collectTiles(world, lx, ly, r);
            }
            collectOccluders(world, lx, ly, r);
            shadow(lx, ly, r);
        }
    }

    /**
     * Returns how far the last shadow map reaches in a direction.
     *
     * @param bin the direction, {@code 0..BINS-1} clockwise from the right
     * @return world units
     */
    float reach(int bin) {
        return distance[bin];
    }

    private void fan(DrawImpl draw, Light light, float lx, float ly, float r, int abgr) {
        boolean spot = light.type() == Light.Type.SPOT;
        float direction = (float) Math.toRadians(light.direction());
        float half = (float) Math.toRadians(light.cone()) * 0.5f;
        float inv = 1f / r;
        for (int k = 0; k < BINS; k++) {
            if (spot) {
                float middle = (k + 0.5f) * STEP;
                float diff = Math.abs(wrap(middle - direction));
                if (diff > half) {
                    continue;
                }
            }
            float d0 = distance[k];
            float d1 = distance[k + 1 == BINS ? 0 : k + 1];
            float x0 = COS[k] * d0;
            float y0 = SIN[k] * d0;
            float x1 = COS[k + 1] * d1;
            float y1 = SIN[k + 1] * d1;
            draw.rawTriangle(
                    lx, ly, 0f, 0f, lx + x0, ly + y0, x0 * inv, y0 * inv, lx + x1, ly + y1, x1 * inv, y1 * inv, abgr);
        }
    }

    private static float wrap(float radians) {
        float twoPi = (float) (2.0 * Math.PI);
        float a = radians % twoPi;
        if (a > Math.PI) {
            a -= twoPi;
        } else if (a < -Math.PI) {
            a += twoPi;
        }
        return a;
    }

    // ------------------------------------------------------------------ shadow map

    private void shadow(float lx, float ly, float r) {
        for (int s = 0; s < segmentCount; s++) {
            int o = s * 4;
            float ax = segments[o] - lx;
            float ay = segments[o + 1] - ly;
            float bx = segments[o + 2] - lx;
            float by = segments[o + 3] - ly;
            float a0 = (float) Math.atan2(ay, ax);
            float a1 = (float) Math.atan2(by, bx);
            float span = a1 - a0;
            if (span > Math.PI) {
                span -= (float) (2.0 * Math.PI);
            } else if (span < -Math.PI) {
                span += (float) (2.0 * Math.PI);
            }
            float start = span >= 0f ? a0 : a1;
            span = Math.abs(span);
            int k0 = (int) Math.ceil(start / STEP);
            int k1 = (int) Math.floor((start + span) / STEP);
            float ex = bx - ax;
            float ey = by - ay;
            for (int k = k0; k <= k1; k++) {
                int bin = Math.floorMod(k, BINS);
                float dx = COS[bin];
                float dy = SIN[bin];
                float denominator = dx * ey - dy * ex;
                if (Math.abs(denominator) < 1e-6f) {
                    continue;
                }
                float t = (ax * ey - ay * ex) / denominator;
                if (t > 0f) {
                    float reach = Math.min(r, t + PENETRATION);
                    if (reach < distance[bin]) {
                        distance[bin] = reach;
                    }
                }
            }
        }
        distance[BINS] = distance[0];
    }

    private void addSegment(float x1, float y1, float x2, float y2) {
        if (segmentCount * 4 + 4 > segments.length) {
            segments = Arrays.copyOf(segments, segments.length * 2);
        }
        int o = segmentCount * 4;
        segments[o] = x1;
        segments[o + 1] = y1;
        segments[o + 2] = x2;
        segments[o + 3] = y2;
        segmentCount++;
    }

    /** Adds the exposed edges of solid tiles around a light, merged into long runs. */
    private void collectTiles(WorldImpl world, float lx, float ly, float r) {
        TileMapImpl map = world.tileMap;
        if (map.chunks.size() == 0) {
            return;
        }
        int minX = (int) Math.floor(lx - r) - 1;
        int minY = (int) Math.floor(ly - r) - 1;
        int maxX = (int) Math.floor(lx + r) + 1;
        int maxY = (int) Math.floor(ly + r) + 1;
        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        if (solid.length < w * h) {
            solid = new boolean[Math.max(w * h, solid.length * 2)];
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                solid[y * w + x] = map.isSolidAt(minX + x, minY + y);
            }
        }
        // Horizontal edges: top (y) and bottom (y + 1) faces.
        for (int y = 1; y < h - 1; y++) {
            int topStart = -1;
            int bottomStart = -1;
            for (int x = 1; x < w; x++) {
                boolean here = x < w - 1 && solid[y * w + x];
                boolean top = here && !solid[(y - 1) * w + x];
                boolean bottom = here && !solid[(y + 1) * w + x];
                if (top && topStart < 0) {
                    topStart = x;
                } else if (!top && topStart >= 0) {
                    addSegment(minX + topStart, minY + y, minX + x, minY + y);
                    topStart = -1;
                }
                if (bottom && bottomStart < 0) {
                    bottomStart = x;
                } else if (!bottom && bottomStart >= 0) {
                    addSegment(minX + bottomStart, minY + y + 1, minX + x, minY + y + 1);
                    bottomStart = -1;
                }
            }
        }
        // Vertical edges: left (x) and right (x + 1) faces.
        for (int x = 1; x < w - 1; x++) {
            int leftStart = -1;
            int rightStart = -1;
            for (int y = 1; y < h; y++) {
                boolean here = y < h - 1 && solid[y * w + x];
                boolean left = here && !solid[y * w + x - 1];
                boolean right = here && !solid[y * w + x + 1];
                if (left && leftStart < 0) {
                    leftStart = y;
                } else if (!left && leftStart >= 0) {
                    addSegment(minX + x, minY + leftStart, minX + x, minY + y);
                    leftStart = -1;
                }
                if (right && rightStart < 0) {
                    rightStart = y;
                } else if (!right && rightStart >= 0) {
                    addSegment(minX + x + 1, minY + rightStart, minX + x + 1, minY + y);
                    rightStart = -1;
                }
            }
        }
    }

    /** Adds the outlines of occluder shapes near a light. */
    private void collectOccluders(WorldImpl world, float lx, float ly, float r) {
        ComponentStore store = world.store(Occluder.class);
        if (store == null) {
            return;
        }
        for (int i = 0; i < store.size; i++) {
            EntityImpl entity = store.owners[i];
            if (entity == null || entity.isRemoved()) {
                continue;
            }
            Occluder occluder = entity.component(Occluder.class);
            if (occluder == null || !occluder.isEnabled()) {
                continue;
            }
            float cx = entity.x + occluder.offset().x();
            float cy = entity.y + occluder.offset().y();
            float reach = Math.max(entity.width, entity.height) + 4f;
            if (Math.abs(cx - lx) > r + reach || Math.abs(cy - ly) > r + reach) {
                continue;
            }
            float cos = 1f;
            float sin = 0f;
            if (entity.rotation != 0f) {
                double radians = Math.toRadians(entity.rotation);
                cos = (float) Math.cos(radians);
                sin = (float) Math.sin(radians);
            }
            Shape shape = occluder.shape();
            if (shape == null) {
                box(entity.width, entity.height, cx, cy, cos, sin);
                continue;
            }
            switch (shape) {
                case Shape.Box b -> box(b.width(), b.height(), cx, cy, cos, sin);
                case Shape.Capsule c -> box(c.radius() * 2f, c.height(), cx, cy, cos, sin);
                case Shape.Circle c -> {
                    int n = 12;
                    for (int k = 0; k < n; k++) {
                        corners[k * 2] = (float) Math.cos(k * 2.0 * Math.PI / n) * c.radius();
                        corners[k * 2 + 1] = (float) Math.sin(k * 2.0 * Math.PI / n) * c.radius();
                    }
                    outline(n, true, cx, cy, 1f, 0f);
                }
                case Shape.Segment s -> {
                    Vec2 a = s.a();
                    Vec2 b = s.b();
                    addSegment(
                            cx + a.x() * cos - a.y() * sin,
                            cy + a.x() * sin + a.y() * cos,
                            cx + b.x() * cos - b.y() * sin,
                            cy + b.x() * sin + b.y() * cos);
                }
                case Shape.Polygon p -> points(p.points(), true, cx, cy, cos, sin);
                case Shape.Chain c -> points(c.points(), c.loop(), cx, cy, cos, sin);
            }
        }
    }

    private void box(float width, float height, float cx, float cy, float cos, float sin) {
        float hw = width * 0.5f;
        float hh = height * 0.5f;
        corners[0] = -hw;
        corners[1] = -hh;
        corners[2] = hw;
        corners[3] = -hh;
        corners[4] = hw;
        corners[5] = hh;
        corners[6] = -hw;
        corners[7] = hh;
        outline(4, true, cx, cy, cos, sin);
    }

    private void points(List<Vec2> points, boolean loop, float cx, float cy, float cos, float sin) {
        int n = points.size();
        int edges = loop && n > 2 ? n : n - 1;
        for (int k = 0; k < edges; k++) {
            Vec2 a = points.get(k);
            Vec2 b = points.get((k + 1) % n);
            addSegment(
                    cx + a.x() * cos - a.y() * sin,
                    cy + a.x() * sin + a.y() * cos,
                    cx + b.x() * cos - b.y() * sin,
                    cy + b.x() * sin + b.y() * cos);
        }
    }

    private void outline(int n, boolean loop, float cx, float cy, float cos, float sin) {
        for (int k = 0; k < (loop ? n : n - 1); k++) {
            int j = (k + 1) % n;
            float ax = corners[k * 2];
            float ay = corners[k * 2 + 1];
            float bx = corners[j * 2];
            float by = corners[j * 2 + 1];
            addSegment(
                    cx + ax * cos - ay * sin,
                    cy + ax * sin + ay * cos,
                    cx + bx * cos - by * sin,
                    cy + bx * sin + by * cos);
        }
    }
}
