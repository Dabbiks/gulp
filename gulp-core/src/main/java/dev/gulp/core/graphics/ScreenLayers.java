package dev.gulp.core.graphics;

import dev.gulp.api.render.Draw;

/** The UI as the renderer sees it: what it draws on the {@code ui} and {@code overlay} screen layers. */
public interface ScreenLayers {

    /**
     * Draws the {@code ui} layer.
     *
     * @param draw where to draw, in logical points
     */
    void drawUi(Draw draw);

    /**
     * Draws the {@code overlay} layer.
     *
     * @param draw where to draw, in logical points
     */
    void drawOverlay(Draw draw);

    /**
     * Returns whether screen layers go into the base buffer on a viewport display.
     *
     * @return {@code true} for pixel-perfect UI
     */
    boolean drawsInBaseBuffer();
}
