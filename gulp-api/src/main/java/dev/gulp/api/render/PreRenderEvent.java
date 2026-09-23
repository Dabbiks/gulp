package dev.gulp.api.render;

import dev.gulp.api.event.Event;

/**
 * Fired every frame before anything is drawn, with the interpolation factor for smooth movement between ticks.
 * Created only when someone listens.
 *
 * <pre>{@code
 * on(PreRenderEvent.class, e -> camera.setPosition(previous.lerp(current, e.alpha())));
 * }</pre>
 */
public final class PreRenderEvent extends Event {

    private final float alpha;

    /**
     * Creates the event; fired by the engine.
     *
     * @param alpha progress between the last two ticks, {@code 0..1}
     */
    public PreRenderEvent(float alpha) {
        this.alpha = alpha;
    }

    /**
     * Progress between the last two ticks.
     *
     * @return {@code 0..1}
     */
    public float alpha() {
        return alpha;
    }
}
