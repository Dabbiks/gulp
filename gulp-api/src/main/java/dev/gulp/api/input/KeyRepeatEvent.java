package dev.gulp.api.input;

/**
 * A held key repeated, at the rate set by the system.
 *
 * <pre>{@code
 * on(KeyRepeatEvent.class, e -> {
 *     if (e.key().equals(Keys.BACKSPACE)) { field.deleteBack(); }
 * });
 * }</pre>
 */
public final class KeyRepeatEvent extends KeyEvent {

    /**
     * Creates the event.
     *
     * @param key the key
     * @param scanCode the platform scancode
     * @param modifiers modifier bits, see {@link KeyEvent}
     */
    public KeyRepeatEvent(KeyboardKey key, int scanCode, int modifiers) {
        super(key, scanCode, modifiers);
    }
}
