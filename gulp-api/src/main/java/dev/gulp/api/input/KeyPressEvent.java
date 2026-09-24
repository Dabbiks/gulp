package dev.gulp.api.input;

/**
 * A key went down.
 *
 * <pre>{@code
 * on(KeyPressEvent.class, e -> {
 *     if (e.key().equals(Keys.F3)) { toggleDebug(); }
 * });
 * }</pre>
 */
public final class KeyPressEvent extends KeyEvent {

    /**
     * Creates the event.
     *
     * @param key the key
     * @param scanCode the platform scancode
     * @param modifiers modifier bits, see {@link KeyEvent}
     */
    public KeyPressEvent(KeyboardKey key, int scanCode, int modifiers) {
        super(key, scanCode, modifiers);
    }
}
