package dev.gulp.core.graphics;

import dev.gulp.api.GameSettings;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.math.Rect;
import dev.gulp.api.render.AspectMode;
import dev.gulp.api.render.Camera;
import dev.gulp.api.render.Display;
import dev.gulp.api.render.RenderLayer;
import dev.gulp.api.render.RenderStats;
import dev.gulp.api.render.StretchMode;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.core.scheduler.PromiseImpl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * {@link Display}: layout settings, layers, the camera and frame statistics. The {@link Renderer} reads it every
 * frame. The display owns the default layers, used while no world is active; an active world brings its own world layers and cameras.
 */
public final class DisplayImpl implements Display, dev.gulp.core.input.InputImpl.PointMapper {

    /** Default world layers, back to front. */
    public static final List<String> WORLD_LAYERS = List.of("background", "tiles", "entities", "foreground", "effects");

    /** Default screen layers, back to front. */
    public static final List<String> SCREEN_LAYERS = List.of("ui", "overlay");

    private static final Comparator<RenderLayer> ORDER = Comparator.comparingInt(RenderLayer::zOrder);

    private final CameraImpl camera;
    private @org.jspecify.annotations.Nullable CameraImpl worldCamera;
    private final Supplier<PromiseImpl<Pixmap>> promises;
    private final List<RenderLayer> layers = new ArrayList<>();
    private final List<RenderLayer> layersView = Collections.unmodifiableList(layers);
    private final List<PromiseImpl<Pixmap>> screenshots = new ArrayList<>();

    private int baseWidth;
    private int baseHeight;
    private StretchMode stretchMode;
    private AspectMode aspectMode;
    private boolean integerScaling;
    private boolean pixelSnap;
    private Color letterboxColor;
    private final int pixelsPerUnit;

    private DisplayLayout layout = new DisplayLayout(0, 0, 1, 1, 1, 1);
    private int framebufferWidth = -1;
    private int framebufferHeight = -1;
    private float contentScale = 1f;
    private boolean dirty = true;

    private long fpsWindowStart = -1;
    private int fpsFrames;
    private float fps;
    private int drawCalls;
    private int textureBinds;
    private int vertices;
    private int flushes;

    /**
     * Creates the display from the game settings.
     *
     * @param settings the settings
     * @param promises creates screenshot promises owned by the game
     */
    public DisplayImpl(GameSettings settings, Supplier<PromiseImpl<Pixmap>> promises) {
        this.promises = promises;
        this.baseWidth = settings.baseWidth();
        this.baseHeight = settings.baseHeight();
        this.stretchMode = settings.stretchMode();
        this.aspectMode = settings.aspectMode();
        this.integerScaling = settings.isIntegerScaling();
        this.pixelSnap = settings.isPixelSnap();
        this.letterboxColor = settings.letterboxColor();
        this.pixelsPerUnit = settings.pixelsPerUnit();
        this.camera = new CameraImpl(pixelsPerUnit);
        for (int i = 0; i < WORLD_LAYERS.size(); i++) {
            addLayer(WORLD_LAYERS.get(i), i * 100, false);
        }
        for (int i = 0; i < SCREEN_LAYERS.size(); i++) {
            addLayer(SCREEN_LAYERS.get(i), 1000 + i * 100, true);
        }
    }

    // ------------------------------------------------------------------ used by the renderer

    /**
     * Recomputes the layout if the framebuffer or a setting changed.
     *
     * @param width framebuffer width in pixels
     * @param height framebuffer height in pixels
     * @param scale content scale of the window
     * @return the current layout
     */
    public DisplayLayout update(int width, int height, float scale) {
        if (dirty || width != framebufferWidth || height != framebufferHeight || scale != contentScale) {
            framebufferWidth = width;
            framebufferHeight = height;
            contentScale = scale;
            layout = DisplayLayout.compute(
                    width, height, scale, baseWidth, baseHeight, stretchMode, aspectMode, integerScaling);
            camera.resize(layout.logicalWidth(), layout.logicalHeight());
            CameraImpl current = worldCamera;
            if (current != null) {
                current.resize(layout.logicalWidth(), layout.logicalHeight());
            }
            dirty = false;
        }
        return layout;
    }

    /**
     * Current layout.
     *
     * @return the layout from the last {@link #update}
     */
    public DisplayLayout layout() {
        return layout;
    }

    /**
     * The camera implementation.
     *
     * @return the camera
     */
    public CameraImpl cameraImpl() {
        CameraImpl current = worldCamera;
        return current != null ? current : camera;
    }

    /**
     * Logical pixels per world unit at zoom 1.
     *
     * @return the value from the settings
     */
    public int pixelsPerUnit() {
        return pixelsPerUnit;
    }

    /**
     * Takes the screenshot requests made so far.
     *
     * @return the pending requests; empty when there are none
     */
    public List<PromiseImpl<Pixmap>> takeScreenshots() {
        if (screenshots.isEmpty()) {
            return List.of();
        }
        List<PromiseImpl<Pixmap>> taken = new ArrayList<>(screenshots);
        screenshots.clear();
        return taken;
    }

