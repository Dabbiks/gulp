package dev.gulp.core.graphics;

import dev.gulp.api.graphics.BlendMode;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Material;
import dev.gulp.api.graphics.Shader;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureFilter;
import dev.gulp.api.math.Affine2;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.render.Bloom;
import dev.gulp.api.render.Blur;
import dev.gulp.api.render.ChromaticAberration;
import dev.gulp.api.render.ColorGrade;
import dev.gulp.api.render.Crt;
import dev.gulp.api.render.CustomEffect;
import dev.gulp.api.render.Pixelate;
import dev.gulp.api.render.PostEffect;
import dev.gulp.api.render.Vignette;
import dev.gulp.platform.Gl;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Runs a post-processing chain: every enabled effect is a full-screen pass from one frame buffer into the next, and the
 * last pass writes into the destination rectangle. Bloom and blur use extra passes at lower resolution. Buffers are
 * kept between frames and remade when the size changes.
 */
final class PostProcessor {

    private final Gl gl;
    private final GraphicsImpl graphics;
    private final DrawImpl draw;
    private final Affine2 projection = new Affine2();
    private @Nullable FrameBufferImpl a;
    private @Nullable FrameBufferImpl b;
    private @Nullable FrameBufferImpl c;
    private @Nullable FrameBufferImpl halfA;
    private @Nullable FrameBufferImpl halfB;
    private final Material replace = Material.DEFAULT.withBlend(BlendMode.REPLACE);
    private final Material add = Material.DEFAULT.withBlend(BlendMode.ADD);

    private int destination;
    private int destX;
    private int destY;
    private int destWidth;
    private int destHeight;
    private int destTargetHeight;

    PostProcessor(Gl gl, GraphicsImpl graphics, DrawImpl draw) {
        this.gl = gl;
        this.graphics = graphics;
        this.draw = draw;
    }

    /**
     * Applies a chain.
     *
     * @param effects the chain; disabled effects are skipped
     * @param source the picture to process; not written
     * @param target the destination frame buffer handle, 0 for the window
     * @param x destination left in target pixels
     * @param y destination top in target pixels
     * @param width destination width
     * @param height destination height
     * @param targetHeight height of the destination frame buffer
     * @param time seconds since start, for animated shaders
     */
    void apply(
            List<PostEffect> effects,
            FrameBufferImpl source,
            int target,
            int x,
            int y,
            int width,
            int height,
            int targetHeight,
            float time) {
        destination = target;
        destX = x;
        destY = y;
        destWidth = width;
        destHeight = height;
        destTargetHeight = targetHeight;
        int w = source.width();
        int h = source.height();
        ensure(w, h);
        int last = -1;
        for (int i = 0; i < effects.size(); i++) {
            if (effects.get(i).isEnabled()) {
                last = i;
            }
        }
        FrameBufferImpl current = source;
        if (last < 0) {
            pass(current, null, replace, null);
            return;
        }
        Vec2 resolution = new Vec2(w, h);
        for (int i = 0; i <= last; i++) {
            PostEffect effect = effects.get(i);
            if (!effect.isEnabled()) {
                continue;
            }
            FrameBufferImpl out = i == last ? null : (current == a ? b : a);
            switch (effect) {
                case Bloom bloom -> {
                    ShaderImpl bright = graphics.internalShader("gulp:post/bright", PostShaders.BRIGHT);
                    bright.set("u_threshold", bloom.threshold());
                    bright.set("u_intensity", bloom.intensity());
                    pass(current, halfA, replace, bright);
                    blur(halfA, halfB, halfA, bloom.radius() * 2f);
                    pass(current, out, replace, null);
                    pass(halfA, out, add, null);
                }
                case Blur blur -> {
                    blur(current, c, out, blur.radius());
                }
                case Vignette vignette -> {
                    ShaderImpl shader = graphics.internalShader("gulp:post/vignette", PostShaders.VIGNETTE);
                    shader.set("u_intensity", vignette.intensity());
                    shader.set("u_radius", vignette.radius());
                    shader.set("u_softness", Math.max(0.001f, vignette.softness()));
                    shader.set("u_color", vignette.color());
                    pass(current, out, replace, shader);
                }
                case ColorGrade grade -> {
                    Texture lut = grade.lut();
                    if (lut == null) {
                        pass(current, out, replace, null);
                    } else {
                        ShaderImpl shader = graphics.internalShader("gulp:post/grade", PostShaders.COLOR_GRADE);
                        shader.set("u_lut", 1);
                        shader.set("u_intensity", grade.intensity());
                        pass(current, out, replace.withTexture(1, lut), shader);
                    }
                }
                case Pixelate pixelate -> {
                    ShaderImpl shader = graphics.internalShader("gulp:post/pixelate", PostShaders.PIXELATE);
                    shader.set("u_size", pixelate.size());
                    shader.set("u_resolution", resolution);
                    pass(current, out, replace, shader);
                }
                case ChromaticAberration aberration -> {
                    ShaderImpl shader = graphics.internalShader("gulp:post/chromatic", PostShaders.CHROMATIC);
                    shader.set("u_amount", aberration.amount());
                    shader.set("u_resolution", resolution);
                    pass(current, out, replace, shader);
                }
                case Crt crt -> {
                    ShaderImpl shader = graphics.internalShader("gulp:post/crt", PostShaders.CRT);
                    shader.set("u_curvature", crt.curvature());
                    shader.set("u_scanlines", crt.scanlines());
                    shader.set("u_resolution", resolution);
                    pass(current, out, replace, shader);
                }
                case CustomEffect custom -> {
                    Shader shader = custom.shader();
                    shader.set("u_resolution", resolution);
                    shader.set("u_time", time);
                    pass(current, out, replace, shader instanceof ShaderImpl impl ? impl : null);
                }
            }
            if (out != null) {
                current = out;
            }
        }
    }

