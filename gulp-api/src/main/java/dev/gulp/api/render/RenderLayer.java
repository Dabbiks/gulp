package dev.gulp.api.render;

import dev.gulp.api.graphics.Material;
import dev.gulp.api.math.Vec2;

/**
 * A named layer drawn in z-order. World layers are drawn through the camera (with an optional parallax factor), screen
 * layers in logical screen units. Defaults: world {@code background}, {@code tiles}, {@code entities},
 * {@code foreground}, {@code effects}; screen {@code ui}, {@code overlay}. Worlds get their own layer lists in stage 6.
 *
 * <pre>{@code
 * display().layer("background").setParallax(new Vec2(0.5f, 0.5f));
 * display().addLayer("weather", 350, false).setMaterial(Material.DEFAULT.withBlend(BlendMode.ADD));
 * }</pre>
 */
public final class RenderLayer {

    private final String name;
    private final int zOrder;
    private final boolean screenSpace;
    private boolean visible = true;
    private boolean ySort;
    private Vec2 parallax = Vec2.ONE;
    private Material material = Material.DEFAULT;

    /**
     * Creates a layer; use {@code Display.addLayer} to add it.
     *
     * @param name unique name
     * @param zOrder drawing order, lower first
     * @param screenSpace {@code true} for screen layers that ignore the camera
     */
    public RenderLayer(String name, int zOrder, boolean screenSpace) {
        this.name = name;
        this.zOrder = zOrder;
        this.screenSpace = screenSpace;
    }

    /**
     * The name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Drawing order.
     *
     * @return lower values are drawn first
     */
    public int zOrder() {
        return zOrder;
    }

    /**
     * Whether the layer ignores the camera.
     *
     * @return {@code true} for screen layers
     */
    public boolean isScreenSpace() {
        return screenSpace;
    }

    /**
     * Whether the layer is drawn.
     *
     * @return {@code true} if visible
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Shows or hides the layer.
     *
     * @param visible whether to draw it
     * @return this layer
     */
    public RenderLayer setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    /**
     * Whether entities in this layer are sorted by Y (top-down games); used from stage 6.
     *
     * @return {@code true} if Y-sorted
     */
    public boolean isYSort() {
        return ySort;
    }

    /**
     * Enables sorting entities by Y.
     *
     * @param ySort whether to sort
     * @return this layer
     */
    public RenderLayer ySort(boolean ySort) {
        this.ySort = ySort;
        return this;
    }

    /**
     * Parallax factor: how much the layer follows the camera.
     *
     * @return {@code (1, 1)} for normal layers, smaller for distant backgrounds
     */
    public Vec2 parallax() {
        return parallax;
    }

    /**
     * Sets the parallax factor of a world layer.
     *
     * @param parallax factor per axis; {@code (0, 0)} stays fixed on screen
     * @return this layer
     */
    public RenderLayer setParallax(Vec2 parallax) {
        this.parallax = parallax;
        return this;
    }

    /**
     * Material applied at the start of the layer.
     *
     * @return the material
     */
    public Material material() {
        return material;
    }

    /**
     * Sets the layer material.
     *
     * @param material the material
     * @return this layer
     */
    public RenderLayer setMaterial(Material material) {
        this.material = material;
        return this;
    }

    @Override
    public String toString() {
        return "RenderLayer[" + name + ", z=" + zOrder + (screenSpace ? ", screen" : "") + "]";
    }
}
