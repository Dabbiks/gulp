package dev.gulp.api.entity.component;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.entity.Component;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.particle.ParticleEffect;
import dev.gulp.api.particle.ParticleInstance;
import org.jspecify.annotations.Nullable;

/**
 * Plays a {@link ParticleEffect} attached to its entity: started when the entity spawns (or by {@link #play()}),
 * following it, and stopped with it; {@link #stop()} ends the emission early.
 *
 * <pre>{@code
 * EntityType.builder(key("torch"))
 *         .component(() -> new ParticleEmitter(GameAssets.Particles.FLAME).offset(0f, -0.4f))
 *         .build();
 * }</pre>
 */
public final class ParticleEmitter extends Component {

    private final @Nullable ParticleEffect effect;
    private final @Nullable AssetKey<ParticleEffect> asset;
    private Vec2 offset = Vec2.ZERO;
    private boolean autoPlay = true;
    private @Nullable ParticleInstance instance;

    /**
     * Creates the component for an effect built in code.
     *
     * @param effect the effect
     */
    public ParticleEmitter(ParticleEffect effect) {
        this.effect = effect;
        this.asset = null;
    }

    /**
     * Creates the component for an effect loaded as an asset; every {@link #play()} uses its current version.
     *
     * @param effect the asset key
     */
    public ParticleEmitter(AssetKey<ParticleEffect> effect) {
        this.effect = null;
        this.asset = effect;
    }

    /**
     * Places the emitter relative to the entity.
     *
     * @param x world units
     * @param y world units
     * @return this component
     */
    public ParticleEmitter offset(float x, float y) {
        offset = new Vec2(x, y);
        return this;
    }

    /**
     * Returns the offset from the entity.
     *
     * @return world units
     */
    public Vec2 offset() {
        return offset;
    }

    /**
     * Sets whether the effect starts when the entity spawns.
     *
     * @param value {@code true} by default
     * @return this component
     */
    public ParticleEmitter autoPlay(boolean value) {
        autoPlay = value;
        return this;
    }

    /**
     * Starts the effect again, stopping the previous run.
     *
     * @return the running instance
     */
    public ParticleInstance play() {
        stop();
        Vec2 at = entity().position().add(offset);
        ParticleInstance started = asset != null
                ? world().spawnParticles(asset, at)
                : world().spawnParticles(java.util.Objects.requireNonNull(effect), at);
        started.follow(entity());
        instance = started;
        return started;
    }

    /**
     * Stops emitting; live particles fade out.
     */
    public void stop() {
        ParticleInstance running = instance;
        if (running != null) {
            running.stop();
            instance = null;
        }
    }

    /**
     * Returns the running instance.
     *
     * @return the instance, or {@code null} when stopped
     */
    public @Nullable ParticleInstance instance() {
        return instance;
    }

    @Override
    protected void onSpawn() {
        if (autoPlay) {
            play();
        }
    }

    @Override
    protected void onTick() {
        ParticleInstance running = instance;
        if (running == null) {
            return;
        }
        running.setPosition(entity().position().add(offset));
    }

    @Override
    protected void onRemove() {
        stop();
    }
}
