package dev.gulp.api.input;

/**
 * A gamepad was disconnected. A good moment to pause the game.
 *
 * <pre>{@code
 * on(GamepadDisconnectEvent.class, e -> engine().pause());
 * }</pre>
 */
public final class GamepadDisconnectEvent extends InputEvent {

    private final Gamepad gamepad;

    /**
     * Creates the event.
     *
     * @param gamepad the slot it left
     */
    public GamepadDisconnectEvent(Gamepad gamepad) {
        this.gamepad = gamepad;
    }

    /**
     * Returns the slot it left.
     *
     * @return the slot it left
     */
    public Gamepad gamepad() {
        return gamepad;
    }
}
