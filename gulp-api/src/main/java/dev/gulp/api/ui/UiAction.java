package dev.gulp.api.ui;

import dev.gulp.api.input.ActionSet;
import dev.gulp.api.input.GamepadAxis;
import dev.gulp.api.input.GamepadButton;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.input.Keys;
import dev.gulp.api.registry.Key;

/**
 * The built-in UI actions {@code gulp:ui_accept}, {@code ui_cancel}, {@code ui_up}, {@code ui_down}, {@code ui_left},
 * {@code ui_right}, {@code ui_next_tab} and {@code ui_prev_tab}. They are registered by the engine, belong to {@link
 * ActionSet#MENU} (so screens that block gameplay input keep them) and can be rebound like any action.
 *
 * <p>Default bindings: accept Enter, Space and the south face button; cancel Escape and the east button; directions
 * the arrow keys, the D-pad and the left stick; tabs Page Up and Page Down and the shoulder buttons.
 *
 * <pre>{@code
 * input().bindings().rebind(UiAction.ACCEPT.action(), 1, Keys.E);
 * }</pre>
 */
public enum UiAction {
    /** Press the focused node. */
    ACCEPT(action("ui_accept").bind(Keys.ENTER, Keys.SPACE, Keys.KP_ENTER, GamepadButton.SOUTH)),
    /** Go back: close a popup, then the top screen. */
    CANCEL(action("ui_cancel").bind(Keys.ESCAPE, GamepadButton.EAST)),
    /** Move focus up. */
    UP(action("ui_up").bind(Keys.UP, GamepadButton.DPAD_UP, GamepadAxis.LEFT_Y.negative())),
    /** Move focus down. */
    DOWN(action("ui_down").bind(Keys.DOWN, GamepadButton.DPAD_DOWN, GamepadAxis.LEFT_Y.positive())),
    /** Move focus left, or decrease a slider. */
    LEFT(action("ui_left").bind(Keys.LEFT, GamepadButton.DPAD_LEFT, GamepadAxis.LEFT_X.negative())),
    /** Move focus right, or increase a slider. */
    RIGHT(action("ui_right").bind(Keys.RIGHT, GamepadButton.DPAD_RIGHT, GamepadAxis.LEFT_X.positive())),
    /** Show the next tab. */
    NEXT_TAB(action("ui_next_tab").bind(Keys.PAGE_DOWN, GamepadButton.RIGHT_BUMPER)),
    /** Show the previous tab. */
    PREV_TAB(action("ui_prev_tab").bind(Keys.PAGE_UP, GamepadButton.LEFT_BUMPER));

    private final InputAction action;

    UiAction(InputAction.Builder builder) {
        this.action = builder.deadZone(0.5f).build();
    }

    private static InputAction.Builder action(String name) {
        return InputAction.builder(Key.of("gulp", name)).set(ActionSet.MENU);
    }

    /**
     * Returns the registered input action.
     *
     * @return the action, for rebinding or reading
     */
    public InputAction action() {
        return action;
    }

    /**
     * Returns the direction of a navigation action.
     *
     * @return the direction, or {@code null} for accept, cancel and tabs
     */
    public @org.jspecify.annotations.Nullable Direction direction() {
        return switch (this) {
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
            case LEFT -> Direction.LEFT;
            case RIGHT -> Direction.RIGHT;
            default -> null;
        };
    }
}
