package dev.gulp.core.graphics;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.render.RenderLayer;
import dev.gulp.api.ui.Transition;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** What the renderer needs from the worlds: the active world's layers, its cameras, built-in drawing and transitions. */
public interface WorldView {

    /**
     * Returns the world layers of the active world.
     *
     * @return the layers, or {@code null} when no world is active
     */
    @Nullable List<RenderLayer> worldLayers();

    /**
     * Returns the cameras of the active world.
     *
     * @return the cameras, the main one first
     */
    List<CameraImpl> cameras();

    /**
     * Draws the built-in contents of a layer (parallax, tiles, sprites) before {@code RenderLayerEvent}.
     *
     * @param draw drawing in world units through the camera
     * @param layer the layer
     * @param camera the camera
     * @param alpha interpolation between ticks
     */
    void drawLayer(DrawImpl draw, RenderLayer layer, CameraImpl camera, float alpha);

    /**
     * Draws screen-space things tied to the world (text above entities) after the world layers of a camera.
     *
     * @param draw drawing in logical points of the camera's area
     * @param camera the camera
     * @param alpha interpolation between ticks
     */
    void drawOverlay(DrawImpl draw, CameraImpl camera, float alpha);

    /**
     * Returns whether the active world shows any debug drawing.
     *
     * @return {@code true} to call {@link #drawDebug}
     */
    boolean hasDebug();

    /**
     * Draws debug shapes of the active world in world units, over its layers.
     *
     * @param draw drawing in world units through the camera
     * @param camera the camera
     * @param worldPixel size of one screen pixel in world units
     */
    void drawDebug(DrawImpl draw, CameraImpl camera, float worldPixel);

    /**
     * Returns the post-processing chain of the active world.
     *
     * @return the chain, or {@code null} without an active world
     */
    @Nullable PostEffectsImpl worldPostEffects();

    /**
     * Returns whether the active world has lighting enabled.
     *
     * @return {@code true} to draw a light map
     */
    boolean lightingEnabled();

    /**
     * Returns the ambient light of the active world.
     *
     * @return the colour the light map starts from
     */
    Color ambient();

    /**
     * Draws the lights of the active world into the bound light map, in world units.
     *
     * @param draw drawing in world units through the camera
     * @param camera the camera
     */
    void drawLights(DrawImpl draw, CameraImpl camera);

    /**
     * Returns the running transition.
     *
     * @return the transition, or {@code null}
     */
    @Nullable Transition transition();

    /**
     * Returns how much the transition covers the screen.
     *
     * @return {@code 0..1}
     */
    float coverage();

    /**
     * Returns whether the transition is uncovering the new world.
     *
     * @return {@code true} in the second half
     */
    boolean entering();
}
