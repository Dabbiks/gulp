package dev.gulp.api.ui;

import dev.gulp.api.registry.Keyed;
import dev.gulp.api.render.Draw;

/**
 * A transition between worlds (and between screens, stage 9): it covers the old picture, the switch happens while the
 * screen is fully covered, then it uncovers the new picture. Built-in transitions are in {@link Transitions}.
 *
 * <pre>{@code
 * worlds().switchTo("level2", Transitions.circleWipe(0.6f));
 *
 * // a custom transition: a curtain from the top
 * Transition curtain = new Transition() {
 *     public Key key() { return key("curtain"); }
 *     public float duration() { return 0.5f; }
 *     public void draw(Draw draw, TransitionFrame frame) {
 *         draw.color(Color.BLACK).rect(0, 0, frame.width(), frame.height() * frame.coverage());
 *     }
 * };
 * }</pre>
 */
public interface Transition extends Keyed {

    /**
     * Returns the length of each half: covering, then uncovering.
     *
     * @return seconds
     */
    float duration();

    /**
     * Returns whether {@link TransitionFrame#screen()} must hold the rendered picture. Costs an extra render target.
     *
     * @return {@code false} by default
     */
    default boolean needsFrame() {
        return false;
    }

    /**
     * Draws the transition over the screen, in logical screen coordinates.
     *
     * @param draw drawing, already set up for the screen
     * @param frame size and progress
     */
    void draw(Draw draw, TransitionFrame frame);
}
