package dev.gulp.core.graphics;

import dev.gulp.api.asset.LoadingScreen;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Material;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.TextureFilter;
import dev.gulp.api.math.Affine2;
import dev.gulp.api.math.Rect;
import dev.gulp.api.render.PostRenderEvent;
import dev.gulp.api.render.PreRenderEvent;
import dev.gulp.api.render.RenderLayer;
import dev.gulp.api.render.RenderLayerEvent;
import dev.gulp.api.render.StretchMode;
import dev.gulp.api.ui.Transition;
import dev.gulp.api.ui.TransitionFrame;
import dev.gulp.core.asset.AssetsImpl;
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
    private @Nullable FrameBufferImpl captureBuffer;
    private @Nullable WorldView worldView;
    private boolean transitionErrorLogged;
    private @Nullable LoadingScreen loadingScreen;
    private float loadingProgress;

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
     * Draws a loading screen in screen space on the following frames, instead of the game.
     *
     * @param screen the loading screen
     * @param progress loading progress from 0 to 1
     */
    public void showLoading(LoadingScreen screen, float progress) {
        this.loadingScreen = screen;
        this.loadingProgress = progress;
    }

    /** Stops drawing the loading screen. */
    public void hideLoading() {
        this.loadingScreen = null;
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
        boolean snap = display.isPixelSnap();
        boolean layerListeners = running && events.hasListeners(RenderLayerEvent.class);
        WorldView view = running ? worldView : null;
        Transition transition = view != null ? view.transition() : null;
        int drawFramebuffer = targetFramebuffer;
        int drawHeight = targetHeight;
        int dx = tx;
        int dy = ty;
        FrameBufferImpl captured = null;
        if (transition != null && transition.needsFrame()) {
            captured = capture(tw, th);
            drawFramebuffer = captured.handle();
            drawHeight = th;
            dx = 0;
            dy = 0;
            gl.bindFramebuffer(Gl.FRAMEBUFFER, drawFramebuffer);
            gl.viewport(0, 0, tw, th);
            gl.clearColor(
                    clearColor.r() * clearColor.a(),
                    clearColor.g() * clearColor.a(),
                    clearColor.b() * clearColor.a(),
                    clearColor.a());
            gl.clear(Gl.COLOR_BUFFER_BIT);
        }
        List<RenderLayer> worldLayers = view != null ? view.worldLayers() : null;
        if (worldLayers != null) {
            List<CameraImpl> cameras = view.cameras();
            for (int c = 0; c < cameras.size(); c++) {
                CameraImpl camera = cameras.get(c);
                Rect area = camera.viewport();
                int sx = dx + Math.round(area.x() * tw);
                int sy = dy + Math.round(area.y() * th);
                int sw = Math.max(1, Math.round(area.width() * tw));
                int sh = Math.max(1, Math.round(area.height() * th));
                float cw = lw * area.width();
                float ch = lh * area.height();
                camera.resize(cw, ch);
                float cameraPixel = cw / sw;
                float worldPixel = cameraPixel / camera.unitScale();
                for (int i = 0; i < worldLayers.size(); i++) {
                    RenderLayer layer = worldLayers.get(i);
                    if (!layer.isVisible()) {
                        continue;
                    }
                    camera.projection(
                            projection, layer.parallax().x(), layer.parallax().y());
                    draw.begin(
                            projection,
                            worldPixel,
                            1f / display.pixelsPerUnit(),
                            snap ? worldPixel : 0f,
                            sx,
                            sy,
                            sw,
                            sh,
                            drawHeight,
                            drawFramebuffer);
                    draw.material(layer.material());
                    view.drawLayer(draw, layer, camera, alpha);
                    if (layerListeners) {
                        events.call(new RenderLayerEvent(draw, layer, alpha));
                    }
                    draw.flush();
                }
                screenProjection(cw, ch);
                draw.begin(
                        projection,
                        cameraPixel,
                        1f,
                        snap ? cameraPixel : 0f,
                        sx,
                        sy,
                        sw,
                        sh,
                        drawHeight,
                        drawFramebuffer);
                view.drawOverlay(draw, camera, alpha);
                draw.flush();
            }
        }
        CameraImpl camera = display.cameraImpl();
        float unitScale = camera.unitScale();
        List<RenderLayer> layers = display.layers();
        for (int i = 0; i < layers.size(); i++) {
            RenderLayer layer = layers.get(i);
            if (!layer.isVisible() || !layerListeners || (worldLayers != null && !layer.isScreenSpace())) {
                continue;
            }
            if (layer.isScreenSpace()) {
                screenProjection(lw, lh);
                draw.begin(
                        projection,
                        screenPixel,
                        1f,
                        snap ? screenPixel : 0f,
                        dx,
                        dy,
                        tw,
                        th,
                        drawHeight,
                        drawFramebuffer);
            } else {
                camera.projection(
                        projection, layer.parallax().x(), layer.parallax().y());
                float worldPixel = screenPixel / unitScale;
                draw.begin(
                        projection,
                        worldPixel,
                        1f / display.pixelsPerUnit(),
                        snap ? worldPixel : 0f,
                        dx,
                        dy,
                        tw,
                        th,
                        drawHeight,
                        drawFramebuffer);
            }
            draw.material(layer.material());
            events.call(new RenderLayerEvent(draw, layer, alpha));
            draw.flush();
        }
        if (running && events.hasListeners(PostRenderEvent.class)) {
            screenProjection(lw, lh);
            draw.begin(
                    projection, screenPixel, 1f, snap ? screenPixel : 0f, dx, dy, tw, th, drawHeight, drawFramebuffer);
            events.call(new PostRenderEvent(draw));
            draw.flush();
        }
        if (transition != null && view != null) {
            screenProjection(lw, lh);
            if (captured != null) {
                gl.bindFramebuffer(Gl.FRAMEBUFFER, targetFramebuffer);
                gl.viewport(tx, targetHeight - ty - th, tw, th);
                draw.begin(projection, screenPixel, 1f, 0f, tx, ty, tw, th, targetHeight, targetFramebuffer);
                draw.image(captured.region(), 0, 0, lw, lh);
            } else {
                draw.begin(projection, screenPixel, 1f, 0f, tx, ty, tw, th, targetHeight, targetFramebuffer);
            }
            try {
                transition.draw(
                        draw,
                        new TransitionFrame(
                                lw, lh, view.coverage(), view.entering(), captured != null ? captured.region() : null));
            } catch (RuntimeException error) {
                transitionFailed(error);
            }
            draw.material(Material.DEFAULT);
            draw.flush();
        }

        LoadingScreen loading = loadingScreen;
        if (loading != null) {
            screenProjection(lw, lh);
            draw.begin(projection, screenPixel, 1f, 0f, tx, ty, tw, th, targetHeight, targetFramebuffer);
            try {
                loading.draw(draw, display, loadingProgress);
            } catch (RuntimeException error) {
                // A broken custom screen must not stop loading; fall back to the default one.
                loadingScreen = AssetsImpl.DEFAULT_LOADING_SCREEN;
            }
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

    private FrameBufferImpl capture(int width, int height) {
        FrameBufferImpl buffer = captureBuffer;
        if (buffer == null || buffer.width() != width || buffer.height() != height) {
            if (buffer != null) {
                buffer.dispose();
            }
            buffer = new FrameBufferImpl(gl, width, height, false, TextureFilter.LINEAR);
            captureBuffer = buffer;
        }
        return buffer;
    }

    private void transitionFailed(RuntimeException error) {
        if (!transitionErrorLogged) {
            transitionErrorLogged = true;
            System.err.println("A transition failed to draw: " + error);
        }
    }

    /**
     * Connects the worlds, whose active world is drawn through its own layers and cameras.
     *
     * @param view the worlds, or {@code null}
     */
    public void setWorldView(@Nullable WorldView view) {
        this.worldView = view;
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
        if (captureBuffer != null) {
            captureBuffer.dispose();
            captureBuffer = null;
        }
    }
}
