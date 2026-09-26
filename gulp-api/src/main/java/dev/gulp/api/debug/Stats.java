package dev.gulp.api.debug;

/**
 * The numbers of the F3 overlay, as of the last frame.
 *
 * <pre>{@code
 * Stats stats = debug().stats();
 * if (stats.frameMillis() > 20f) {
 *     logger().warn("Slow frame: " + stats.frameMillis() + " ms with " + stats.entities() + " entities");
 * }
 * }</pre>
 *
 * @param fps frames per second, averaged over the last second
 * @param frameMillis time between the last two frames
 * @param renderMillis time spent drawing the last frame
 * @param tps game ticks per second, averaged over the last second
 * @param tickMillis time the last game tick took
 * @param entities entities in the active world
 * @param chunks loaded chunks of the active world
 * @param particles live particles in the active world
 * @param sounds sounds playing
 * @param drawCalls draw calls in the last frame
 * @param textureBinds texture changes in the last frame
 * @param memoryUsed heap in use in bytes, or {@code -1} where the platform does not tell (web)
 * @param memoryMax the largest heap in bytes, or {@code -1}
 */
public record Stats(
        float fps,
        float frameMillis,
        float renderMillis,
        float tps,
        float tickMillis,
        int entities,
        int chunks,
        int particles,
        int sounds,
        int drawCalls,
        int textureBinds,
        long memoryUsed,
        long memoryMax) {}
