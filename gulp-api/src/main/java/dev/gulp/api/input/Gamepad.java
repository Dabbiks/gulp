package dev.gulp.api.input;

/**
 * One gamepad slot. The object stays the same when a pad disconnects and another connects in the slot.
 *
 * <pre>{@code
 * Gamepad pad = input().gamepad(1);          // second player
 * if (pad.isConnected() && pad.isDown(GamepadButton.SOUTH)) { ... }
 * float x = pad.axis(GamepadAxis.LEFT_X);    // dead zone applied
 * pad.rumble(0.3f, 0.8f, 0.2f);
 * }</pre>
 */
public interface Gamepad {

    /**
     * Returns the slot index.
     *
     * @return the index, from 0
     */
    int index();

    /**
     * Returns whether a pad is connected in this slot.
     *
     * @return {@code true} if connected
     */
    boolean isConnected();

    /**
     * Returns the name the pad reports.
     *
     * @return the name, empty when disconnected
     */
    String name();

    /**
     * Returns the controller family guessed from the name.
     *
     * @return the family
     */
    ControllerFamily family();

    /**
     * Returns whether a button is held.
     *
     * @param button the button
     * @return {@code true} if held
     */
    boolean isDown(GamepadButton button);

    /**
     * Returns an axis with the dead zone applied and rescaled, so small drift reads as {@code 0}.
     *
     * @param axis the axis
     * @return {@code -1..1} for sticks, {@code 0..1} for triggers
     */
    float axis(GamepadAxis axis);

    /**
     * Returns an axis as the device reports it.
     *
     * @param axis the axis
     * @return the raw value
     */
    float rawAxis(GamepadAxis axis);

    /**
     * Returns the dead zone used by {@link #axis(GamepadAxis)}.
     *
     * @return the dead zone
     */
    float deadZone();

    /**
     * Sets the dead zone used by {@link #axis(GamepadAxis)}.
     *
     * @param deadZone the dead zone, {@code 0..1} exclusive of 1
     */
    void setDeadZone(float deadZone);

    /**
     * Returns whether this pad and platform support rumble.
     *
     * @return {@code true} if {@link #rumble(float, float, float)} has an effect
     */
    boolean supportsRumble();

    /**
     * Rumbles the pad where the platform allows it; otherwise does nothing.
     *
     * @param weak the high-frequency motor, {@code 0..1}
     * @param strong the low-frequency motor, {@code 0..1}
     * @param seconds how long
     */
    void rumble(float weak, float strong, float seconds);
}
