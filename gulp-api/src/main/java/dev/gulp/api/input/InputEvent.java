package dev.gulp.api.input;

import dev.gulp.api.event.Event;

/**
 * Base of input events. Input goes first to the developer console, then to the UI (focus and hover), then to game
 * listeners; an event the UI handled has {@link #isConsumedByUi()} set, and gameplay actions do not see it.
 *
 * <pre>{@code
 * on(MouseButtonPressEvent.class, e -> {
 *     if (!e.isConsumedByUi()) { shoot(e.x(), e.y()); }
 * });
 * }</pre>
 */
public abstract class InputEvent extends Event {

    private boolean consumedByUi;

    /** Creates the event. */
    protected InputEvent() {}

    /**
     * Returns whether the UI already handled this input.
     *
     * @return {@code true} if a UI element used it
     */
    public final boolean isConsumedByUi() {
        return consumedByUi;
    }

    /** Marks the input as handled by the UI. Called by the UI system; game code rarely needs it. */
    public final void consumeByUi() {
        consumedByUi = true;
    }
}
