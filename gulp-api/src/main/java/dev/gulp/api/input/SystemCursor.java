package dev.gulp.api.input;

/**
 * Standard cursor shapes drawn by the system.
 *
 * <pre>{@code
 * input().setCursor(hovered ? SystemCursor.HAND : SystemCursor.ARROW);
 * }</pre>
 */
public enum SystemCursor implements Cursor {
    /** The normal arrow. */
    ARROW,
    /** A pointing hand, for links and buttons. */
    HAND,
    /** A text caret. */
    TEXT,
    /** A crosshair. */
    CROSSHAIR,
    /** Horizontal resize arrows. */
    RESIZE_HORIZONTAL,
    /** Vertical resize arrows. */
    RESIZE_VERTICAL,
    /** Four-way move arrows. */
    RESIZE_ALL,
    /** A "not allowed" sign. */
    NOT_ALLOWED
}
