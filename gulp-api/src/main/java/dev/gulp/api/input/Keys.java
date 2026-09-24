package dev.gulp.api.input;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Keyboard keys by position (USB HID usage ids). Letter keys are named after the US layout: {@link #Z} is the key
 * right of left shift even where it prints Y or W.
 *
 * <pre>{@code
 * InputAction jump = InputAction.builder(key("jump")).bind(Keys.SPACE, Keys.W, Keys.UP).build();
 * if (input().isDown(Keys.ESCAPE)) { ... }
 * KeyboardKey key = Keys.byName("left_shift");
 * }</pre>
 */
public final class Keys {

    private static final int SIZE = 256;
    private static final String[] NAMES = new String[SIZE];
    private static final KeyboardKey[] KEYS = new KeyboardKey[SIZE];
    private static final Map<String, KeyboardKey> BY_NAME = new HashMap<>();

    /** Unknown key. */
    public static final KeyboardKey UNKNOWN = new KeyboardKey(0);

    /** Letter A. */
    public static final KeyboardKey A = key(4, "A");
    /** Letter B. */
    public static final KeyboardKey B = key(5, "B");
    /** Letter C. */
    public static final KeyboardKey C = key(6, "C");
    /** Letter D. */
    public static final KeyboardKey D = key(7, "D");
    /** Letter E. */
    public static final KeyboardKey E = key(8, "E");
    /** Letter F. */
    public static final KeyboardKey F = key(9, "F");
    /** Letter G. */
    public static final KeyboardKey G = key(10, "G");
    /** Letter H. */
    public static final KeyboardKey H = key(11, "H");
    /** Letter I. */
    public static final KeyboardKey I = key(12, "I");
    /** Letter J. */
    public static final KeyboardKey J = key(13, "J");
    /** Letter K. */
    public static final KeyboardKey K = key(14, "K");
    /** Letter L. */
    public static final KeyboardKey L = key(15, "L");
    /** Letter M. */
    public static final KeyboardKey M = key(16, "M");
    /** Letter N. */
    public static final KeyboardKey N = key(17, "N");
    /** Letter O. */
    public static final KeyboardKey O = key(18, "O");
    /** Letter P. */
    public static final KeyboardKey P = key(19, "P");
    /** Letter Q. */
    public static final KeyboardKey Q = key(20, "Q");
    /** Letter R. */
    public static final KeyboardKey R = key(21, "R");
    /** Letter S. */
    public static final KeyboardKey S = key(22, "S");
    /** Letter T. */
    public static final KeyboardKey T = key(23, "T");
    /** Letter U. */
    public static final KeyboardKey U = key(24, "U");
    /** Letter V. */
    public static final KeyboardKey V = key(25, "V");
    /** Letter W. */
    public static final KeyboardKey W = key(26, "W");
    /** Letter X. */
    public static final KeyboardKey X = key(27, "X");
    /** Letter Y. */
    public static final KeyboardKey Y = key(28, "Y");
    /** Letter Z. */
    public static final KeyboardKey Z = key(29, "Z");
    /** Digit 1 in the top row. */
    public static final KeyboardKey NUM_1 = key(30, "1");
    /** Digit 2 in the top row. */
    public static final KeyboardKey NUM_2 = key(31, "2");
    /** Digit 3 in the top row. */
    public static final KeyboardKey NUM_3 = key(32, "3");
    /** Digit 4 in the top row. */
    public static final KeyboardKey NUM_4 = key(33, "4");
    /** Digit 5 in the top row. */
    public static final KeyboardKey NUM_5 = key(34, "5");
    /** Digit 6 in the top row. */
    public static final KeyboardKey NUM_6 = key(35, "6");
    /** Digit 7 in the top row. */
    public static final KeyboardKey NUM_7 = key(36, "7");
    /** Digit 8 in the top row. */
    public static final KeyboardKey NUM_8 = key(37, "8");
    /** Digit 9 in the top row. */
    public static final KeyboardKey NUM_9 = key(38, "9");
    /** Digit 0 in the top row. */
    public static final KeyboardKey NUM_0 = key(39, "0");
    /** Enter. */
    public static final KeyboardKey ENTER = key(40, "Enter");
    /** Escape. */
    public static final KeyboardKey ESCAPE = key(41, "Escape");
    /** Backspace. */
    public static final KeyboardKey BACKSPACE = key(42, "Backspace");
    /** Tab. */
    public static final KeyboardKey TAB = key(43, "Tab");
    /** Space bar. */
    public static final KeyboardKey SPACE = key(44, "Space");
    /** Minus. */
    public static final KeyboardKey MINUS = key(45, "Minus");
    /** Equals. */
    public static final KeyboardKey EQUALS = key(46, "Equals");
    /** Left bracket. */
    public static final KeyboardKey LEFT_BRACKET = key(47, "Left Bracket");
    /** Right bracket. */
    public static final KeyboardKey RIGHT_BRACKET = key(48, "Right Bracket");
    /** Backslash. */
    public static final KeyboardKey BACKSLASH = key(49, "Backslash");
    /** Semicolon. */
    public static final KeyboardKey SEMICOLON = key(51, "Semicolon");
    /** Apostrophe. */
    public static final KeyboardKey APOSTROPHE = key(52, "Apostrophe");
    /** Grave accent, left of 1 (often opens consoles). */
    public static final KeyboardKey GRAVE = key(53, "Grave");
    /** Comma. */
    public static final KeyboardKey COMMA = key(54, "Comma");
    /** Period. */
    public static final KeyboardKey PERIOD = key(55, "Period");
    /** Slash. */
    public static final KeyboardKey SLASH = key(56, "Slash");
    /** Caps lock. */
    public static final KeyboardKey CAPS_LOCK = key(57, "Caps Lock");
    /** F1. */
    public static final KeyboardKey F1 = key(58, "F1");
    /** F2. */
    public static final KeyboardKey F2 = key(59, "F2");
    /** F3. */
    public static final KeyboardKey F3 = key(60, "F3");
    /** F4. */
    public static final KeyboardKey F4 = key(61, "F4");
    /** F5. */
    public static final KeyboardKey F5 = key(62, "F5");
    /** F6. */
    public static final KeyboardKey F6 = key(63, "F6");
    /** F7. */
    public static final KeyboardKey F7 = key(64, "F7");
    /** F8. */
    public static final KeyboardKey F8 = key(65, "F8");
    /** F9. */
    public static final KeyboardKey F9 = key(66, "F9");
    /** F10. */
    public static final KeyboardKey F10 = key(67, "F10");
    /** F11. */
    public static final KeyboardKey F11 = key(68, "F11");
    /** F12. */
    public static final KeyboardKey F12 = key(69, "F12");
    /** Print screen. */
    public static final KeyboardKey PRINT_SCREEN = key(70, "Print Screen");
    /** Scroll lock. */
    public static final KeyboardKey SCROLL_LOCK = key(71, "Scroll Lock");
    /** Pause. */
    public static final KeyboardKey PAUSE = key(72, "Pause");
    /** Insert. */
    public static final KeyboardKey INSERT = key(73, "Insert");
    /** Home. */
    public static final KeyboardKey HOME = key(74, "Home");
    /** Page up. */
    public static final KeyboardKey PAGE_UP = key(75, "Page Up");
    /** Delete. */
    public static final KeyboardKey DELETE = key(76, "Delete");
    /** End. */
    public static final KeyboardKey END = key(77, "End");
    /** Page down. */
    public static final KeyboardKey PAGE_DOWN = key(78, "Page Down");
    /** Right arrow. */
    public static final KeyboardKey RIGHT = key(79, "Right");
    /** Left arrow. */
    public static final KeyboardKey LEFT = key(80, "Left");
    /** Down arrow. */
    public static final KeyboardKey DOWN = key(81, "Down");
    /** Up arrow. */
    public static final KeyboardKey UP = key(82, "Up");
    /** Num lock. */
    public static final KeyboardKey NUM_LOCK = key(83, "Num Lock");
    /** Keypad divide. */
    public static final KeyboardKey KP_DIVIDE = key(84, "Keypad Divide");
    /** Keypad multiply. */
    public static final KeyboardKey KP_MULTIPLY = key(85, "Keypad Multiply");
    /** Keypad minus. */
    public static final KeyboardKey KP_SUBTRACT = key(86, "Keypad Subtract");
    /** Keypad plus. */
    public static final KeyboardKey KP_ADD = key(87, "Keypad Add");
    /** Keypad enter. */
    public static final KeyboardKey KP_ENTER = key(88, "Keypad Enter");
    /** Keypad 1. */
    public static final KeyboardKey KP_1 = key(89, "Keypad 1");
    /** Keypad 2. */
    public static final KeyboardKey KP_2 = key(90, "Keypad 2");
    /** Keypad 3. */
    public static final KeyboardKey KP_3 = key(91, "Keypad 3");
    /** Keypad 4. */
    public static final KeyboardKey KP_4 = key(92, "Keypad 4");
    /** Keypad 5. */
    public static final KeyboardKey KP_5 = key(93, "Keypad 5");
    /** Keypad 6. */
    public static final KeyboardKey KP_6 = key(94, "Keypad 6");
    /** Keypad 7. */
    public static final KeyboardKey KP_7 = key(95, "Keypad 7");
    /** Keypad 8. */
    public static final KeyboardKey KP_8 = key(96, "Keypad 8");
    /** Keypad 9. */
    public static final KeyboardKey KP_9 = key(97, "Keypad 9");
    /** Keypad 0. */
    public static final KeyboardKey KP_0 = key(98, "Keypad 0");
    /** Keypad decimal point. */
    public static final KeyboardKey KP_DECIMAL = key(99, "Keypad Decimal");
    /** The extra key next to left shift on ISO keyboards. */
    public static final KeyboardKey WORLD = key(100, "World");
    /** Context menu key. */
    public static final KeyboardKey MENU = key(101, "Menu");
    /** Keypad equals. */
    public static final KeyboardKey KP_EQUALS = key(103, "Keypad Equals");
    /** Left control. */
    public static final KeyboardKey LEFT_CONTROL = key(224, "Left Control");
    /** Left shift. */
    public static final KeyboardKey LEFT_SHIFT = key(225, "Left Shift");
    /** Left alt (option). */
    public static final KeyboardKey LEFT_ALT = key(226, "Left Alt");
    /** Left super (Windows, command). */
    public static final KeyboardKey LEFT_SUPER = key(227, "Left Super");
    /** Right control. */
    public static final KeyboardKey RIGHT_CONTROL = key(228, "Right Control");
    /** Right shift. */
    public static final KeyboardKey RIGHT_SHIFT = key(229, "Right Shift");
    /** Right alt (AltGr). */
    public static final KeyboardKey RIGHT_ALT = key(230, "Right Alt");
    /** Right super. */
    public static final KeyboardKey RIGHT_SUPER = key(231, "Right Super");

    static {
        for (int i = 0; i < 12; i++) {
            key(104 + i, "F" + (13 + i));
        }
        KEYS[0] = UNKNOWN;
    }

    private Keys() {}

    private static KeyboardKey key(int code, String name) {
        KeyboardKey key = new KeyboardKey(code);
        NAMES[code] = name;
        KEYS[code] = key;
        BY_NAME.put(name.toLowerCase(Locale.ROOT).replace(' ', '_'), key);
        return key;
    }

    /**
     * Returns the key with a code. Known codes return the shared constant, so the call does not allocate.
     *
     * @param code the USB HID usage id
     * @return the key
     */
    public static KeyboardKey of(int code) {
        if (code >= 0 && code < SIZE) {
            KeyboardKey key = KEYS[code];
            if (key == null) {
                key = new KeyboardKey(code);
                KEYS[code] = key;
            }
            return key;
        }
        return new KeyboardKey(code);
    }

    /**
     * Finds a key by its identifier name, for example {@code space}, {@code a} or {@code left_shift}.
     *
     * @param name the name, as returned by {@link KeyboardKey#name()}
     * @return the key, or {@code null} if no key has that name
     */
    public static @Nullable KeyboardKey byName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("#")) {
            try {
                return of(Integer.parseInt(lower.substring(1)));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return BY_NAME.get(lower);
    }

    /**
     * Returns the display name of a key code.
     *
     * @param code the code
     * @return the name, for example {@code Left Shift}, or {@code null} for codes without a name
     */
    public static @Nullable String nameOf(int code) {
        return code > 0 && code < SIZE ? NAMES[code] : null;
    }
}
