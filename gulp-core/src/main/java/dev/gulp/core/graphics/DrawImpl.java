package dev.gulp.core.graphics;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.FrameBuffer;
import dev.gulp.api.graphics.Material;
import dev.gulp.api.graphics.Mesh2D;
import dev.gulp.api.graphics.NinePatch;
import dev.gulp.api.graphics.Shader;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.math.Affine2;
import dev.gulp.api.math.Geometry;
import dev.gulp.api.math.Mathf;
import dev.gulp.api.math.Polygon;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Transform2D;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.render.Draw;
import dev.gulp.platform.Gl;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * {@link Draw} on top of the {@link Batcher}. Vertices are transformed on the CPU by the current transform; shapes get a
 * one-pixel anti-aliasing fringe whose outer vertices are transparent.
 */
public final class DrawImpl implements Draw {

    private static final int MAX_CLIPS = 32;

    private final Gl gl;
    private final Batcher batcher;
    private final GraphicsImpl graphics;

    private final Affine2 transform = new Affine2();
    private Color color = Color.WHITE;
    private float alpha = 1f;
    private int packed = 0xffffffff;
    private Material material = Material.DEFAULT;

    private Affine2[] savedTransforms = new Affine2[8];
    private Color[] savedColors = new Color[8];
    private float[] savedAlphas = new float[8];
    private Material[] savedMaterials = new Material[8];
    private int depth;

    private final Affine2 projection = new Affine2();
    private float unitsPerPixel = 1f;
    private float unitsPerTexel = 1f;
    private float snapStep;
    private int viewportX;
    private int viewportY;
    private int viewportWidth = 1;
    private int viewportHeight = 1;
    private int targetHeight = 1;
    private int framebuffer;

    private final int[] clips = new int[MAX_CLIPS * 4];
    private int clipDepth;

    private float[] points = new float[256];

    /**
     * Creates the draw.
     *
     * @param gl the graphics context
     * @param batcher the batcher
     * @param graphics source of the white and fallback textures and the default shader
     */
    public DrawImpl(Gl gl, Batcher batcher, GraphicsImpl graphics) {
        this.gl = gl;
        this.batcher = batcher;
        this.graphics = graphics;
        for (int i = 0; i < savedTransforms.length; i++) {
            savedTransforms[i] = new Affine2();
        }
    }

    // ------------------------------------------------------------------ setup by the renderer

    /**
     * Prepares drawing into the current target: resets state and sets the projection.
     *
     * @param newProjection drawing units to clip space
     * @param pixel drawing units per target pixel
     * @param texel drawing units per texture pixel for natural-size images
     * @param snap snapping step in drawing units, 0 for none
     * @param vx viewport left in target pixels
     * @param vy viewport top in target pixels
     * @param vw viewport width
     * @param vh viewport height
     * @param target height of the current target in pixels
     * @param targetFramebuffer handle of the bound frame buffer, 0 for the window
     */
    public void begin(
            Affine2 newProjection,
            float pixel,
            float texel,
            float snap,
            int vx,
            int vy,
            int vw,
            int vh,
            int target,
            int targetFramebuffer) {
        projection.set(newProjection);
        batcher.projection(projection);
        unitsPerPixel = pixel;
        unitsPerTexel = texel;
        snapStep = snap;
        viewportX = vx;
        viewportY = vy;
        viewportWidth = vw;
        viewportHeight = vh;
        targetHeight = target;
        framebuffer = targetFramebuffer;
        transform.identity();
        depth = 0;
        clipDepth = 0;
        color(Color.WHITE);
        alpha = 1f;
        updatePacked();
        material(Material.DEFAULT);
    }

    /** Draws what is pending. */
    public void flush() {
        batcher.flush();
    }

    // ------------------------------------------------------------------ images

    @Override
    public Draw image(TextureRegion region, float x, float y) {
        return image(region, x, y, region.originalWidth() * unitsPerTexel, region.originalHeight() * unitsPerTexel);
    }

    @Override
    public Draw image(TextureRegion region, float x, float y, float width, float height) {
        useTexture(region.texture());
        float sx = width / region.originalWidth();
        float sy = height / region.originalHeight();
        float x0 = x + region.offsetX() * sx;
        float y0 = y + region.offsetY() * sy;
        quad(region, x0, y0, x0 + region.width() * sx, y0 + region.height() * sy);
        return this;
    }