    /** Two blur passes: horizontal from {@code from} into {@code middle}, vertical into {@code to}. */
    private void blur(
            @Nullable FrameBufferImpl from,
            @Nullable FrameBufferImpl middle,
            @Nullable FrameBufferImpl to,
            float radius) {
        ShaderImpl shader = graphics.internalShader("gulp:post/blur", PostShaders.BLUR);
        float spread = Math.max(0f, radius) / 4f;
        shader.set("u_direction", new Vec2(spread, 0f));
        pass(from, middle, replace, shader);
        shader.set("u_direction", new Vec2(0f, spread));
        pass(middle, to, replace, shader);
    }

    /** Draws a buffer over a whole target: a frame buffer, or the destination when {@code to} is {@code null}. */
    private void pass(
            @Nullable FrameBufferImpl from,
            @Nullable FrameBufferImpl to,
            Material material,
            @Nullable ShaderImpl shader) {
        if (from == null) {
            return;
        }
        int handle;
        int x;
        int y;
        int w;
        int h;
        int targetHeight;
        if (to != null) {
            handle = to.handle();
            x = 0;
            y = 0;
            w = to.width();
            h = to.height();
            targetHeight = h;
        } else {
            handle = destination;
            x = destX;
            y = destY;
            w = destWidth;
            h = destHeight;
            targetHeight = destTargetHeight;
        }
        gl.bindFramebuffer(Gl.FRAMEBUFFER, handle);
        gl.viewport(x, targetHeight - y - h, w, h);
        projection.identity().scale(2f / w, -2f / h).translate(-w / 2f, -h / 2f);
        draw.begin(projection, 1f, 1f, 0f, x, y, w, h, targetHeight, handle);
        draw.material(shader == null ? material : material.withShader(shader));
        draw.color(Color.WHITE);
        draw.image(from.region(), 0, 0, w, h);
        draw.flush();
        draw.material(Material.DEFAULT);
    }

    private void ensure(int w, int h) {
        a = sized(a, w, h);
        b = sized(b, w, h);
        c = sized(c, w, h);
        halfA = sized(halfA, Math.max(1, w / 2), Math.max(1, h / 2));
        halfB = sized(halfB, Math.max(1, w / 2), Math.max(1, h / 2));
    }

    private FrameBufferImpl sized(@Nullable FrameBufferImpl buffer, int w, int h) {
        if (buffer != null && buffer.width() == w && buffer.height() == h && !buffer.isDisposed()) {
            return buffer;
        }
        if (buffer != null) {
            buffer.dispose();
        }
        return new FrameBufferImpl(gl, w, h, false, TextureFilter.LINEAR);
    }

    void dispose() {
        for (FrameBufferImpl buffer : new FrameBufferImpl[] {a, b, c, halfA, halfB}) {
            if (buffer != null) {
                buffer.dispose();
            }
        }
    }
}
