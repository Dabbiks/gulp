package dev.gulp.api.world;

/**
 * A square of {@link #SIZE} × {@link #SIZE} tiles. Worlds with a generator load chunks around their cameras and around
 * areas kept with {@link World#keepLoaded}; bounded maps have every chunk loaded.
 *
 * <pre>{@code
 * on(ChunkLoadEvent.class, e -> logger().debug("Loaded " + e.chunk().x() + ", " + e.chunk().y()));
 * }</pre>
 */
public interface Chunk {

    /** Side of a chunk in tiles. */
    int SIZE = 32;

    /**
     * Returns the world.
     *
     * @return the world
     */
    World world();

    /**
     * Returns the chunk column: tile x divided by {@link #SIZE}, rounded down.
     *
     * @return the chunk x
     */
    int x();

    /**
     * Returns the chunk row.
     *
     * @return the chunk y
     */
    int y();

    /**
     * Returns whether the chunk is loaded.
     *
     * @return {@code true} until it unloads
     */
    boolean isLoaded();

    /**
     * Returns whether tiles changed since the chunk was generated; changed chunks are kept in memory when they unload.
     *
     * @return {@code true} after a change
     */
    boolean isModified();
}
