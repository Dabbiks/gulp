package dev.gulp.api.particle;

/**
 * The particle system of one world ({@code world.particles()}). Particles are simulated on the CPU from pools and
 * drawn through the batcher in the render layer of their emitter; a world holds at most {@link #limit()} of them, and
 * new ones are skipped when it is full.
 *
 * <pre>{@code
 * world.particles().setLimit(5_000);
 * int alive = world.particles().count();
 * }</pre>
 */
public interface Particles {

    /** The default limit per world. */
    int DEFAULT_LIMIT = 20_000;

    /**
     * Returns the particles alive in the world.
     *
     * @return the count
     */
    int count();

    /**
     * Returns the running effect instances.
     *
     * @return the count
     */
    int instanceCount();

    /**
     * Returns the most particles alive at once.
     *
     * @return the limit
     */
    int limit();

    /**
     * Sets the most particles alive at once.
     *
     * @param value the limit, at least {@code 0}
     */
    void setLimit(int value);

    /**
     * Removes every particle and instance.
     */
    void clear();
}
