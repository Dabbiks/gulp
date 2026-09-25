package dev.gulp.api.particle;

/**
 * What particles do when they hit collision tiles.
 *
 * <pre>{@code
 * builder.collision(ParticleCollision.BOUNCE, 0.4f);
 * }</pre>
 */
public enum ParticleCollision {
    /** They pass through. */
    NONE,
    /** They bounce off, keeping part of their speed. */
    BOUNCE,
    /** They disappear (and spawn their death effect). */
    DIE
}
