package dev.gulp.api.world;

import dev.gulp.api.event.Event;

/**
 * A chunk was loaded or generated and added to its world.
 *
 * <pre>{@code
 * on(ChunkLoadEvent.class, e -> populateVillages(e.chunk()));
 * }</pre>
 */
public final class ChunkLoadEvent extends Event {

    private final Chunk chunk;
    private final boolean generated;

    /**
     * Creates the event.
     *
     * @param chunk the chunk
     * @param generated whether it was generated just now
     */
    public ChunkLoadEvent(Chunk chunk, boolean generated) {
        super();
        this.chunk = chunk;
        this.generated = generated;
    }

    /**
     * Returns the chunk.
     *
     * @return the chunk
     */
    public Chunk chunk() {
        return chunk;
    }

    /**
     * Returns whether it was generated just now.
     *
     * @return whether it was generated just now
     */
    public boolean generated() {
        return generated;
    }
}
