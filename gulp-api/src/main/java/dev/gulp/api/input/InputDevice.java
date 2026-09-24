package dev.gulp.api.input;

/**
 * Kind of device an input comes from.
 *
 * <pre>{@code
 * if (binding.device() == InputDevice.GAMEPAD) {
 *     showPadHint();
 * }
 * }</pre>
 */
public enum InputDevice {
    /** A keyboard. */
    KEYBOARD,
    /** A mouse, or the first finger on a touch screen. */
    MOUSE,
    /** A gamepad. */
    GAMEPAD
}
