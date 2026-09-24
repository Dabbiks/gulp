package dev.gulp.api.input;

/**
 * An action went up in this tick.
 *
 * <pre>{@code
 * on(ActionReleaseEvent.class, e -> {
 *     if (e.action().equals(CHARGE)) { fire(e.heldTicks()); }
 * });
 * }</pre>
 */
public final class ActionReleaseEvent extends InputEvent {

    private final InputAction action;
    private final int heldTicks;

    /**
     * Creates the event.
     *
     * @param action the action
     * @param heldTicks for how many ticks it was held
     */
    public ActionReleaseEvent(InputAction action, int heldTicks) {
        this.action = action;
        this.heldTicks = heldTicks;
    }

    /**
     * Returns the action.
     *
     * @return the action
     */
    public InputAction action() {
        return action;
    }

    /**
     * Returns for how many ticks it was held.
     *
     * @return for how many ticks it was held
     */
    public int heldTicks() {
        return heldTicks;
    }
}
