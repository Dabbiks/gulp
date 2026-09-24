package dev.gulp.api.world;

import dev.gulp.api.event.Event;

/**
 * A chunk is about to unload.
 *
 * <pre>{@code
 * on(ChunkUnloadEvent.class, e -> forgetMachines(e.chunk()));
 * }</pre>
 */
public final class ChunkUnloadEvent extends Event {

    private final Chunk chunk;

    /**
     * Creates the event.
     *
     * @param chunk the chunk
     */
    public ChunkUnloadEvent(Chunk chunk) {
        super();
        this.chunk = chunk;
    }

    /**
     * Returns the chunk.
     *
     * @return the chunk
     */
    public Chunk chunk() {
        return chunk;
    }
}
