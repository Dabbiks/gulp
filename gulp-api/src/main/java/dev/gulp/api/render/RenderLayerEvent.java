package dev.gulp.api.render;

import dev.gulp.api.event.Event;

/**
 * Fired for every visible render layer, in z-order, with a {@link Draw} set up for that layer: world units through the
 * camera for world layers, logical screen units for screen layers. Created only when someone listens.
 *
 * <pre>{@code
 * on(RenderLayerEvent.class, e -> {
 *     if (e.layer().name().equals("entities")) drawEnemies(e.draw());
 * });
 * }</pre>
 */
public final class RenderLayerEvent extends Event {

    private final Draw draw;
    private final RenderLayer layer;
    private final float alpha;

    /**
     * Creates the event; fired by the engine.
     *
     * @param draw the draw for this layer
     * @param layer the layer
     * @param alpha progress between the last two ticks
     */
    public RenderLayerEvent(Draw draw, RenderLayer layer, float alpha) {
        this.draw = draw;
        this.layer = layer;
        this.alpha = alpha;
    }

    /**
     * The draw for this layer.
     *
     * @return the draw
     */
    public Draw draw() {
        return draw;
    }

    /**
     * The layer being drawn.
     *
     * @return the layer
     */
    public RenderLayer layer() {
        return layer;
    }

    /**
     * Progress between the last two ticks, for interpolating positions.
     *
     * @return {@code 0..1}
     */
    public float alpha() {
        return alpha;
    }
}
