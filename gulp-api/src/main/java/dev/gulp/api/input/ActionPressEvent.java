package dev.gulp.api.input;

/**
 * An action went down in this tick; the same moment as {@link Input#justPressed}.
 *
 * <pre>{@code
 * on(ActionPressEvent.class, e -> {
 *     if (e.action().equals(PAUSE)) { togglePause(); }
 * });
 * }</pre>
 */
public final class ActionPressEvent extends InputEvent {

    private final InputAction action;

    /**
     * Creates the event.
     *
     * @param action the action
     */
    public ActionPressEvent(InputAction action) {
        this.action = action;
    }

    /**
     * Returns the action.
     *
     * @return the action
     */
    public InputAction action() {
        return action;
    }
}
