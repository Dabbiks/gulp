package dev.gulp.api.input;

/**
 * A gamepad was connected.
 *
 * <pre>{@code
 * on(GamepadConnectEvent.class, e -> toast(e.gamepad().name() + " connected"));
 * }</pre>
 */
public final class GamepadConnectEvent extends InputEvent {

    private final Gamepad gamepad;

    /**
     * Creates the event.
     *
     * @param gamepad the slot it connected in
     */
    public GamepadConnectEvent(Gamepad gamepad) {
        this.gamepad = gamepad;
    }

    /**
     * Returns the slot it connected in.
     *
     * @return the slot it connected in
     */
    public Gamepad gamepad() {
        return gamepad;
    }
}
