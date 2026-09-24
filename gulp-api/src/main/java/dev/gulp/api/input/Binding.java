package dev.gulp.api.input;

import dev.gulp.api.Gulp;
import java.util.Locale;

/**
 * One physical input an action can be bound to: a keyboard key, a mouse button, a gamepad button or one direction of a
 * gamepad axis.
 *
 * <pre>{@code
 * InputAction jump = InputAction.builder(key("jump"))
 *         .bind(Keys.SPACE, MouseButton.RIGHT, GamepadButton.SOUTH, GamepadAxis.LEFT_Y.negative())
 *         .build();
 * String saved = Keys.SPACE.id();          // "key:space"
 * Binding back = Binding.parse(saved);     // Keys.SPACE
 * }</pre>
 */
public sealed interface Binding permits KeyboardKey, MouseButton, GamepadButton, AxisDirection {

    /**
     * Returns the stable identifier used when bindings are saved, for example {@code key:space}, {@code mouse:left},
     * {@code pad:south} or {@code axis:left_x-}.
     *
     * @return the identifier
     */
    String id();

    /**
     * Returns the device this binding belongs to.
     *
     * @return the device
     */
    InputDevice device();

    /**
     * Returns a short English name for settings screens, for example {@code Space}, {@code Left mouse} or {@code
     * South}. Use {@link #glyph()} to show the button as the player's controller labels it.
     *
     * @return the name
     */
    String displayName();

    /**
     * Returns the label and icon of this binding as a controller of a given family shows it.
     *
     * @param family the controller family
     * @return the glyph
     */
    BindingGlyph glyph(ControllerFamily family);

    /**
     * Returns the glyph for the controller the player used last ({@link Input#controllerFamily()}).
     *
     * @return the glyph
     */
    default BindingGlyph glyph() {
        return glyph(Gulp.engine().input().controllerFamily());
    }

    /**
     * Parses an identifier returned by {@link #id()}.
     *
     * @param id the identifier
     * @return the binding
     * @throws IllegalArgumentException if the identifier is unknown
     */
    static Binding parse(String id) {
        int colon = id.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("Unknown binding '" + id + "'");
        }
        String kind = id.substring(0, colon);
        String name = id.substring(colon + 1).toLowerCase(Locale.ROOT);
        switch (kind) {
            case "key" -> {
                KeyboardKey key = Keys.byName(name);
                if (key != null) {
                    return key;
                }
            }
            case "mouse" -> {
                for (MouseButton button : MouseButton.values()) {
                    if (button.id().equals(id)) {
                        return button;
                    }
                }
            }
            case "pad" -> {
                for (GamepadButton button : GamepadButton.values()) {
                    if (button.id().equals(id)) {
                        return button;
                    }
                }
            }
            case "axis" -> {
                if (name.length() > 1) {
                    char sign = name.charAt(name.length() - 1);
                    String axisName = name.substring(0, name.length() - 1);
                    for (GamepadAxis axis : GamepadAxis.values()) {
                        if (axis.name().toLowerCase(Locale.ROOT).equals(axisName) && (sign == '+' || sign == '-')) {
                            return sign == '+' ? axis.positive() : axis.negative();
                        }
                    }
                }
            }
            default -> {}
        }
        throw new IllegalArgumentException("Unknown binding '" + id + "'");
    }
}