    /**
     * Records the statistics of a finished frame.
     *
     * @param nanoTime frame time
     * @param batcher the batcher with this frame's counters
     */
    public void frameDone(long nanoTime, Batcher batcher) {
        drawCalls = batcher.drawCalls();
        textureBinds = batcher.textureBinds();
        vertices = batcher.vertices();
        flushes = batcher.flushes();
        if (fpsWindowStart < 0) {
            fpsWindowStart = nanoTime;
        }
        fpsFrames++;
        long window = nanoTime - fpsWindowStart;
        if (window >= 1_000_000_000L) {
            fps = fpsFrames * 1e9f / window;
            fpsFrames = 0;
            fpsWindowStart = nanoTime;
        }
    }

    // ------------------------------------------------------------------ Display

    @Override
    public float width() {
        return layout.logicalWidth();
    }

    @Override
    public float height() {
        return layout.logicalHeight();
    }

    @Override
    public int baseWidth() {
        return baseWidth;
    }

    @Override
    public int baseHeight() {
        return baseHeight;
    }

    @Override
    public void setBaseResolution(int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Base resolution must be positive: " + width + "x" + height);
        }
        baseWidth = width;
        baseHeight = height;
        if (stretchMode == StretchMode.DISABLED) {
            stretchMode = StretchMode.CANVAS;
        }
        dirty = true;
    }

    @Override
    public StretchMode stretchMode() {
        return stretchMode;
    }

    @Override
    public void setStretchMode(StretchMode mode) {
        stretchMode = mode;
        dirty = true;
    }

    @Override
    public AspectMode aspectMode() {
        return aspectMode;
    }

    @Override
    public void setAspectMode(AspectMode mode) {
        aspectMode = mode;
        dirty = true;
    }

    @Override
    public boolean isIntegerScaling() {
        return integerScaling;
    }

    @Override
    public void setIntegerScaling(boolean integerScaling) {
        this.integerScaling = integerScaling;
        dirty = true;
    }

    @Override
    public boolean isPixelSnap() {
        return pixelSnap;
    }

    @Override
    public void setPixelSnap(boolean pixelSnap) {
        this.pixelSnap = pixelSnap;
    }

    @Override
    public Color letterboxColor() {
        return letterboxColor;
    }

    @Override
    public void setLetterboxColor(Color color) {
        this.letterboxColor = color;
    }

    @Override
    public Rect viewport() {
        return new Rect(layout.viewportX(), layout.viewportY(), layout.viewportWidth(), layout.viewportHeight());
    }

    @Override
    public int framebufferWidth() {
        return Math.max(0, framebufferWidth);
    }

    @Override
    public int framebufferHeight() {
        return Math.max(0, framebufferHeight);
    }

    /**
     * Converts a window point (logical window points, as input reports them) to the logical game area.
     *
     * @param windowX points from the left edge of the window
     * @param windowY points from the top edge of the window
     * @return logical game coordinates
     */
    public dev.gulp.api.math.Vec2 toLogical(float windowX, float windowY) {
        DisplayLayout current = layout;
        float px = windowX * contentScale - current.viewportX();
        float py = windowY * contentScale - current.viewportY();
        return new dev.gulp.api.math.Vec2(
                px / Math.max(1, current.viewportWidth()) * current.logicalWidth(),
                py / Math.max(1, current.viewportHeight()) * current.logicalHeight());
    }

    @Override
    public Camera camera() {
        return cameraImpl();
    }

    /**
     * Makes {@link #camera()} return the main camera of the active world.
     *
     * @param value the camera, or {@code null} for the display's own camera
     */
    public void setWorldCamera(@org.jspecify.annotations.Nullable CameraImpl value) {
        this.worldCamera = value;
        if (value != null) {
            value.resize(layout.logicalWidth(), layout.logicalHeight());
        }
    }

    @Override
    public List<RenderLayer> layers() {
        return layersView;
    }

    @Override
    public @Nullable RenderLayer layer(String name) {
        for (RenderLayer layer : layers) {
            if (layer.name().equals(name)) {
                return layer;
            }
        }
        return null;
    }

    @Override
    public RenderLayer addLayer(String name, int zOrder, boolean screenSpace) {
        if (layer(name) != null) {
            throw new IllegalArgumentException("Render layer '" + name + "' already exists");
        }
        RenderLayer layer = new RenderLayer(name, zOrder, screenSpace);
        layers.add(layer);
        layers.sort(ORDER);
        return layer;
    }

    @Override
    public Promise<Pixmap> screenshot() {
        PromiseImpl<Pixmap> promise = promises.get();
        screenshots.add(promise);
        return promise;
    }

    @Override
    public float fps() {
        return fps;
    }

    @Override
    public RenderStats stats() {
        return new RenderStats(drawCalls, textureBinds, vertices, flushes);
    }
}
