package dev.gulp.api.input;

import java.util.Locale;

/**
 * One direction of a gamepad axis used as a binding. Its strength is how far the axis is pushed that way, after the dead
 * zone; the action counts as pressed from half way.
 *
 * <pre>{@code
 * AxisDirection left = GamepadAxis.LEFT_X.negative();
 * float howFar = input().strength(MOVE_LEFT); // 0..1
 * }</pre>
 *
 * @param axis the axis
 * @param positive {@code true} for right, down or a pressed trigger
 */
public record AxisDirection(GamepadAxis axis, boolean positive) implements Binding {

    @Override
    public String id() {
        return "axis:" + axis.name().toLowerCase(Locale.ROOT) + (positive ? "+" : "-");
    }

    @Override
    public InputDevice device() {
        return InputDevice.GAMEPAD;
    }

    @Override
    public String displayName() {
        return switch (axis) {
            case LEFT_X -> positive ? "Left stick right" : "Left stick left";
            case LEFT_Y -> positive ? "Left stick down" : "Left stick up";
            case RIGHT_X -> positive ? "Right stick right" : "Right stick left";
            case RIGHT_Y -> positive ? "Right stick down" : "Right stick up";
            case LEFT_TRIGGER -> "Left trigger";
            case RIGHT_TRIGGER -> "Right trigger";
        };
    }

    @Override
    public BindingGlyph glyph(ControllerFamily family) {
        if (axis.isTrigger()) {
            return (axis == GamepadAxis.LEFT_TRIGGER ? GamepadButton.LEFT_TRIGGER : GamepadButton.RIGHT_TRIGGER)
                    .glyph(family);
        }
        ControllerFamily shown = family == ControllerFamily.KEYBOARD_MOUSE ? ControllerFamily.GENERIC : family;
        boolean left = axis == GamepadAxis.LEFT_X || axis == GamepadAxis.LEFT_Y;
        boolean horizontal = axis == GamepadAxis.LEFT_X || axis == GamepadAxis.RIGHT_X;
        String direction = horizontal ? (positive ? "right" : "left") : (positive ? "down" : "up");

        return new BindingGlyph(
                shown,
                (left ? "LS " : "RS ") + direction,
                "input/" + shown.name().toLowerCase(Locale.ROOT) + "/" + (left ? "left_stick_" : "right_stick_")
                        + direction);
    }
}
