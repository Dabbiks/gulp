package dev.gulp.api.text;

import dev.gulp.api.event.Event;

/**
 * Fired when a typewriter effect has shown every character of a text (UI labels, from stage 9).
 *
 * <pre>{@code
 * on(TextRevealCompleteEvent.class, e -> showContinueArrow());
 * }</pre>
 */
public final class TextRevealCompleteEvent extends Event {

    private final TextLayout layout;

    /**
     * Creates the event; fired by the engine.
     *
     * @param layout the fully revealed text
     */
    public TextRevealCompleteEvent(TextLayout layout) {
        this.layout = layout;
    }

    /**
     * Returns the fully revealed text.
     *
     * @return the layout
     */
    public TextLayout layout() {
        return layout;
    }
}
