package dev.gulp.api.input;

/**
 * Text was typed, after the keyboard layout and IME composition.
 *
 * <pre>{@code
 * on(CharTypedEvent.class, e -> field.insert(e.text()));
 * }</pre>
 */
public final class CharTypedEvent extends InputEvent {

    private final int codePoint;

    /**
     * Creates the event.
     *
     * @param codePoint the Unicode code point
     */
    public CharTypedEvent(int codePoint) {
        this.codePoint = codePoint;
    }

    /**
     * Returns the Unicode code point.
     *
     * @return the Unicode code point
     */
    public int codePoint() {
        return codePoint;
    }

    /**
     * Returns the typed text.
     *
     * @return the character as a string
     */
    public String text() {
        return new String(Character.toChars(codePoint));
    }
}
