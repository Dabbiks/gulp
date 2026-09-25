package dev.gulp.api.particle;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.math.Vec2;
import org.jspecify.annotations.Nullable;

/**
 * A running {@link ParticleEffect} in a world. It ends by itself when every emitter is done and its last particle has
 * died; looping effects run until {@link #stop()}.
 *
 * <pre>{@code
 * ParticleInstance trail = world.spawnParticles(smoke, rocket.position());
 * trail.follow(rocket);
 * // later
 * trail.stop();
 * }</pre>
 */
public interface ParticleInstance {

    /**
     * Returns the effect.
     *
     * @return the effect
     */
    ParticleEffect effect();

    /**
     * Stops emitting; particles already alive live out their lifetime.
     */
    void stop();

    /**
     * Stops emitting and removes every particle at once.
     */
    void kill();

    /**
     * Keeps the emitters on an entity; when it is removed the instance stops.
     *
     * @param entity the entity, or {@code null} to stay where it is
     * @return this instance
     */
    ParticleInstance follow(@Nullable Entity entity);

    /**
     * Moves the emitters.
     *
     * @param position world units
     * @return this instance
     */
    ParticleInstance setPosition(Vec2 position);

    /**
     * Returns where the emitters are.
     *
     * @return world units
     */
    Vec2 position();

    /**
     * Returns whether it still emits or has particles alive.
     *
     * @return {@code false} once finished
     */
    boolean isAlive();

    /**
     * Returns whether it still emits.
     *
     * @return {@code false} after {@link #stop()} or when every emitter is done
     */
    boolean isEmitting();

    /**
     * Returns the particles alive from this instance.
     *
     * @return the count
     */
    int particleCount();
}
