package dev.gulp.api.asset;

import dev.gulp.api.render.Display;
import dev.gulp.api.render.Draw;

/**
 * Draws the screen shown while the {@code startup} group loads, before {@code Game.onStart}. The default one is a
 * progress bar; replace it in {@code onLoad} with {@link Assets#setLoadingScreen(LoadingScreen)}.
 *
 * <pre>{@code
 * assets().setLoadingScreen((draw, display, progress) ->
 *         draw.color(Color.WHITE).rect(0, display.height() - 8, display.width() * progress, 8));
 * }</pre>
 */
@FunctionalInterface
public interface LoadingScreen {

    /**
     * Draws one frame in screen space (logical pixels, origin at the top-left).
     *
     * @param draw where to draw
     * @param display the display, for its size
     * @param progress loading progress from {@code 0} to {@code 1}
     */
    void draw(Draw draw, Display display, float progress);
}
