package dev.gulp.platform;

/**
 * Receives raw input events. The backend calls it on the main thread, before each frame, with only primitive arguments
 * so that dispatch does not allocate.
 *
 * <p>Key codes are layout-independent key positions: USB HID usage ids of the keyboard page ({@code 4} = A, {@code 44}
 * = space, {@code 79}–{@code 82} = arrows, {@code 224}–{@code 231} = modifiers; {@code 0} for unknown keys), the same
 * on every backend. Modifiers: 1 shift, 2 control, 4 alt, 8 super. The scancode is
 * the raw platform value.
 *
 * <pre>{@code
 * input.setListener(inputQueue); // gulp-core turns these into per-tick states and events
 * }</pre>
 */
public interface InputListener {

    /**
     * A key went down or repeated.
     *
     * @param keyCode layout-independent key code
     * @param scanCode platform scancode
     * @param modifiers bit set of modifier keys
     * @param repeat whether this is an auto-repeat
     */
    void keyDown(int keyCode, int scanCode, int modifiers, boolean repeat);

    /**
     * A key went up.
     *
     * @param keyCode layout-independent key code
     * @param scanCode platform scancode
     * @param modifiers bit set of modifier keys
     */
    void keyUp(int keyCode, int scanCode, int modifiers);

    /**
     * Text was typed, after keyboard layout and IME composition.
     *
     * @param codePoint the Unicode code point
     */
    void textTyped(int codePoint);

    /**
     * The mouse moved.
     *
     * @param x position in logical points from the left edge
     * @param y position in logical points from the top edge
     * @param deltaX relative motion, valid also when the cursor is captured
     * @param deltaY relative motion, valid also when the cursor is captured
     */
    void mouseMoved(float x, float y, float deltaX, float deltaY);

    /**
     * A mouse button changed state.
     *
     * @param button 0 left, 1 right, 2 middle, then extra buttons
     * @param down whether it is now pressed
     * @param modifiers bit set of modifier keys
     */
    void mouseButton(int button, boolean down, int modifiers);

    /**
     * The mouse wheel or touchpad scrolled.
     *
     * @param deltaX horizontal amount, positive to the right
     * @param deltaY vertical amount, positive downwards
     */
    void scrolled(float deltaX, float deltaY);

    /**
     * A touch point changed.
     *
     * @param pointer stable identifier of the finger while it touches
     * @param phase 0 began, 1 moved, 2 ended, 3 cancelled
     * @param x position in logical points
     * @param y position in logical points
     */
    void touch(int pointer, int phase, float x, float y);

    /**
     * A gamepad was connected or disconnected.
     *
     * @param index gamepad slot
     * @param connected whether it is now connected
     */
    void gamepadConnection(int index, boolean connected);
}