    @Override
    public Draw image(
            TextureRegion region,
            float x,
            float y,
            float width,
            float height,
            float originX,
            float originY,
            float degrees) {
        push();
        translate(x + originX, y + originY).rotate(degrees).translate(-originX, -originY);
        image(region, 0, 0, width, height);
        pop();
        return this;
    }

    @Override
    public Draw image(TextureRegion region, Transform2D placement) {
        push();
        transform.mul(placement.toAffine());
        image(region, 0, 0, 1, 1);
        pop();
        return this;
    }

    private void quad(TextureRegion region, float x0, float y0, float x1, float y1) {
        float u = region.u();
        float v = region.v();
        float u2 = region.u2();
        float v2 = region.v2();
        int base = batcher.reserve(4, 6);
        if (region.isRotated()) {
            vertex(x0, y0, u2, v, true);
            vertex(x1, y0, u2, v2, true);
            vertex(x1, y1, u, v2, true);
            vertex(x0, y1, u, v, true);
        } else {
            vertex(x0, y0, u, v, true);
            vertex(x1, y0, u2, v, true);
            vertex(x1, y1, u2, v2, true);
            vertex(x0, y1, u, v2, true);
        }
        batcher.quad(base);
    }

    private void uvQuad(float x0, float y0, float x1, float y1, float u, float v, float u2, float v2) {
        int base = batcher.reserve(4, 6);
        vertex(x0, y0, u, v, true);
        vertex(x1, y0, u2, v, true);
        vertex(x1, y1, u2, v2, true);
        vertex(x0, y1, u, v2, true);
        batcher.quad(base);
    }

