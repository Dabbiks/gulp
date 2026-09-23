package dev.gulp.platform;

import org.jspecify.annotations.Nullable;

/**
 * Raw input: keys, text, mouse, scroll, touch, gamepads and clipboard. Events go to the {@link InputListener};
 * gamepad axes and buttons are polled.
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
