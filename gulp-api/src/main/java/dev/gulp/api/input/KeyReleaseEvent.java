package dev.gulp.api.input;

/**
 * A key went up.
 *
 * <pre>{@code
 * on(KeyReleaseEvent.class, e -> charging = false);
 * }</pre>
 */
public final class KeyReleaseEvent extends KeyEvent {

    /**
     * Creates the event.
     *
     * @param key the key
     * @param scanCode the platform scancode
     * @param modifiers modifier bits, see {@link KeyEvent}
     */
    public KeyReleaseEvent(KeyboardKey key, int scanCode, int modifiers) {
        super(key, scanCode, modifiers);
    }
}
