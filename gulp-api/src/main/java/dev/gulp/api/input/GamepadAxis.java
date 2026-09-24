package dev.gulp.api.input;

/**
 * Gamepad axes in the standard layout. Sticks give {@code -1..1} (right and down are positive), triggers {@code 0..1}.
 * Bind one direction of an axis with {@link #positive()} or {@link #negative()}.
 *
 * <pre>{@code
 * InputAction left = InputAction.builder(key("move_left")).bind(Keys.A, GamepadAxis.LEFT_X.negative()).build();
 * float throttle = input().gamepad(0).axis(GamepadAxis.RIGHT_TRIGGER);
 * }</pre>
 */
public enum GamepadAxis {
    /** Left stick, horizontal. */
    LEFT_X,
    /** Left stick, vertical (down is positive). */
    LEFT_Y,
    /** Right stick, horizontal. */
    RIGHT_X,
    /** Right stick, vertical (down is positive). */
    RIGHT_Y,
    /** Left trigger, {@code 0..1}. */
    LEFT_TRIGGER,
    /** Right trigger, {@code 0..1}. */
    RIGHT_TRIGGER;

    private final AxisDirection positive = new AxisDirection(this, true);
    private final AxisDirection negative = new AxisDirection(this, false);

    /**
     * Returns the axis index in the standard mapping.
     *
     * @return the index, 0 for {@link #LEFT_X}
     */
    public int index() {
        return ordinal();
    }

    /**
     * Returns whether this is a trigger, which only has a positive direction.
     *
     * @return {@code true} for triggers
     */
    public boolean isTrigger() {
        return this == LEFT_TRIGGER || this == RIGHT_TRIGGER;
    }

    /**
     * Returns the positive direction (right, down, or trigger pressed) as a binding.
     *
     * @return the binding
     */
    public AxisDirection positive() {
        return positive;
    }

    /**
     * Returns the negative direction (left or up) as a binding.
     *
     * @return the binding
     */
    public AxisDirection negative() {
        return negative;
    }
}
