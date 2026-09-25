package dev.gulp.api.ui;

/**
 * What a node does with the pointer. Clicks that no UI node stops reach the game (for example the world under a HUD).
 *
 * <pre>{@code
 * panel(stats).mouseFilter(MouseFilter.IGNORE);   // clicks go through the panel into the world
 * }</pre>
 */
public enum MouseFilter {
    /** The node receives the pointer and nothing below it does. Default for widgets and panels. */
    STOP,
    /** The node receives hover and tooltips, and the pointer also reaches what is below. */
    PASS,
    /** The node is transparent to the pointer; its children still receive it. Default for layout containers. */
    IGNORE
}
