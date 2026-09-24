package dev.gulp.api.input;

import java.util.Locale;

/**
 * A keyboard key identified by its position, not by the letter printed on it: {@link Keys#W} is the key left of E on
 * QWERTY, AZERTY and QWERTZ alike, so WASD controls work on every layout. Codes are USB HID usage ids. Use the constants
 * in {@link Keys}.
 *
 * <pre>{@code
 * if (input().isDown(Keys.LEFT_SHIFT)) {
 *     speed *= 2;
 * }
 * KeyboardKey key = Keys.of(event.key().code());
 * }</pre>
 *
 * @param code the USB HID usage id, {@code 0} for unknown keys
 */
public record KeyboardKey(int code) implements Binding {

    @Override
    public String id() {
        return "key:" + name();
    }

    /**
     * Returns the lower-case name used in identifiers, for example {@code space} or {@code a}.
     *
     * @return the name, {@code #<code>} for keys without a name
     */
    public String name() {
        String name = Keys.nameOf(code);
        return name != null ? name.toLowerCase(Locale.ROOT).replace(' ', '_') : "#" + code;
    }

    @Override
    public InputDevice device() {
        return InputDevice.KEYBOARD;
    }

    @Override
    public String displayName() {
        String name = Keys.nameOf(code);
        return name != null ? name : "Key " + code;
    }

    @Override
    public BindingGlyph glyph(ControllerFamily family) {
        return new BindingGlyph(ControllerFamily.KEYBOARD_MOUSE, displayName(), "input/keyboard/" + name());
    }
}
