package dev.gulp.api.render;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.math.Rect;
import dev.gulp.api.scheduler.Promise;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Screen scaling: the base resolution the game is designed for, how it stretches to the window, and HiDPI. Game code
 * works in logical units and never sees physical pixels unless it asks for {@link #framebufferWidth()}.
 *
 * <pre>{@code
 * // in configure: settings.baseResolution(640, 360).stretchMode(StretchMode.CANVAS).aspectMode(AspectMode.KEEP)
 * float w = display().width();            // 640 in KEEP mode, more in EXPAND
 * display().screenshot().thenSync(image -> save(image.encodePng()));
 * }</pre>
 */
public interface Display {

    /**
     * Visible width in logical units (base pixels, or window points when stretching is disabled).
     *
     * @return the width
     */
    float width();

    /**
     * Visible height in logical units.
     *
     * @return the height
     */
    float height();

    /**
     * Base resolution width.
     *
     * @return pixels, {@code 0} when disabled
     */
    int baseWidth();

    /**
     * Base resolution height.
     *
     * @return pixels, {@code 0} when disabled
     */
    int baseHeight();

    /**
     * Sets the base resolution.
     *
     * @param width width, or {@code 0} together with height to disable
     * @param height height
     */
    void setBaseResolution(int width, int height);

    /**
     * Stretch mode.
     *
     * @return the mode
     */
    StretchMode stretchMode();

    /**
     * Changes the stretch mode.
     *
     * @param mode the mode
     */
    void setStretchMode(StretchMode mode);

    /**
     * Aspect mode.
     *
     * @return the mode
     */
    AspectMode aspectMode();

    /**
     * Changes the aspect mode.
     *
     * @param mode the mode
     */
    void setAspectMode(AspectMode mode);

    /**
     * Whether scaling uses whole multiples only.
     *
     * @return {@code true} for integer scaling
     */
    boolean isIntegerScaling();

    /**
     * Enables whole-multiple scaling (crisp pixel art).
     *
     * @param integerScaling whether to use integer scaling
     */
    void setIntegerScaling(boolean integerScaling);

    /**
     * Whether world drawing snaps positions to base pixels.
     *
     * @return {@code true} if snapping
     */
    boolean isPixelSnap();

    /**
     * Enables snapping world positions to base pixels.
     *
     * @param pixelSnap whether to snap
     */
    void setPixelSnap(boolean pixelSnap);

    /**
     * Color of the bars outside the game area.
     *
     * @return the color
     */
    Color letterboxColor();

    /**
     * Changes the color of the bars.
     *
     * @param color the color
     */
    void setLetterboxColor(Color color);

    /**
     * Area of the window used by the game, in framebuffer pixels (bottom bars excluded).
     *
     * @return the viewport, origin at the top-left
     */
    Rect viewport();

    /**
     * Framebuffer width in physical pixels.
     *
     * @return pixels
     */
    int framebufferWidth();

    /**
     * Framebuffer height in physical pixels.
     *
     * @return pixels
     */
    int framebufferHeight();

    /**
     * The main camera for world layers.
     *
     * @return the camera
     */
    Camera camera();

    /**
     * All layers in drawing order.
     *
     * @return an unmodifiable snapshot
     */
    List<RenderLayer> layers();

    /**
     * A layer by name.
     *
     * @param name the name
     * @return the layer, or {@code null}
     */
    @Nullable RenderLayer layer(String name);

    /**
     * Adds a layer.
     *
     * @param name unique name
     * @param zOrder drawing order
     * @param screenSpace {@code true} to ignore the camera
     * @return the new layer
     * @throws IllegalArgumentException if the name is taken
     */
    RenderLayer addLayer(String name, int zOrder, boolean screenSpace);

    /**
     * Returns the post-processing chain applied to the whole frame, after the world's own chain and screen-space
     * layers.
     *
     * @return the chain
     */
    PostEffects postEffects();

    /**
     * Captures the next rendered frame.
     *
     * @return the image in framebuffer pixels, delivered after the frame is drawn
     */
    Promise<Pixmap> screenshot();

    /**
     * Frames per second over the last second.
     *
     * @return the measured rate
     */
    float fps();

    /**
     * Counters of the last frame.
     *
     * @return the statistics
     */
    RenderStats stats();
}
