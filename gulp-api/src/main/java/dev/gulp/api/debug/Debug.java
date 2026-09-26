package dev.gulp.api.debug;

import dev.gulp.api.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Developer tools: the F3 overlay with statistics, debug drawings toggled with F3 + a key or {@code /debug}, the
 * entity inspector, shapes drawn in the world and the profiler.
 *
 * <p>The tools work in development builds ({@code runDesktop}, {@code runWeb}, tests) and in production when the game
 * turns them on with {@link dev.gulp.api.GameSettings#debugTools(boolean)}. When they are off, drawing and profiling
 * sections do nothing and cost nothing, so calls can stay in shipped code.
 *
 * <pre>{@code
 * debug().draw().arrow(enemy.position(), target, Color.RED, 30);
 * try (var _ = debug().section("ai")) {
 *     planner.update();
 * }
 * debug().set(DebugFlag.COLLISION, true);
 * }</pre>
 */
public interface Debug {

    /**
     * Returns whether the developer tools work.
     *
     * @return {@code true} in development builds, or when the game turned them on
     */
    boolean isEnabled();

    /**
     * Returns shapes drawn in the active world for a number of ticks.
     *
     * @return the drawing, a no-op when the tools are off
     */
    DebugDraw draw();

    /**
     * Returns the profiler.
     *
     * @return the profiler
     */
    Profiler profiler();

    /**
     * Opens a named profiler section; close it, best with try-with-resources. Free when the profiler is not running.
     *
     * <pre>{@code
     * try (var _ = debug().section("pathfinding")) {
     *     grid.findPath(from, to);
     * }
     * }</pre>
     *
     * @param name the section name in the report
     * @return the open section
     */
    default Profiler.Section section(String name) {
        return profiler().section(name);
    }

    /**
     * Returns the numbers the F3 overlay shows, as of the last frame.
     *
     * @return a snapshot
     */
    Stats stats();

    /**
     * Returns whether the F3 overlay is shown.
     *
     * @return {@code true} if shown
     */
    boolean isOverlayVisible();

    /**
     * Shows or hides the F3 overlay, as pressing F3 does.
     *
     * @param visible whether to show it
     */
    void setOverlayVisible(boolean visible);

    /**
     * Returns whether a debug drawing or inspector is on.
     *
     * @param flag the drawing or inspector
     * @return {@code true} if on
     */
    boolean isOn(DebugFlag flag);

    /**
     * Turns a debug drawing or inspector on or off, as F3 + its key or {@code /debug <flag>} does.
     *
     * @param flag the drawing or inspector
     * @param on whether it is on
     */
    void set(DebugFlag flag, boolean on);

    /**
     * Switches a debug drawing or inspector.
     *
     * @param flag the drawing or inspector
     */
    default void toggle(DebugFlag flag) {
        set(flag, !isOn(flag));
    }

    /**
     * Returns the entity the entity inspector shows.
     *
     * @return the entity, or {@code null}
     */
    @Nullable Entity inspected();

    /**
     * Shows an entity in the entity inspector, which turns it on; {@code null} clears it. With the inspector on,
     * clicking an entity in the world picks it too.
     *
     * @param entity the entity, or {@code null}
     */
    void inspect(@Nullable Entity entity);
}
