package dev.gulp.api.particle;

import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Keyed;
import java.util.ArrayList;
import java.util.List;

/**
 * A particle effect: one or more emitters started together. Built in code, or loaded from JSON in {@code particles/}
 * as an asset ({@code AssetKey.particles("game:particles/sparks")}), which reloads while the game runs; effects
 * spawned by asset key pick up the new version on their next spawn. Immutable.
 *
 * <pre>{@code
 * ParticleEffect hit = ParticleEffect.builder(Key.of("game", "hit"))
 *         .emitter(EmitterConfig.builder().burst(20, 0f).speed(2f, 6f).lifetime(0.2f, 0.5f).build())
 *         .build();
 * world.spawnParticles(hit, player.position());
 * }</pre>
 */
public final class ParticleEffect implements Keyed {

    private final Key key;
    private final List<EmitterConfig> emitters;

    private ParticleEffect(Key key, List<EmitterConfig> emitters) {
        this.key = key;
        this.emitters = List.copyOf(emitters);
    }

    /**
     * Returns an effect made of emitters.
     *
     * @param key the key
     * @param emitters the emitters
     * @return the effect
     */
    public static ParticleEffect of(Key key, List<EmitterConfig> emitters) {
        return new ParticleEffect(key, emitters);
    }

    /**
     * Starts building an effect.
     *
     * @param key the key
     * @return the builder
     */
    public static Builder builder(Key key) {
        return new Builder(key);
    }

    @Override
    public Key key() {
        return key;
    }

    /**
     * Returns the emitters.
     *
     * @return the emitters, started together
     */
    public List<EmitterConfig> emitters() {
        return emitters;
    }

    /**
     * Returns whether the effect ever stops by itself: no emitter loops or emits continuously forever.
     *
     * @return {@code true} if every emitter has an end
     */
    public boolean isFinite() {
        for (EmitterConfig emitter : emitters) {
            if (emitter.loop()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "ParticleEffect[" + key + ", " + emitters.size() + " emitters]";
    }

    /** Builds a {@link ParticleEffect}. */
    public static final class Builder {
        private final Key key;
        private final List<EmitterConfig> emitters = new ArrayList<>();

        private Builder(Key key) {
            this.key = key;
        }

        /**
         * Adds an emitter.
         *
         * @param emitter the emitter
         * @return this builder
         */
        public Builder emitter(EmitterConfig emitter) {
            emitters.add(emitter);
            return this;
        }

        /**
         * Finishes the effect.
         *
         * @return the effect
         */
        public ParticleEffect build() {
            return new ParticleEffect(key, emitters);
        }
    }
}
