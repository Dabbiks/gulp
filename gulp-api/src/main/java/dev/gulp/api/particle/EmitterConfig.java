package dev.gulp.api.particle;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.graphics.BlendMode;
import dev.gulp.api.graphics.Gradient;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.math.FloatCurve;
import dev.gulp.api.math.Vec2;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One emitter of a {@link ParticleEffect}: how many particles it makes and when, where, how they move, how they look
 * over their lifetime, and what happens when they die or hit collision tiles. Immutable; built with {@link #builder()}.
 * Angles are in degrees ({@code 0} right, {@code 90} down), sizes and speeds in world units.
 *
 * <pre>{@code
 * EmitterConfig sparks = EmitterConfig.builder()
 *         .burst(40, 0f)
 *         .duration(0.1f)
 *         .direction(-90f).spread(70f)
 *         .speed(3f, 8f).gravity(0f, 18f)
 *         .lifetime(0.3f, 0.7f)
 *         .size(FloatCurve.linear(0.15f, 0f))
 *         .color(Gradient.of(Color.WHITE, Color.rgb(0xffb000), Color.rgb(0xff3000)))
 *         .blend(BlendMode.ADD)
 *         .collision(ParticleCollision.BOUNCE, 0.4f)
 *         .build();
 * }</pre>
 */
public final class EmitterConfig {

    /**
     * Particles created all at once.
     *
     * @param count how many
     * @param at seconds after the emitter starts
     */
    public record Burst(int count, float at) {}

    private final float rate;
    private final List<Burst> bursts;
    private final float duration;
    private final boolean loop;
    private final EmitterShape shape;
    private final float direction;
    private final float spread;
    private final float speedMin;
    private final float speedMax;
    private final Vec2 gravity;
    private final float drag;
    private final float spinMin;
    private final float spinMax;
    private final float rotationMin;
    private final float rotationMax;
    private final float lifetimeMin;
    private final float lifetimeMax;
    private final FloatCurve size;
    private final Gradient color;
    private final FloatCurve alpha;
    private final List<AssetKey<TextureRegion>> frames;
    private final BlendMode blend;
    private final boolean local;
    private final @Nullable ParticleEffect onDeath;
    private final ParticleCollision collision;
    private final float bounce;
    private final int maxParticles;
    private final String layer;

    private EmitterConfig(Builder b) {
        rate = b.rate;
        bursts = List.copyOf(b.bursts);
        duration = b.duration;
        loop = b.loop;
        shape = b.shape;
        direction = b.direction;
        spread = b.spread;
        speedMin = b.speedMin;
        speedMax = b.speedMax;
        gravity = b.gravity;
        drag = b.drag;
        spinMin = b.spinMin;
        spinMax = b.spinMax;
        rotationMin = b.rotationMin;
        rotationMax = b.rotationMax;
        lifetimeMin = b.lifetimeMin;
        lifetimeMax = b.lifetimeMax;
        size = b.size;
        color = b.color;
        alpha = b.alpha;
        frames = List.copyOf(b.frames);
        blend = b.blend;
        local = b.local;
        onDeath = b.onDeath;
        collision = b.collision;
        bounce = b.bounce;
        maxParticles = b.maxParticles;
        layer = b.layer;
    }

    /**
     * Starts building an emitter.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns particles per second.
     *
     * @return the rate
     */
    public float rate() {
        return rate;
    }

    /**
     * Returns the bursts.
     *
     * @return the bursts
     */
    public List<Burst> bursts() {
        return bursts;
    }

    /**
     * Returns how long the emitter emits.
     *
     * @return seconds
     */
    public float duration() {
        return duration;
    }

    /**
     * Returns whether it starts over after its duration.
     *
     * @return {@code true} for looping emitters
     */
    public boolean loop() {
        return loop;
    }

    /**
     * Returns the spawn shape.
     *
     * @return the shape
     */
    public EmitterShape shape() {
        return shape;
    }

    /**
     * Returns the launch direction.
     *
     * @return degrees
     */
    public float direction() {
        return direction;
    }

    /**
     * Returns the full spread around the direction.
     *
     * @return degrees
     */
    public float spread() {
        return spread;
    }

    /**
     * Returns the slowest launch speed.
     *
     * @return units per second
     */
    public float speedMin() {
        return speedMin;
    }

    /**
     * Returns the fastest launch speed.
     *
     * @return units per second
     */
    public float speedMax() {
        return speedMax;
    }

    /**
     * Returns the acceleration of every particle.
     *
     * @return units per second squared
     */
    public Vec2 gravity() {
        return gravity;
    }

    /**
     * Returns the drag.
     *
     * @return fraction of speed lost per second
     */
    public float drag() {
        return drag;
    }

    /**
     * Returns the slowest spin.
     *
     * @return degrees per second
     */
    public float spinMin() {
        return spinMin;
    }

    /**
     * Returns the fastest spin.
     *
     * @return degrees per second
     */
    public float spinMax() {
        return spinMax;
    }

    /**
     * Returns the smallest start rotation.
     *
     * @return degrees
     */
    public float rotationMin() {
        return rotationMin;
    }

    /**
     * Returns the largest start rotation.
     *
     * @return degrees
     */
    public float rotationMax() {
        return rotationMax;
    }

    /**
     * Returns the shortest lifetime.
     *
     * @return seconds
     */
    public float lifetimeMin() {
        return lifetimeMin;
    }

    /**
     * Returns the longest lifetime.
     *
     * @return seconds
     */
    public float lifetimeMax() {
        return lifetimeMax;
    }

    /**
     * Returns the size over the lifetime.
     *
     * @return world units
     */
    public FloatCurve size() {
        return size;
    }

    /**
     * Returns the colour over the lifetime.
     *
     * @return the gradient
     */
    public Gradient color() {
        return color;
    }

    /**
     * Returns the opacity over the lifetime, multiplied with the colour's alpha.
     *
     * @return {@code 0..1}
     */
    public FloatCurve alpha() {
        return alpha;
    }

    /**
     * Returns the images: one, or frames played over the lifetime.
     *
     * @return the regions, empty for plain soft dots
     */
    public List<AssetKey<TextureRegion>> frames() {
        return frames;
    }

    /**
     * Returns the blend mode.
     *
     * @return the mode
     */
    public BlendMode blend() {
        return blend;
    }

    /**
     * Returns whether particles move with the emitter.
     *
     * @return {@code true} for local space
     */
    public boolean isLocal() {
        return local;
    }

    /**
     * Returns the effect spawned where each particle dies.
     *
     * @return the effect, or {@code null}
     */
    public @Nullable ParticleEffect onDeath() {
        return onDeath;
    }

    /**
     * Returns what particles do on collision tiles.
     *
     * @return the behaviour
     */
    public ParticleCollision collision() {
        return collision;
    }

    /**
     * Returns the speed kept after a bounce.
     *
     * @return {@code 0..1}
     */
    public float bounce() {
        return bounce;
    }

    /**
     * Returns the most particles alive at once from this emitter.
     *
     * @return the limit, {@code 0} for none (the world limit still applies)
     */
    public int maxParticles() {
        return maxParticles;
    }

    /**
     * Returns the render layer the particles are drawn in.
     *
     * @return the layer name, {@code effects} by default
     */
    public String layer() {
        return layer;
    }

    /** Builds an {@link EmitterConfig}; every setting has a sensible default. */
    public static final class Builder {
        private float rate;
        private final List<Burst> bursts = new ArrayList<>();
        private float duration = 1f;
        private boolean loop;
        private EmitterShape shape = EmitterShape.point();
        private float direction = -90f;
        private float spread = 360f;
        private float speedMin = 1f;
        private float speedMax = 2f;
        private Vec2 gravity = Vec2.ZERO;
        private float drag;
        private float spinMin;
        private float spinMax;
        private float rotationMin;
        private float rotationMax;
        private float lifetimeMin = 1f;
        private float lifetimeMax = 1f;
        private FloatCurve size = FloatCurve.constant(0.2f);
        private Gradient color = Gradient.WHITE;
        private FloatCurve alpha = FloatCurve.linear(1f, 0f);
        private final List<AssetKey<TextureRegion>> frames = new ArrayList<>();
        private BlendMode blend = BlendMode.NORMAL;
        private boolean local;
        private @Nullable ParticleEffect onDeath;
        private ParticleCollision collision = ParticleCollision.NONE;
        private float bounce = 0.5f;
        private int maxParticles;
        private String layer = "effects";

        private Builder() {}

        /**
         * Emits continuously.
         *
         * @param perSecond particles per second
         * @return this builder
         */
        public Builder rate(float perSecond) {
            rate = Math.max(0f, perSecond);
            return this;
        }

        /**
         * Adds a burst.
         *
         * @param count particles at once
         * @param atSeconds when, after the start
         * @return this builder
         */
        public Builder burst(int count, float atSeconds) {
            bursts.add(new Burst(count, atSeconds));
            return this;
        }

        /**
         * Sets how long the emitter emits.
         *
         * @param seconds the duration
         * @return this builder
         */
        public Builder duration(float seconds) {
            duration = Math.max(0f, seconds);
            return this;
        }

        /**
         * Starts over after the duration, until stopped.
         *
         * @param value whether to loop
         * @return this builder
         */
        public Builder loop(boolean value) {
            loop = value;
            return this;
        }

        /**
         * Sets where particles appear.
         *
         * @param value the shape
         * @return this builder
         */
        public Builder shape(EmitterShape value) {
            shape = value;
            return this;
        }

        /**
         * Sets the launch direction.
         *
         * @param degrees the direction
         * @return this builder
         */
        public Builder direction(float degrees) {
            direction = degrees;
            return this;
        }

        /**
         * Sets the full spread around the direction.
         *
         * @param degrees {@code 0} straight, {@code 360} all around
         * @return this builder
         */
        public Builder spread(float degrees) {
            spread = degrees;
            return this;
        }

        /**
         * Sets the launch speed range.
         *
         * @param min slowest
         * @param max fastest
         * @return this builder
         */
        public Builder speed(float min, float max) {
            speedMin = min;
            speedMax = max;
            return this;
        }

        /**
         * Sets the acceleration of every particle.
         *
         * @param x units per second squared
         * @param y units per second squared, positive is down
         * @return this builder
         */
        public Builder gravity(float x, float y) {
            gravity = new Vec2(x, y);
            return this;
        }

        /**
         * Sets the drag.
         *
         * @param perSecond fraction of speed lost per second
         * @return this builder
         */
        public Builder drag(float perSecond) {
            drag = Math.max(0f, perSecond);
            return this;
        }

        /**
         * Sets the spin range.
         *
         * @param min degrees per second
         * @param max degrees per second
         * @return this builder
         */
        public Builder spin(float min, float max) {
            spinMin = min;
            spinMax = max;
            return this;
        }

        /**
         * Sets the start rotation range.
         *
         * @param min degrees
         * @param max degrees
         * @return this builder
         */
        public Builder rotation(float min, float max) {
            rotationMin = min;
            rotationMax = max;
            return this;
        }

        /**
         * Sets the lifetime range.
         *
         * @param min seconds
         * @param max seconds
         * @return this builder
         */
        public Builder lifetime(float min, float max) {
            lifetimeMin = Math.max(0.001f, min);
            lifetimeMax = Math.max(lifetimeMin, max);
            return this;
        }

        /**
         * Sets the size over the lifetime.
         *
         * @param curve world units
         * @return this builder
         */
        public Builder size(FloatCurve curve) {
            size = curve;
            return this;
        }

        /**
         * Sets the colour over the lifetime.
         *
         * @param gradient the colours
         * @return this builder
         */
        public Builder color(Gradient gradient) {
            color = gradient;
            return this;
        }

        /**
         * Sets the opacity over the lifetime.
         *
         * @param curve {@code 0..1}
         * @return this builder
         */
        public Builder alpha(FloatCurve curve) {
            alpha = curve;
            return this;
        }

        /**
         * Draws particles with an image.
         *
         * @param region the image
         * @return this builder
         */
        public Builder region(AssetKey<TextureRegion> region) {
            frames.clear();
            frames.add(region);
            return this;
        }

        /**
         * Draws particles with frames played over their lifetime.
         *
         * @param regions the frames, in order
         * @return this builder
         */
        public Builder frames(List<AssetKey<TextureRegion>> regions) {
            frames.clear();
            frames.addAll(regions);
            return this;
        }

        /**
         * Sets the blend mode, {@code ADD} for fire and sparks.
         *
         * @param value the mode
         * @return this builder
         */
        public Builder blend(BlendMode value) {
            blend = value;
            return this;
        }

        /**
         * Makes particles move with the emitter instead of staying where they were made.
         *
         * @param value whether in local space
         * @return this builder
         */
        public Builder local(boolean value) {
            local = value;
            return this;
        }

        /**
         * Spawns an effect where each particle dies.
         *
         * @param effect the effect, or {@code null}
         * @return this builder
         */
        public Builder onDeath(@Nullable ParticleEffect effect) {
            onDeath = effect;
            return this;
        }

        /**
         * Sets what particles do on collision tiles.
         *
         * @param value the behaviour
         * @param keep the speed kept after a bounce, {@code 0..1}
         * @return this builder
         */
        public Builder collision(ParticleCollision value, float keep) {
            collision = value;
            bounce = keep;
            return this;
        }

        /**
         * Limits the particles alive at once from this emitter.
         *
         * @param value the limit, {@code 0} for none
         * @return this builder
         */
        public Builder maxParticles(int value) {
            maxParticles = Math.max(0, value);
            return this;
        }

        /**
         * Sets the render layer.
         *
         * @param name the layer name
         * @return this builder
         */
        public Builder layer(String name) {
            layer = name;
            return this;
        }

        /**
         * Finishes the emitter.
         *
         * @return the emitter
         */
        public EmitterConfig build() {
            return new EmitterConfig(this);
        }
    }
}
