package dev.gulp.core.input;

import dev.gulp.api.input.KeyboardKey;
import dev.gulp.api.input.MouseButton;

/** The view of raw input that the UI gets before game listeners; returning {@code true} consumes the input. */
public interface UiInput {

    /**
     * A key was pressed or repeats.
     *
     * @param key the key
     * @param modifiers modifier bits (1 shift, 2 control, 4 alt, 8 super)
     * @param repeat whether the key repeats
     * @return {@code true} if the UI used it
     */
    boolean keyPressed(KeyboardKey key, int modifiers, boolean repeat);

    /**
     * A character was typed.
     *
     * @param codePoint the character
     * @return {@code true} if the UI used it
     */
    boolean charTyped(int codePoint);

    /**
     * A mouse button changed.
     *
     * @param button the button
     * @param down whether pressed
     * @param x logical x
     * @param y logical y
     * @return {@code true} if the UI used it
     */
    boolean mouseButton(MouseButton button, boolean down, float x, float y);

    /**
     * The wheel turned.
     *
     * @param dx horizontal amount
     * @param dy vertical amount, positive downwards
     * @return {@code true} if the UI used it
     */
    boolean scrolled(float dx, float dy);

    /**
     * The pointer moved.
     *
     * @param x logical x
     * @param y logical y
     */
    void pointerMoved(float x, float y);
}
