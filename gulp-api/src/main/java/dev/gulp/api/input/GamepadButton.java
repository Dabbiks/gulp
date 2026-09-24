package dev.gulp.api.input;

import java.util.Locale;

/**
 * Gamepad buttons in the standard layout, named by position so that the same code works on every controller: {@link
 * #SOUTH} is A on Xbox, Cross on PlayStation and B on Nintendo.
 *
 * <pre>{@code
 * InputAction jump = InputAction.builder(key("jump")).bind(Keys.SPACE, GamepadButton.SOUTH).build();
 * String label = GamepadButton.SOUTH.glyph().label(); // matches the player's controller
 * }</pre>
 */
public enum GamepadButton implements Binding {
    /** Bottom face button. */
    SOUTH("A", "Cross", "B"),
    /** Right face button. */
    EAST("B", "Circle", "A"),
    /** Left face button. */
    WEST("X", "Square", "Y"),
    /** Top face button. */
    NORTH("Y", "Triangle", "X"),
    /** Left shoulder button. */
    LEFT_BUMPER("LB", "L1", "L"),
    /** Right shoulder button. */
    RIGHT_BUMPER("RB", "R1", "R"),
    /** Left trigger pressed past half way; the analog value is {@link GamepadAxis#LEFT_TRIGGER}. */
    LEFT_TRIGGER("LT", "L2", "ZL"),
    /** Right trigger pressed past half way; the analog value is {@link GamepadAxis#RIGHT_TRIGGER}. */
    RIGHT_TRIGGER("RT", "R2", "ZR"),
    /** The left centre button (Back, View, Share, Create, Minus). */
    SELECT("View", "Create", "-"),
    /** The right centre button (Start, Menu, Options, Plus). */
    START("Menu", "Options", "+"),
    /** Left stick click. */
    LEFT_STICK("LS", "L3", "LS"),
    /** Right stick click. */
    RIGHT_STICK("RS", "R3", "RS"),
    /** D-pad up. */
    DPAD_UP("Up", "Up", "Up"),
    /** D-pad down. */
    DPAD_DOWN("Down", "Down", "Down"),
    /** D-pad left. */
    DPAD_LEFT("Left", "Left", "Left"),
    /** D-pad right. */
    DPAD_RIGHT("Right", "Right", "Right"),
    /** The logo button; some platforms reserve it. */
    GUIDE("Xbox", "PS", "Home");

    private final String xbox;
    private final String playStation;
    private final String nintendo;

    GamepadButton(String xbox, String playStation, String nintendo) {
        this.xbox = xbox;
        this.playStation = playStation;
        this.nintendo = nintendo;
    }

    /**
     * Returns the button index in the standard mapping.
     *
     * @return the index, 0 for {@link #SOUTH}
     */
    public int index() {
        return ordinal();
    }

    @Override
    public String id() {
        return "pad:" + name().toLowerCase(Locale.ROOT);
    }

    @Override
    public InputDevice device() {
        return InputDevice.GAMEPAD;
    }

    @Override
    public String displayName() {
        String name = name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @Override
    public BindingGlyph glyph(ControllerFamily family) {
        String label =
                switch (family) {
                    case XBOX -> xbox;
                    case PLAYSTATION -> playStation;
                    case NINTENDO -> nintendo;
                    case KEYBOARD_MOUSE, GENERIC -> displayName();
                };
        ControllerFamily shown = family == ControllerFamily.KEYBOARD_MOUSE ? ControllerFamily.GENERIC : family;
        return new BindingGlyph(
                shown, label, "input/" + shown.name().toLowerCase(Locale.ROOT) + "/" + name().toLowerCase(Locale.ROOT));
    }
}
