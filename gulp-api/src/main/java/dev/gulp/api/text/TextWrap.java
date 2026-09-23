package dev.gulp.api.text;

/**
 * How lines break when text is wider than its box.
 *
 * <pre>{@code
 * TextBox box = TextBox.width(200).wrap(TextWrap.CHARACTERS);
 * }</pre>
 */
public enum TextWrap {
    /** Break between words; a word longer than the line is broken between characters. */
    WORDS,
    /** Break anywhere, for scripts without spaces. */
    CHARACTERS,
    /** Never break; only explicit newlines start lines. */
    NONE
}
