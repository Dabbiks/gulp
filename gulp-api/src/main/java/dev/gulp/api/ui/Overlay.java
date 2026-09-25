package dev.gulp.api.ui;

import dev.gulp.api.Owner;
import dev.gulp.api.event.Subscription;
import dev.gulp.api.render.Draw;

/**
 * Immediate drawing on the screen every frame, above screens, without building nodes: debug text, simple markers,
 * quick prototypes. The {@link OverlayArea} gives the screen size and anchored rectangles, so even here nothing is
 * positioned by hand for one resolution.
 *
 * <pre>{@code
 * ui().overlay().draw(this, (draw, screen) -> {
 *     draw.text("Paused", screen.center(), TextStyle.of(32), TextAlign.CENTER);
 *     draw.text("x: " + player.position().x(), 8, 8);
 *     draw.rect(screen.anchor(Anchor.BOTTOM_RIGHT, 8, 8, 100, 20), Color.BLACK.withAlpha(0.5f));
 * });
 * }</pre>
 */
public interface Overlay {

    /** Draws every frame. */
    @FunctionalInterface
    interface Drawer {
        /**
         * Draws.
         *
         * @param draw where to draw, in UI points
         * @param screen the screen area
         */
        void draw(Draw draw, OverlayArea screen);
    }

    /**
     * Adds a drawer.
     *
     * @param owner the owner; the drawer is removed when it is disabled
     * @param drawer the drawer
     * @return cancels the drawer
     */
    Subscription draw(Owner owner, Drawer drawer);
}
