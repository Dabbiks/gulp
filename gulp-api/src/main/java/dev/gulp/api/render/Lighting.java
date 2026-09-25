package dev.gulp.api.render;

import dev.gulp.api.graphics.Color;
import java.util.List;

/**
 * The 2D lighting of a world. Off until an ambient colour is set; while off it costs nothing. When on, the world is
 * drawn darkened to the ambient colour and brightened by its lights, whose shadows come from {@code Occluder}
 * components and from collision tiles.
 *
 * <pre>{@code
 * Lighting lighting = world.lighting().ambient(Color.rgb(0x1a1a2e));
 * lighting.add(Light.point(Color.rgb(0xffb347), 6f).setPosition(torch.position()));
 * }</pre>
 */
public interface Lighting {

    /**
     * Returns the ambient colour.
     *
     * @return the colour of unlit places
     */
    Color ambient();

    /**
     * Sets the ambient colour and turns lighting on.
     *
     * @param color the colour of unlit places; white shows the world unchanged
     * @return this lighting
     */
    Lighting ambient(Color color);

    /** Turns lighting off; lights are kept. */
    void disable();

    /**
     * Returns whether lighting is on.
     *
     * @return {@code true} after {@link #ambient(Color)}
     */
    boolean isEnabled();

    /**
     * Adds a light.
     *
     * @param light the light
     * @return the same light
     */
    Light add(Light light);

    /**
     * Removes a light.
     *
     * @param light the light
     * @return {@code true} if it was there
     */
    boolean remove(Light light);

    /**
     * Returns the lights, including those of {@code LightSource} components.
     *
     * @return the lights
     */
    List<Light> lights();

    /**
     * Returns whether collision tiles cast shadows.
     *
     * @return {@code true} by default
     */
    boolean tileShadows();

    /**
     * Sets whether collision tiles cast shadows.
     *
     * @param value whether they do
     */
    void setTileShadows(boolean value);
}
