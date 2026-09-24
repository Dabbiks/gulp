package dev.gulp.api.input;

import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.render.Camera;
import java.util.List;

/**
 * Player input. Game code reads actions; devices are there for editors, debug keys and UI.
 *
 * <p>Action states are computed once per game tick (and once per real-time tick while the game is paused), not per
 * frame, so a press and release between two ticks still reads as {@link #justPressed} on the next tick and {@link
 * #justReleased} on the one after. Actions combine every binding: keyboard, mouse and all connected gamepads.
 *
 * <pre>{@code
 * @Override protected void onTick() {
 *     float x = input().axis(MOVE_LEFT, MOVE_RIGHT) * 7f;
 *     if (input().justPressed(JUMP) && mover.canJump()) { mover.jump(12f); }
 *     Vec2 aim = input().vector(AIM_LEFT, AIM_RIGHT, AIM_UP, AIM_DOWN);
 * }
 * }</pre>
 */
public interface Input {

    /**
     * Returns whether an action is held in this tick.
     *
     * @param action the action
     * @return {@code true} while held
     */
    boolean pressed(InputAction action);

    /**
     * Returns whether an action went down in this tick.
     *
     * @param action the action
     * @return {@code true} in the first tick it is held
     */
    boolean justPressed(InputAction action);

    /**
     * Returns whether an action went up in this tick.
     *
     * @param action the action
     * @return {@code true} in the first tick after it was released
     */
    boolean justReleased(InputAction action);

    /**
     * Returns for how many ticks an action has been held.
     *
     * @param action the action
     * @return {@code 1} in the tick it went down, {@code 0} when released
     */
    int heldTicks(InputAction action);

    /**
     * Returns how strongly an action is held: {@code 1} for keys and buttons, the push of an axis past the dead zone.
     *
     * @param action the action
     * @return {@code 0..1}
     */
    float strength(InputAction action);

    /**
     * Returns one axis from two opposite actions.
     *
     * @param negative the action towards {@code -1}, for example move left
     * @param positive the action towards {@code 1}
     * @return {@code -1..1}
     */
    float axis(InputAction negative, InputAction positive);

    /**
     * Returns a direction from four actions, with a round dead zone and never longer than {@code 1}, so diagonals are
     * not faster.
     *
     * @param left the action towards negative x
     * @param right the action towards positive x
     * @param up the action towards negative y
     * @param down the action towards positive y
     * @return the vector, {@link Vec2#ZERO} when idle
     */
    Vec2 vector(InputAction left, InputAction right, InputAction up, InputAction down);

    /**
     * Enables a set of actions.
     *
     * @param set the set
     */
    void enable(ActionSet set);

    /**
     * Disables a set; its actions read as released until it is enabled again.
     *
     * @param set the set
     */
    void disable(ActionSet set);

    /**
     * Returns whether a set is enabled. Every set starts enabled.
     *
     * @param set the set
     * @return {@code true} if enabled
     */
    boolean isEnabled(ActionSet set);

    /**
     * Returns the player's bindings.
     *
     * @return the bindings
     */
    InputBindings bindings();

    /**
     * Returns whether a key is held right now.
     *
     * @param key the key
     * @return {@code true} if held
     */
    boolean isDown(KeyboardKey key);

    /**
     * Returns whether a mouse button is held right now.
     *
     * @param button the button
     * @return {@code true} if held
     */
    boolean isDown(MouseButton button);

    /**
     * Returns the mouse position.
     *
     * @return points from the left edge of the window
     */
    float mouseX();

    /**
     * Returns the mouse position.
     *
     * @return points from the top edge of the window
     */
    float mouseY();

    /**
     * Returns the mouse position in the world seen by a camera.
     *
     * @param camera the camera, usually {@code display().camera()}
     * @return world coordinates under the pointer
     */
    Vec2 mouseWorld(Camera camera);

    /**
     * Returns how far the mouse moved during the last tick; works also when the cursor is captured.
     *
     * @return points
     */
    float mouseDeltaX();

    /**
     * Returns how far the mouse moved during the last tick.
     *
     * @return points
     */
    float mouseDeltaY();

    /**
     * Returns horizontal scrolling during the last tick.
     *
     * @return scroll steps, positive to the right
     */
    float scrollX();

    /**
     * Returns vertical scrolling during the last tick.
     *
     * @return scroll steps, positive downwards
     */
    float scrollY();

    /**
     * Returns the cursor image.
     *
     * @return the cursor
     */
    Cursor cursor();

    /**
     * Changes the cursor image.
     *
     * @param cursor the cursor
     */
    void setCursor(Cursor cursor);

    /**
     * Returns the cursor mode.
     *
     * @return the mode
     */
    CursorMode cursorMode();

    /**
     * Changes the cursor mode.
     *
     * @param mode the mode
     */
    void setCursorMode(CursorMode mode);

    /**
     * Returns the fingers on the screen. The first one also moves the mouse and holds {@link MouseButton#LEFT}.
     *
     * @return the touches, a live view in the order they began
     */
    List<Touch> touches();

    /**
     * Returns a gamepad slot.
     *
     * @param index the slot, from 0
     * @return the slot, connected or not
     * @throws IndexOutOfBoundsException if the index is not below {@link #maxGamepads()}
     */
    Gamepad gamepad(int index);

    /**
     * Returns the connected gamepads.
     *
     * @return the gamepads, a snapshot in slot order
     */
    List<Gamepad> gamepads();

    /**
     * Returns the number of gamepad slots.
     *
     * @return the number of slots
     */
    int maxGamepads();

    /**
     * Returns the device the player used last.
     *
     * @return the device
     */
    InputDevice lastDevice();

    /**
     * Returns the family of the controller the player used last, for button prompts.
     *
     * @return {@link ControllerFamily#KEYBOARD_MOUSE} after keyboard or mouse input, otherwise the gamepad family
     */
    ControllerFamily controllerFamily();

    /**
     * Returns the clipboard.
     *
     * @return the clipboard
     */
    Clipboard clipboard();

    /**
     * Starts text entry: shows the on-screen keyboard on touch devices and places the IME window near the field. Typed
     * text arrives as {@link CharTypedEvent}.
     *
     * @param area the text field in window points
     */
    void startTextInput(Rect area);

    /** Ends text entry. */
    void stopTextInput();

    /**
     * Returns whether text entry is active.
     *
     * @return {@code true} between {@link #startTextInput(Rect)} and {@link #stopTextInput()}
     */
    boolean isTextInputActive();
}
