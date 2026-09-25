package dev.gulp.api.ui;

/**
 * The interaction state a theme styles: a node shows the first state that applies, in the order {@code DISABLED},
 * {@code PRESSED}, {@code CHECKED}, {@code FOCUSED}, {@code HOVER}, {@code NORMAL}.
 *
 * <pre>{@code
 * Theme.builder(key("neon")).style("button", WidgetState.HOVER, s -> s.background(Color.hex("#ff00aa"))).build();
 * }</pre>
 */
public enum WidgetState {
    /** Nothing special. */
    NORMAL,
    /** The pointer is over the node. */
    HOVER,
    /** The node is being pressed. */
    PRESSED,
    /** The node has the keyboard or gamepad focus. */
    FOCUSED,
    /** The node is disabled. */
    DISABLED,
    /** The node is checked or selected (checkbox, toggle, selected tab or list row). */
    CHECKED
}
