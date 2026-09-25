package dev.gulp.api.render;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.math.Vec2;

/**
 * A 2D light added to a world's {@link Lighting}: a point light shining all around, a spot light in a cone, or a
 * directional light brightening everything evenly. Point and spot lights cast shadows from occluders and collision
 * tiles. Lights are mutable and can be animated with {@code Props.LIGHT_*}.
 *
 * <pre>{@code
 * Light torch = world.lighting().add(Light.point(Color.rgb(0xffb347), 6f).setFalloff(1.5f));
 * torch.setPosition(new Vec2(12, 7));
 * }</pre>
 */
public final class Light {

    /** Kinds of light. */
    public enum Type {
        /** Shines all around from a point. */
        POINT,
        /** Shines in a cone from a point. */
        SPOT,
        /** Brightens the whole world evenly, without shadows. */
        DIRECTIONAL
    }

    private final Type type;
    private Vec2 position = Vec2.ZERO;
    private Color color;
    private float radius;
    private float intensity = 1f;
    private float falloff = 1f;
    private boolean shadows = true;
    private float direction;
    private float cone = 60f;
    private boolean enabled = true;

    private Light(Type type, Color color, float radius) {
        this.type = type;
        this.color = color;
        this.radius = radius;
    }

    /**
     * Returns a point light.
     *
     * @param color the colour
     * @param radius how far it reaches, world units
     * @return the light
     */
    public static Light point(Color color, float radius) {
        return new Light(Type.POINT, color, radius);
    }

    /**
     * Returns a spot light.
     *
     * @param color the colour
     * @param radius how far it reaches
     * @param direction where it points, degrees ({@code 0} is right, {@code 90} down)
     * @param cone the full opening angle, degrees
     * @return the light
     */
    public static Light spot(Color color, float radius, float direction, float cone) {
        Light light = new Light(Type.SPOT, color, radius);
        light.direction = direction;
        light.cone = cone;
        return light;
    }

    /**
     * Returns a directional light, like sunlight or moonlight through the whole level.
     *
     * @param color the colour
     * @param direction where it comes from, degrees; kept for future normal mapping
     * @return the light
     */
    public static Light directional(Color color, float direction) {
        Light light = new Light(Type.DIRECTIONAL, color, Float.MAX_VALUE);
        light.direction = direction;
        light.shadows = false;
        return light;
    }

    /**
     * Returns the kind of light.
     *
     * @return the type
     */
    public Type type() {
        return type;
    }

    /**
     * Returns the position.
     *
     * @return world units
     */
    public Vec2 position() {
        return position;
    }

    /**
     * Moves the light.
     *
     * @param value world units
     * @return this light
     */
    public Light setPosition(Vec2 value) {
        position = value;
        return this;
    }

    /**
     * Returns the colour.
     *
     * @return the colour
     */
    public Color color() {
        return color;
    }

    /**
     * Changes the colour.
     *
     * @param value the colour
     * @return this light
     */
    public Light setColor(Color value) {
        color = value;
        return this;
    }

    /**
     * Returns how far the light reaches.
     *
     * @return world units
     */
    public float radius() {
        return radius;
    }

    /**
     * Changes how far the light reaches.
     *
     * @param value world units
     * @return this light
     */
    public Light setRadius(float value) {
        radius = Math.max(0f, value);
        return this;
    }

    /**
     * Returns the brightness.
     *
     * @return {@code 1} by default
     */
    public float intensity() {
        return intensity;
    }

    /**
     * Changes the brightness.
     *
     * @param value {@code 0} is off; above {@code 1} overexposes
     * @return this light
     */
    public Light setIntensity(float value) {
        intensity = Math.max(0f, value);
        return this;
    }

    /**
     * Returns how quickly the light fades towards its radius.
     *
     * @return the exponent, {@code 1} for linear
     */
    public float falloff() {
        return falloff;
    }

    /**
     * Changes how quickly the light fades: above {@code 1} it drops faster near the centre, below {@code 1} it stays
     * bright longer.
     *
     * @param value the exponent
     * @return this light
     */
    public Light setFalloff(float value) {
        falloff = Math.max(0.05f, value);
        return this;
    }

    /**
     * Returns whether the light casts shadows.
     *
     * @return {@code true} by default for point and spot lights
     */
    public boolean castsShadows() {
        return shadows;
    }

    /**
     * Turns shadows on or off; lights without shadows are cheaper.
     *
     * @param value whether to cast shadows
     * @return this light
     */
    public Light setShadows(boolean value) {
        shadows = value;
        return this;
    }

    /**
     * Returns the direction of a spot or directional light.
     *
     * @return degrees
     */
    public float direction() {
        return direction;
    }

    /**
     * Turns a spot or directional light.
     *
     * @param degrees the direction
     * @return this light
     */
    public Light setDirection(float degrees) {
        direction = degrees;
        return this;
    }

    /**
     * Returns the opening angle of a spot light.
     *
     * @return degrees
     */
    public float cone() {
        return cone;
    }

    /**
     * Changes the opening angle of a spot light.
     *
     * @param degrees the full angle, {@code 0..360}
     * @return this light
     */
    public Light setCone(float degrees) {
        cone = Math.max(0f, Math.min(360f, degrees));
        return this;
    }

    /**
     * Returns whether the light shines.
     *
     * @return {@code true} unless switched off
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Switches the light on or off.
     *
     * @param value whether it shines
     * @return this light
     */
    public Light setEnabled(boolean value) {
        enabled = value;
        return this;
    }
}
