package dev.gulp.api.input;

import java.util.Locale;

/**
 * Family of a controller, used to pick button labels and icons.
 *
 * <pre>{@code
 * ControllerFamily family = input().controllerFamily();
 * String label = GamepadButton.SOUTH.glyph(family).label(); // "A" on Xbox, "Cross" on PlayStation
 * }</pre>
 */
public enum ControllerFamily {
    /** Keyboard and mouse. */
    KEYBOARD_MOUSE,
    /** Xbox and most PC gamepads. */
    XBOX,
    /** PlayStation (DualShock, DualSense). */
    PLAYSTATION,
    /** Nintendo (Switch Pro, Joy-Con). */
    NINTENDO,
    /** Any other gamepad. */
    GENERIC;

    /**
     * Guesses the family from the name a gamepad reports.
     *
     * @param gamepadName the name, for example {@code Xbox Controller} or {@code DualSense Wireless Controller}
     * @return the family, {@link #GENERIC} if nothing matches
     */
    public static ControllerFamily fromName(String gamepadName) {
        String name = gamepadName.toLowerCase(Locale.ROOT);
        if (name.contains("xbox") || name.contains("xinput") || name.contains("x-box")) {
            return XBOX;
        }
        if (name.contains("playstation")
                || name.contains("dualshock")
                || name.contains("dualsense")
                || name.contains("ps3")
                || name.contains("ps4")
                || name.contains("ps5")
                || name.contains("sony")
                || name.contains("054c")) {
            return PLAYSTATION;
        }
        if (name.contains("nintendo")
                || name.contains("switch")
                || name.contains("joy-con")
                || name.contains("pro controller")
                || name.contains("057e")) {
            return NINTENDO;
        }
        return GENERIC;
    }
}