    @Override
    public Draw ninePatch(NinePatch patch, Rect rect) {
        TextureRegion r = patch.region();
        useTexture(r.texture());
        float tw = r.texture().width();
        float th = r.texture().height();
        float[] xs = {
            rect.x(),
            rect.x() + patch.left() * unitsPerTexel,
            rect.right() - patch.right() * unitsPerTexel,
            rect.right()
        };
        float[] ys = {
            rect.y(),
            rect.y() + patch.top() * unitsPerTexel,
            rect.bottom() - patch.bottom() * unitsPerTexel,
            rect.bottom()
        };
        float[] us = {
            r.x() / tw, (r.x() + patch.left()) / tw, (r.x() + r.width() - patch.right()) / tw, (r.x() + r.width()) / tw
        };
        float[] vs = {
            r.y() / th,
            (r.y() + patch.top()) / th,
            (r.y() + r.height() - patch.bottom()) / th,
            (r.y() + r.height()) / th
        };
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (xs[col + 1] > xs[col] && ys[row + 1] > ys[row]) {
                    uvQuad(xs[col], ys[row], xs[col + 1], ys[row + 1], us[col], vs[row], us[col + 1], vs[row + 1]);
                }
            }
        }
        return this;
    }

    @Override
    public Draw tiled(TextureRegion region, Rect rect) {
        useTexture(region.texture());
        float tileW = region.width() * unitsPerTexel;
        float tileH = region.height() * unitsPerTexel;
        for (float y = rect.y(); y < rect.bottom(); y += tileH) {
            float h = Math.min(tileH, rect.bottom() - y);
            float fv = h / tileH;
            for (float x = rect.x(); x < rect.right(); x += tileW) {
                float w = Math.min(tileW, rect.right() - x);
                float fu = w / tileW;
                float u = region.u();
                float v = region.v();
                uvQuad(x, y, x + w, y + h, u, v, u + (region.u2() - u) * fu, v + (region.v2() - v) * fv);
            }
        }
        return this;
    }

    @Override
    public Draw mesh(Mesh2D mesh, @Nullable Texture texture) {
        if (texture == null) {
            useWhite();
        } else {
            useTexture(texture);
        }
        int offset = 0;
        // Draw in chunks that fit the batch; each chunk copies the vertices its triangles use.
        int indexCount = mesh.indexCount();
        if (mesh.vertexCount() <= Batcher.MAX_VERTICES && indexCount <= Batcher.MAX_INDICES) {
            int base = batcher.reserve(mesh.vertexCount(), indexCount);
            for (int i = 0; i < mesh.vertexCount(); i++) {
                emit(mesh.x(i), mesh.y(i), mesh.u(i), mesh.v(i), multiply(mesh.packedColor(i), packed), false);
            }
            for (int i = 0; i < indexCount; i += 3) {
                batcher.triangle(base + mesh.index(i), base + mesh.index(i + 1), base + mesh.index(i + 2));
            }
            return this;
        }
        while (offset < indexCount) {
            int base = batcher.reserve(3, 3);
            for (int k = 0; k < 3; k++) {
                int i = mesh.index(offset + k);
                emit(mesh.x(i), mesh.y(i), mesh.u(i), mesh.v(i), multiply(mesh.packedColor(i), packed), false);
            }
            batcher.triangle(base, base + 1, base + 2);
            offset += 3;
        }
        return this;
    }

    // ------------------------------------------------------------------ shapes

    @Override
    public Draw rect(float x, float y, float width, float height) {
        float[] p = points(4);
        p[0] = x;
        p[1] = y;
        p[2] = x + width;
        p[3] = y;
        p[4] = x + width;
        p[5] = y + height;
        p[6] = x;
        p[7] = y + height;
        fillConvex(p, 4);
        return this;
    }

    @Override
    public Draw rect(Rect rect) {
        return rect(rect.x(), rect.y(), rect.width(), rect.height());
    }

    @Override
    public Draw rectOutline(Rect rect, float thickness) {
        float t = Math.min(thickness, Math.min(rect.width(), rect.height()) / 2f);
        rect(rect.x(), rect.y(), rect.width(), t);
        rect(rect.x(), rect.bottom() - t, rect.width(), t);
        rect(rect.x(), rect.y() + t, t, rect.height() - 2 * t);
        rect(rect.right() - t, rect.y() + t, t, rect.height() - 2 * t);
        return this;
    }

    @Override
    public Draw roundedRect(Rect rect, float radius) {
        float r = Mathf.clamp(radius, 0f, Math.min(rect.width(), rect.height()) / 2f);
        if (r <= 0f) {
            return rect(rect);
        }
        int perCorner = Math.max(2, segments(r) / 4);
        float[] p = points(perCorner * 4);
        int n = 0;
        float[][] centers = {
            {rect.right() - r, rect.y() + r, -90},
            {rect.right() - r, rect.bottom() - r, 0},
            {rect.x() + r, rect.bottom() - r, 90},
            {rect.x() + r, rect.y() + r, 180}
        };
        for (float[] corner : centers) {
            for (int i = 0; i < perCorner; i++) {
                float angle = corner[2] + 90f * i / (perCorner - 1);
                p[n * 2] = corner[0] + Mathf.cosDeg(angle) * r;
                p[n * 2 + 1] = corner[1] + Mathf.sinDeg(angle) * r;
                n++;
            }
        }
        fillConvex(p, n);
        return this;
    }

    @Override
    public Draw gradientRect(Rect rect, Color top, Color bottom) {
        useWhite();
        int topColor = multiply(top.toPremultipliedAbgr(), packed);
        int bottomColor = multiply(bottom.toPremultipliedAbgr(), packed);
        int base = batcher.reserve(4, 6);
        emit(rect.x(), rect.y(), 0.5f, 0.5f, topColor, false);
        emit(rect.right(), rect.y(), 0.5f, 0.5f, topColor, false);
        emit(rect.right(), rect.bottom(), 0.5f, 0.5f, bottomColor, false);
        emit(rect.x(), rect.bottom(), 0.5f, 0.5f, bottomColor, false);
        batcher.quad(base);
        return this;
    }

    @Override
    public Draw circle(float cx, float cy, float radius) {
        return ellipse(cx, cy, radius, radius);
    }

    @Override
    public Draw ellipse(float cx, float cy, float radiusX, float radiusY) {
        int n = segments(Math.max(radiusX, radiusY));
        float[] p = points(n);
        for (int i = 0; i < n; i++) {
            float angle = 360f * i / n;
            p[i * 2] = cx + Mathf.cosDeg(angle) * radiusX;
            p[i * 2 + 1] = cy + Mathf.sinDeg(angle) * radiusY;
        }
        fillConvex(p, n);
        return this;
    }

    @Override
    public Draw circleOutline(float cx, float cy, float radius, float thickness) {
        useWhite();
        int n = segments(radius + thickness / 2f);
        float fringe = pixelSize();
        float inner = Math.max(0f, radius - thickness / 2f);
        float outer = radius + thickness / 2f;
        float[] radii = {
            Math.max(0f, inner - fringe / 2f),
            inner + fringe / 2f,
            Math.max(inner + fringe / 2f, outer - fringe / 2f),
            outer + fringe / 2f
        };
        int solid = packed;
        int[] colors = {0, solid, solid, 0};
        int base = batcher.reserve(n * 4, n * 18);
        for (int i = 0; i < n; i++) {
            float cos = Mathf.cosDeg(360f * i / n);
            float sin = Mathf.sinDeg(360f * i / n);
            for (int ring = 0; ring < 4; ring++) {
                emit(cx + cos * radii[ring], cy + sin * radii[ring], 0.5f, 0.5f, colors[ring], false);
            }
        }
        for (int i = 0; i < n; i++) {
            int a = base + i * 4;
            int b = base + ((i + 1) % n) * 4;
            for (int ring = 0; ring < 3; ring++) {
                batcher.triangle(a + ring, b + ring, b + ring + 1);
                batcher.triangle(a + ring, b + ring + 1, a + ring + 1);
            }
        }
        return this;
    }

    @Override
    public Draw arc(float cx, float cy, float radius, float startDegrees, float sweepDegrees) {
        float remaining = Mathf.clamp(sweepDegrees, -360f, 360f);
        float start = startDegrees;
        while (Math.abs(remaining) > 0.001f) {
            float part = Math.abs(remaining) > 180f ? Math.signum(remaining) * 180f : remaining;
            int n = Math.max(2, (int) Math.ceil(segments(radius) * Math.abs(part) / 360f)) + 1;
            float[] p = points(n + 1);
            p[0] = cx;
            p[1] = cy;
            for (int i = 0; i < n; i++) {
                float angle = start + part * i / (n - 1);
                p[(i + 1) * 2] = cx + Mathf.cosDeg(angle) * radius;
                p[(i + 1) * 2 + 1] = cy + Mathf.sinDeg(angle) * radius;
            }
            fillConvex(p, n + 1);
            start += part;
            remaining -= part;
        }
        return this;
    }

    @Override
    public Draw line(float x1, float y1, float x2, float y2, float width) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length == 0f) {
            return this;
        }
        float nx = -dy / length * width / 2f;
        float ny = dx / length * width / 2f;
        float[] p = points(4);
        p[0] = x1 + nx;
        p[1] = y1 + ny;
        p[2] = x2 + nx;
        p[3] = y2 + ny;
        p[4] = x2 - nx;
        p[5] = y2 - ny;
        p[6] = x1 - nx;
        p[7] = y1 - ny;
        fillConvex(p, 4);
        return this;
    }

    @Override
    public Draw line(Vec2 a, Vec2 b, float width) {
        return line(a.x(), a.y(), b.x(), b.y(), width);
    }

    @Override
    public Draw polyline(List<Vec2> linePoints, float width, boolean closed) {
        int count = linePoints.size();
        for (int i = 0; i + 1 < count; i++) {
            line(linePoints.get(i), linePoints.get(i + 1), width);
        }
        if (closed && count > 2) {
            line(linePoints.get(count - 1), linePoints.get(0), width);
        }
        return this;
    }

    @Override
    public Draw polygon(Polygon polygon) {
        List<Vec2> v = polygon.vertices();
        if (polygon.isConvex()) {
            float[] p = points(v.size());
            for (int i = 0; i < v.size(); i++) {
                p[i * 2] = v.get(i).x();
                p[i * 2 + 1] = v.get(i).y();
            }
            fillConvex(p, v.size());
            return this;
        }
        useWhite();
        int[] triangles = Geometry.triangulate(polygon);
        int base = batcher.reserve(v.size(), triangles.length);
        for (Vec2 point : v) {
            emit(point.x(), point.y(), 0.5f, 0.5f, packed, false);
        }
        for (int i = 0; i < triangles.length; i += 3) {
            batcher.triangle(base + triangles[i], base + triangles[i + 1], base + triangles[i + 2]);
        }
        return this;
    }

    @Override
    public Draw triangle(float x1, float y1, float x2, float y2, float x3, float y3) {
        float[] p = points(3);
        p[0] = x1;
        p[1] = y1;
        p[2] = x2;
        p[3] = y2;
        p[4] = x3;
        p[5] = y3;
        fillConvex(p, 3);
        return this;
    }

    /** Fills a convex polygon with an anti-aliased fringe; {@code p} holds {@code n} points as x, y pairs. */
    private void fillConvex(float[] p, int n) {
        if (n < 3) {
            return;
        }
        useWhite();
        float area = 0f;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += p[i * 2] * p[j * 2 + 1] - p[j * 2] * p[i * 2 + 1];
        }
        if (area == 0f) {
            return;
        }
        float orientation = area > 0f ? 1f : -1f;
        float half = pixelSize() / 2f;
        int base = batcher.reserve(n * 2, (n - 2) * 3 + n * 6);
        for (int i = 0; i < n; i++) {
            int prev = (i + n - 1) % n;
            int next = (i + 1) % n;
            float e1x = p[i * 2] - p[prev * 2];
            float e1y = p[i * 2 + 1] - p[prev * 2 + 1];
            float e2x = p[next * 2] - p[i * 2];
            float e2y = p[next * 2 + 1] - p[i * 2 + 1];
            float l1 = (float) Math.sqrt(e1x * e1x + e1y * e1y);
            float l2 = (float) Math.sqrt(e2x * e2x + e2y * e2y);
            float n1x = l1 == 0f ? 0f : orientation * e1y / l1;
            float n1y = l1 == 0f ? 0f : -orientation * e1x / l1;
            float n2x = l2 == 0f ? 0f : orientation * e2y / l2;
            float n2y = l2 == 0f ? 0f : -orientation * e2x / l2;
            float ax = n1x + n2x;
            float ay = n1y + n2y;
            float al = (float) Math.sqrt(ax * ax + ay * ay);
            float ox = 0f;
            float oy = 0f;
            if (al > 0f) {
                ax /= al;
                ay /= al;
                float dot = Math.max(0.25f, ax * n2x + ay * n2y);
                ox = ax / dot * half;
                oy = ay / dot * half;
            }
            emit(p[i * 2] - ox, p[i * 2 + 1] - oy, 0.5f, 0.5f, packed, false);
            emit(p[i * 2] + ox, p[i * 2 + 1] + oy, 0.5f, 0.5f, 0, false);
        }
        for (int i = 1; i < n - 1; i++) {
            batcher.triangle(base, base + i * 2, base + (i + 1) * 2);
        }
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            int innerI = base + i * 2;
            int innerJ = base + j * 2;
            batcher.triangle(innerI, innerI + 1, innerJ + 1);
            batcher.triangle(innerI, innerJ + 1, innerJ);
        }
    }

    private int segments(float radius) {
        float pixels = radius / Math.max(pixelSize(), 1e-6f);
        return Mathf.clamp((int) Math.ceil(Mathf.TAU * pixels / 4f), 12, 256);
    }

    private float[] points(int count) {
        if (points.length < count * 2) {
            points = new float[Math.max(count * 2, points.length * 2)];
        }
        return points;
    }

    // ------------------------------------------------------------------ vertices

    private void vertex(float x, float y, float u, float v, boolean snap) {
        emit(x, y, u, v, packed, snap);
    }

    private void emit(float x, float y, float u, float v, int abgr, boolean snap) {
        float tx = transform.transformX(x, y);
        float ty = transform.transformY(x, y);
        if (snap && snapStep > 0f) {
            tx = Math.round(tx / snapStep) * snapStep;
            ty = Math.round(ty / snapStep) * snapStep;
        }
        batcher.vertex(tx, ty, u, v, abgr);
    }

    private void useWhite() {
        batcher.texture(graphics.whiteTexture().handle());
    }

    private void useTexture(Texture texture) {
        TextureImpl impl = texture instanceof TextureImpl t && !t.isDisposed() ? t : graphics.fallbackTexture();
        batcher.texture(impl.handle());
    }

    /** Multiplies two packed premultiplied colors channel by channel. */
    static int multiply(int a, int b) {
        if (b == 0xffffffff) {
            return a;
        }
        int r = ((a & 0xff) * (b & 0xff) + 127) / 255;
        int g = (((a >>> 8) & 0xff) * ((b >>> 8) & 0xff) + 127) / 255;
        int bl = (((a >>> 16) & 0xff) * ((b >>> 16) & 0xff) + 127) / 255;
        int al = (((a >>> 24) & 0xff) * ((b >>> 24) & 0xff) + 127) / 255;
        return (al << 24) | (bl << 16) | (g << 8) | r;
    }

    // ------------------------------------------------------------------ state

    @Override
    public Draw push() {
        if (depth == savedTransforms.length) {
            int size = depth * 2;
            savedTransforms = Arrays.copyOf(savedTransforms, size);
            for (int i = depth; i < size; i++) {
                savedTransforms[i] = new Affine2();
            }
            savedColors = Arrays.copyOf(savedColors, size);
            savedAlphas = Arrays.copyOf(savedAlphas, size);
            savedMaterials = Arrays.copyOf(savedMaterials, size);
        }
        savedTransforms[depth].set(transform);
        savedColors[depth] = color;
        savedAlphas[depth] = alpha;
        savedMaterials[depth] = material;
        depth++;
        return this;
    }

    @Override
    public Draw pop() {
        if (depth == 0) {
            throw new IllegalStateException("Draw.pop() without a matching push()");
        }
        depth--;
        transform.set(savedTransforms[depth]);
        color = savedColors[depth];
        alpha = savedAlphas[depth];
        updatePacked();
        material(savedMaterials[depth]);
        return this;
    }

    @Override
    public Draw translate(float x, float y) {
        transform.translate(x, y);
        return this;
    }

    @Override
    public Draw rotate(float degrees) {
        transform.rotate(degrees);
        return this;
    }

    @Override
    public Draw scale(float sx, float sy) {
        transform.scale(sx, sy);
        return this;
    }

    @Override
    public Draw color(Color newColor) {
        this.color = newColor;
        updatePacked();
        return this;
    }

    @Override
    public Color color() {
        return color;
    }

    @Override
    public Draw alpha(float newAlpha) {
        this.alpha = Mathf.clamp(newAlpha, 0f, 1f);
        updatePacked();
        return this;
    }

    private void updatePacked() {
        float a = color.a() * alpha;
        packed = (Math.round(a * 255f) << 24)
                | (Math.round(color.b() * a * 255f) << 16)
                | (Math.round(color.g() * a * 255f) << 8)
                | Math.round(color.r() * a * 255f);
    }

    @Override
    public Draw material(Material newMaterial) {
        this.material = newMaterial;
        Shader shader = newMaterial.shader();
        batcher.shader(shader instanceof ShaderImpl impl ? impl : graphics.defaultShader());
        batcher.blend(newMaterial.blend());
        for (int unit = 1; unit < 8; unit++) {
            Texture extra = newMaterial.textures().get(unit);
            batcher.extraTexture(unit, extra instanceof TextureImpl t && !t.isDisposed() ? t.handle() : 0);
        }
        return this;
    }

    Map<Integer, Texture> materialTextures() {
        return material.textures();
    }

    @Override
    public Draw clip(Rect rect, Runnable body) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float[] corners = {
            rect.x(), rect.y(), rect.right(), rect.y(), rect.right(), rect.bottom(), rect.x(), rect.bottom()
        };
        for (int i = 0; i < 8; i += 2) {
            float wx = transform.transformX(corners[i], corners[i + 1]);
            float wy = transform.transformY(corners[i], corners[i + 1]);
            float cx = projection.transformX(wx, wy);
            float cy = projection.transformY(wx, wy);
            float px = viewportX + (cx + 1f) / 2f * viewportWidth;
            float py = viewportY + (1f - cy) / 2f * viewportHeight;
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
        }
        int x0 = Math.round(minX);
        int y0 = Math.round(minY);
        int x1 = Math.round(maxX);
        int y1 = Math.round(maxY);
        if (clipDepth > 0) {
            int o = (clipDepth - 1) * 4;
            x0 = Math.max(x0, clips[o]);
            y0 = Math.max(y0, clips[o + 1]);
            x1 = Math.min(x1, clips[o + 2]);
            y1 = Math.min(y1, clips[o + 3]);
        }
        if (clipDepth >= MAX_CLIPS) {
            throw new IllegalStateException("Clips nested deeper than " + MAX_CLIPS);
        }
        batcher.flush();
        int o = clipDepth * 4;
        clips[o] = x0;
        clips[o + 1] = y0;
        clips[o + 2] = Math.max(x0, x1);
        clips[o + 3] = Math.max(y0, y1);
        clipDepth++;
        applyScissor();
        try {
            body.run();
        } finally {
            batcher.flush();
            clipDepth--;
            applyScissor();
        }
        return this;
    }

    private void applyScissor() {
        if (clipDepth == 0) {
            gl.disable(Gl.SCISSOR_TEST);
            return;
        }
        int o = (clipDepth - 1) * 4;
        gl.enable(Gl.SCISSOR_TEST);
        gl.scissor(clips[o], targetHeight - clips[o + 3], clips[o + 2] - clips[o], clips[o + 3] - clips[o + 1]);
    }

    /**
     * Returns the current clip in target pixels, for tests.
     *
     * @return {@code x0, y0, x1, y1}, or an empty array without a clip
     */
    int[] currentClip() {
        if (clipDepth == 0) {
            return new int[0];
        }
        int o = (clipDepth - 1) * 4;
        return Arrays.copyOfRange(clips, o, o + 4);
    }

    @Override
    public Draw into(FrameBuffer target, Consumer<Draw> body) {
        if (!(target instanceof FrameBufferImpl buffer) || buffer.isDisposed()) {
            throw new IllegalArgumentException("Frame buffer was not created by this engine or is disposed");
        }
        batcher.flush();
        Affine2 savedProjection = new Affine2().set(projection);
        float savedPixel = unitsPerPixel;
        float savedTexel = unitsPerTexel;
        float savedSnap = snapStep;
        int[] savedViewport = {viewportX, viewportY, viewportWidth, viewportHeight, targetHeight, framebuffer};
        int savedClipDepth = clipDepth;
        Affine2 savedTransform = new Affine2().set(transform);
        Color savedColor = color;
        float savedAlpha = alpha;
        Material savedMaterial = material;
        int savedDepth = depth;

        int w = buffer.width();
        int h = buffer.height();
        gl.bindFramebuffer(Gl.FRAMEBUFFER, buffer.handle());
        gl.disable(Gl.SCISSOR_TEST);
        gl.viewport(0, 0, w, h);
        gl.clearColor(0f, 0f, 0f, 0f);
        gl.clear(Gl.COLOR_BUFFER_BIT);
        begin(
                new Affine2().scale(2f / w, -2f / h).translate(-w / 2f, -h / 2f),
                1f,
                1f,
                0f,
                0,
                0,
                w,
                h,
                h,
                buffer.handle());
        try {
            body.accept(this);
        } finally {
            batcher.flush();
            gl.bindFramebuffer(Gl.FRAMEBUFFER, savedViewport[5]);
            gl.viewport(
                    savedViewport[0],
                    savedViewport[4] - savedViewport[1] - savedViewport[3],
                    savedViewport[2],
                    savedViewport[3]);
            begin(
                    savedProjection,
                    savedPixel,
                    savedTexel,
                    savedSnap,
                    savedViewport[0],
                    savedViewport[1],
                    savedViewport[2],
                    savedViewport[3],
                    savedViewport[4],
                    savedViewport[5]);
            transform.set(savedTransform);
            depth = savedDepth;
            color(savedColor);
            alpha(savedAlpha);
            material(savedMaterial);
            clipDepth = savedClipDepth;
            applyScissor();
        }
        return this;
    }

    @Override
    public float pixelSize() {
        float scale = transform.averageScale();
        return scale == 0f ? unitsPerPixel : unitsPerPixel / scale;
    }

    /**
     * Current transform, for tests.
     *
     * @return the live transform
     */
    Affine2 transform() {
        return transform;
    }
}
