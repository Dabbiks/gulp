package dev.gulp.api.world;

import dev.gulp.api.math.Rng;

/**
 * Fills new chunks of an endless world. Runs outside the tick (a thread on desktop, spread over frames on the web), so
 * it must only use its arguments and immutable data. The random generator is seeded from the world seed and the chunk
 * position, so a chunk comes out the same every time.
 *
 * <pre>{@code
 * Noise noise = new Noise(7);
 * ChunkGenerator islands = (data, cx, cy, rng) -> {
 *     for (int y = 0; y < Chunk.SIZE; y++) {
 *         for (int x = 0; x < Chunk.SIZE; x++) {
 *             float h = noise.fractal(Noise.Type.SIMPLEX, (data.originX() + x) * 0.05f,
 *                     (data.originY() + y) * 0.05f, 4, 2f, 0.5f);
 *             data.setTile("ground", x, y, h > 0 ? GRASS : WATER);
 *         }
 *     }
 * };
 * worlds().load("overworld", WorldSource.generator(islands));
 * }</pre>
 */
@FunctionalInterface
public interface ChunkGenerator {

    /**
     * Generates one chunk.
     *
     * @param data where tiles and spawns go
     * @param chunkX chunk column
     * @param chunkY chunk row
     * @param rng random numbers for this chunk
     */
    void generate(ChunkData data, int chunkX, int chunkY, Rng rng);
}
