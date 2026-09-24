package dev.gulp.api.world;

import dev.gulp.api.event.Event;
import org.jspecify.annotations.Nullable;

/**
 * Another world became active.
 *
 * <pre>{@code
 * on(WorldSwitchEvent.class, e -> audio().music().crossfadeTo(themeOf(e.to()), 1f));
 * }</pre>
 */
public final class WorldSwitchEvent extends Event {

    private final @Nullable World from;
    private final World to;

    /**
     * Creates the event.
     *
     * @param from the world active before, or {@code null}
     * @param to the world active now
     */
    public WorldSwitchEvent(@Nullable World from, World to) {
        super();
        this.from = from;
        this.to = to;
    }

    /**
     * Returns the world active before, or {@code null}.
     *
     * @return the world active before, or {@code null}
     */
    public @Nullable World from() {
        return from;
    }

    /**
     * Returns the world active now.
     *
     * @return the world active now
     */
    public World to() {
        return to;
    }
}
