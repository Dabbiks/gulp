package dev.gulp.core.graphics;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.TextureFilter;
import dev.gulp.api.math.Affine2;
import dev.gulp.api.render.PostRenderEvent;
import dev.gulp.api.render.PreRenderEvent;
import dev.gulp.api.render.RenderLayer;
import dev.gulp.api.render.RenderLayerEvent;
import dev.gulp.api.render.StretchMode;
import dev.gulp.core.event.EventBus;
import dev.gulp.core.scheduler.PromiseImpl;
import dev.gulp.platform.Gl;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Draws one frame: letterbox, then every visible layer through {@link RenderLayerEvent}, then the screen overlay
 * through {@link PostRenderEvent}. In {@link StretchMode#VIEWPORT} the frame is drawn at the logical resolution into
 * an offscreen buffer and scaled up with nearest filtering.
 */
public final class Renderer {

    private final Gl gl;
    private final GraphicsImpl graphics;
    private final DisplayImpl display;
    private final EventBus events;
    private final Batcher batcher;
    private final DrawImpl draw;
    private final Affine2 projection = new Affine2();
    private @Nullable FrameBufferImpl offscreen;

    /**
     * Creates the renderer.
     *
     * @param graphics the graphics factory
     * @param display the display settings
     * @param events where render events go
     */
    public Renderer(GraphicsImpl graphics, DisplayImpl display, EventBus events) {
        this.gl = graphics.gl();
        this.graphics = graphics;
        this.display = display;
        this.events = events;
        this.batcher = new Batcher(gl, graphics.defaultShader());
        this.draw = new DrawImpl(gl, batcher, graphics);
    }

    /**
     * The draw used for rendering, for tests.
     *
     * @return the draw
     */
    public DrawImpl draw() {
        return draw;
    }

    /**
     * The batcher, for tests.
     *
     * @return the batcher
     */
    public Batcher batcher() {
        return batcher;
    }

    /**
     * Renders a frame.
     *
     * @param framebufferWidth window framebuffer width
     * @param framebufferHeight window framebuffer height
     * @param contentScale window content scale
     * @param clearColor the game background
     * @param alpha interpolation between ticks
     * @param running whether game events may be fired
     * @param nanoTime frame time, for statistics
     */
    public void render(
            int framebufferWidth,
            int framebufferHeight,
            float contentScale,
            Color clearColor,
            float alpha,
            boolean running,
            long nanoTime) {
        int fw = Math.max(1, framebufferWidth);
        int fh = Math.max(1, framebufferHeight);
        // First, so display and camera changes made by listeners apply to this frame.
        if (running && events.hasListeners(PreRenderEvent.class)) {
            events.call(new PreRenderEvent(alpha));
        }
        DisplayLayout layout = display.update(fw, fh, contentScale);
        batcher.beginFrame();

        gl.bindFramebuffer(Gl.FRAMEBUFFER, 0);
        gl.disable(Gl.SCISSOR_TEST);
        gl.viewport(0, 0, fw, fh);
        Color letterbox = display.letterboxColor();
        gl.clearColor(letterbox.r(), letterbox.g(), letterbox.b(), 1f);
        gl.clear(Gl.COLOR_BUFFER_BIT);

        int vx = layout.viewportX();
        int vy = layout.viewportY();
        int vw = Math.max(1, layout.viewportWidth());
        int vh = Math.max(1, layout.viewportHeight());
        float lw = layout.logicalWidth();
        float lh = layout.logicalHeight();

        boolean offscreenPass = display.stretchMode() == StretchMode.VIEWPORT;
        int targetFramebuffer = 0;
        int targetHeight = fh;
        int tx = vx;
        int ty = vy;
        int tw = vw;
        int th = vh;
        if (offscreenPass) {
            FrameBufferImpl buffer = offscreen(Math.max(1, Math.round(lw)), Math.max(1, Math.round(lh)));
            targetFramebuffer = buffer.handle();
            gl.bindFramebuffer(Gl.FRAMEBUFFER, targetFramebuffer);
            tx = 0;
            ty = 0;
            tw = buffer.width();
            th = buffer.height();
            targetHeight = th;
        }
        gl.viewport(tx, targetHeight - ty - th, tw, th);
        gl.enable(Gl.SCISSOR_TEST);
        gl.scissor(tx, targetHeight - ty - th, tw, th);
        gl.clearColor(
                clearColor.r() * clearColor.a(),
                clearColor.g() * clearColor.a(),
                clearColor.b() * clearColor.a(),
                clearColor.a());
        gl.clear(Gl.COLOR_BUFFER_BIT);
        gl.disable(Gl.SCISSOR_TEST);

        float screenPixel = lw / tw;
        CameraImpl camera = display.cameraImpl();
        float unitScale = camera.unitScale();
        boolean snap = display.isPixelSnap();
        boolean layerListeners = running && events.hasListeners(RenderLayerEvent.class);
        List<RenderLayer> layers = display.layers();
        for (int i = 0; i < layers.size(); i++) {
            RenderLayer layer = layers.get(i);
            if (!layer.isVisible() || !layerListeners) {
                continue;
            }
            if (layer.isScreenSpace()) {
                screenProjection(lw, lh);
                draw.begin(
                        projection,
                        screenPixel,
                        1f,
                        snap ? screenPixel : 0f,
                        tx,
                        ty,
                        tw,
                        th,
                        targetHeight,
                        targetFramebuffer);
            } else {
                camera.projection(
                        projection, layer.parallax().x(), layer.parallax().y());
                float worldPixel = screenPixel / unitScale;
                draw.begin(
                        projection,
                        worldPixel,
                        1f / display.pixelsPerUnit(),
                        snap ? worldPixel : 0f,
                        tx,
                        ty,
                        tw,
                        th,
                        targetHeight,
                        targetFramebuffer);
            }
            draw.material(layer.material());
            events.call(new RenderLayerEvent(draw, layer, alpha));
            draw.flush();
        }
        if (running && events.hasListeners(PostRenderEvent.class)) {
            screenProjection(lw, lh);
            draw.begin(
                    projection,
                    screenPixel,
                    1f,
                    snap ? screenPixel : 0f,
                    tx,
                    ty,
                    tw,
                    th,
                    targetHeight,
                    targetFramebuffer);
            events.call(new PostRenderEvent(draw));
            draw.flush();
        }

        if (offscreenPass && offscreen != null) {
            gl.bindFramebuffer(Gl.FRAMEBUFFER, 0);
            gl.viewport(vx, fh - vy - vh, vw, vh);
            screenProjection(lw, lh);
            draw.begin(projection, lw / vw, 1f, 0f, vx, vy, vw, vh, fh, 0);
            draw.image(offscreen.region(), 0, 0, lw, lh);
            draw.flush();
        }
        gl.disable(Gl.SCISSOR_TEST);
        completeScreenshots(fw, fh);
        display.frameDone(nanoTime, batcher);
    }

    private void screenProjection(float lw, float lh) {
        projection.identity().scale(2f / lw, -2f / lh).translate(-lw / 2f, -lh / 2f);
    }

    private FrameBufferImpl offscreen(int width, int height) {
        FrameBufferImpl buffer = offscreen;
        if (buffer == null || buffer.width() != width || buffer.height() != height) {
            if (buffer != null) {
                buffer.dispose();
            }
            buffer = new FrameBufferImpl(gl, width, height, false, TextureFilter.NEAREST);
            offscreen = buffer;
        }
        return buffer;
    }

    private void completeScreenshots(int width, int height) {
        List<PromiseImpl<Pixmap>> pending = display.takeScreenshots();
        if (pending.isEmpty()) {
            return;
        }
        Pixmap image;
        try {
            image = readPixels(width, height);
        } catch (RuntimeException error) {
            for (PromiseImpl<Pixmap> promise : pending) {
                promise.fail(error);
            }
            return;
        }
        for (PromiseImpl<Pixmap> promise : pending) {
            promise.complete(image);
        }
    }

    /**
     * Reads the window framebuffer as an opaque image, top row first.
     *
     * @param width framebuffer width
     * @param height framebuffer height
     * @return the image
     */
    Pixmap readPixels(int width, int height) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
        gl.bindFramebuffer(Gl.FRAMEBUFFER, 0);
        gl.pixelStorei(Gl.PACK_ALIGNMENT, 1);
        gl.readPixels(0, 0, width, height, Gl.RGBA, Gl.UNSIGNED_BYTE, buffer);
        byte[] rgba = new byte[width * height * 4];
        int row = width * 4;
        for (int y = 0; y < height; y++) {
            buffer.position((height - 1 - y) * row);
            buffer.get(rgba, y * row, row);
        }
        for (int i = 3; i < rgba.length; i += 4) {
            rgba[i] = (byte) 0xff;
        }
        return Pixmap.fromRgba(width, height, rgba);
    }

    /** Frees the batcher and the offscreen buffer; textures and shaders are freed by {@link GraphicsImpl}. */
    public void dispose() {
        batcher.dispose();
        if (offscreen != null) {
            offscreen.dispose();
            offscreen = null;
        }
    }
}
