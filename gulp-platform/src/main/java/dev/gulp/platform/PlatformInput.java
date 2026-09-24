package dev.gulp.platform;

import org.jspecify.annotations.Nullable;

/**
 * Raw input: keys, text, mouse, scroll, touch, gamepads and clipboard. Events go to the {@link InputListener};
 * gamepad axes and buttons are polled.
 *
 * <p>Gamepads use the W3C standard mapping on every backend. Buttons: 0 south, 1 east, 2 west, 3 north, 4 left bumper,
 * 5 right bumper, 6 left trigger, 7 right trigger, 8 select, 9 start, 10 left stick, 11 right stick, 12–15 d-pad up,
 * down, left, right, 16 guide. Axes: 0 left x, 1 left y, 2 right x, 3 right y (right and down positive), 4 left
 * trigger, 5 right trigger ({@code 0..1}).
 *
 * <pre>{@code
 * input.setListener(queue);
 * input.pollGamepads();
 * float lx = input.gamepadAxis(0, 0);
 * }</pre>
 */
public interface PlatformInput {

    /**
     * Sets the receiver of input events.
     *
     * @param listener the listener, or {@code null} to drop events
     */
    void setListener(@Nullable InputListener listener);

    /** Refreshes gamepad states and reports connections. Called once per frame before reading gamepads. */
    void pollGamepads();

    /**
     * Returns whether a gamepad is connected in a slot.
     *
     * @param index the slot, from 0
     * @return {@code true} if connected
     */
    boolean isGamepadConnected(int index);

    /**
     * Returns a human-readable gamepad name.
     *
     * @param index the slot
     * @return the name, or {@code null} if no gamepad is connected there
     */
    @Nullable String gamepadName(int index);

    /**
     * Returns a gamepad axis in the standard mapping.
     *
     * @param index the slot
     * @param axis axis index in the standard mapping
     * @return the value, {@code -1..1} for sticks and {@code 0..1} for triggers
     */
    float gamepadAxis(int index, int axis);

    /**
     * Returns a gamepad button in the standard mapping.
     *
     * @param index the slot
     * @param button button index in the standard mapping
     * @return {@code true} if pressed
     */
    boolean gamepadButton(int index, int button);

    /**
     * Starts rumble if the platform supports it.
     *
     * @param index the slot
     * @param weak strength of the high-frequency motor, {@code 0..1}
     * @param strong strength of the low-frequency motor, {@code 0..1}
     * @param durationMillis how long to rumble
     * @return {@code false} if rumble is not supported
     */
    boolean rumble(int index, float weak, float strong, int durationMillis);

    /**
     * Returns whether a gamepad can rumble.
     *
     * @param index the slot
     * @return {@code true} if {@link #rumble} has an effect
     */
    boolean supportsRumble(int index);

    /**
     * Starts or ends text entry. On touch devices this shows the on-screen keyboard; the area places IME windows.
     *
     * @param active whether text entry is active
     * @param x left edge of the text field in logical points
     * @param y top edge of the text field
     * @param width width of the text field
     * @param height height of the text field
     */
    void setTextInput(boolean active, float x, float y, float width, float height);

    /**
     * Reads the clipboard.
     *
     * @param callback receives the text, or an empty string
     */
    void readClipboard(PlatformCallback<String> callback);

    /**
     * Writes text to the clipboard.
     *
     * @param text the text
     */
    void writeClipboard(String text);
}
