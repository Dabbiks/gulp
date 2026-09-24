package dev.gulp.api.input;

/**
 * Base of keyboard events: the key position, the platform scancode and the held modifiers.
 *
 * <pre>{@code
 * on(KeyPressEvent.class, e -> {
 *     if (e.isControlDown() && e.key().equals(Keys.S)) { save(); }
 * });
 * }</pre>
 */
public abstract sealed class KeyEvent extends InputEvent permits KeyPressEvent, KeyReleaseEvent, KeyRepeatEvent {

    /** Modifier bit of shift. */
    public static final int SHIFT = 1;

    /** Modifier bit of control. */
    public static final int CONTROL = 2;

    /** Modifier bit of alt. */
    public static final int ALT = 4;

    /** Modifier bit of super (Windows, command). */
    public static final int SUPER = 8;

    private final KeyboardKey key;
    private final int scanCode;
    private final int modifiers;

    /**
     * Creates the event.
     *
     * @param key the key
     * @param scanCode the platform scancode
     * @param modifiers modifier bits
     */
    protected KeyEvent(KeyboardKey key, int scanCode, int modifiers) {
        this.key = key;
        this.scanCode = scanCode;
        this.modifiers = modifiers;
    }

    /**
     * Returns the key, by position.
     *
     * @return the key
     */
    public final KeyboardKey key() {
        return key;
    }

    /**
     * Returns the raw platform scancode, for layouts the key table does not cover.
     *
     * @return the scancode
     */
    public final int scanCode() {
        return scanCode;
    }

    /**
     * Returns the held modifiers.
     *
     * @return bits {@link #SHIFT}, {@link #CONTROL}, {@link #ALT} and {@link #SUPER}
     */
    public final int modifiers() {
        return modifiers;
    }

    /**
     * Returns whether shift is held.
     *
     * @return {@code true} if held
     */
    public final boolean isShiftDown() {
        return (modifiers & SHIFT) != 0;
    }

    /**
     * Returns whether control is held.
     *
     * @return {@code true} if held
     */
    public final boolean isControlDown() {
        return (modifiers & CONTROL) != 0;
    }

    /**
     * Returns whether alt is held.
     *
     * @return {@code true} if held
     */
    public final boolean isAltDown() {
        return (modifiers & ALT) != 0;
    }

    /**
     * Returns whether super is held.
     *
     * @return {@code true} if held
     */
    public final boolean isSuperDown() {
        return (modifiers & SUPER) != 0;
    }
}
