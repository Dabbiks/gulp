package dev.gulp.api.render;

/**
 * Counters of the last rendered frame.
 *
 * <pre>{@code
 * RenderStats stats = display().stats();
 * logger().debug(stats.drawCalls() + " draw calls, " + stats.vertices() + " vertices");
 * }</pre>
 *
 * @param drawCalls GPU draw calls
 * @param textureBinds texture switches
 * @param vertices vertices submitted
 * @param flushes batch flushes, including empty state changes
 */
public record RenderStats(int drawCalls, int textureBinds, int vertices, int flushes) {}
