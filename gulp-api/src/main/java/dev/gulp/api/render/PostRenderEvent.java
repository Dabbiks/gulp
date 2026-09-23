package dev.gulp.api.render;

import dev.gulp.api.event.Event;

/**
 * Fired every frame after all layers, with a {@link Draw} in logical screen units, on top of everything. Created only
 * when someone listens.
 *
 * <pre>{@code
 * on(PostRenderEvent.class, e -> e.draw().color(Color.RED).rect(0, 0, 4, 4));
 * }</pre>
 */
public final class PostRenderEvent extends Event {

    private final Draw draw;

    /**
     * Creates the event; fired by the engine.
     *
     * @param draw the screen-space draw
     */
    public PostRenderEvent(Draw draw) {
        this.draw = draw;
    }

    /**
     * The screen-space draw.
     *
     * @return the draw
     */
    public Draw draw() {
        return draw;
    }
}
