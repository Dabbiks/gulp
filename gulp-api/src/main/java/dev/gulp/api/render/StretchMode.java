package dev.gulp.api.render;

/**
 * How the base resolution maps to the window.
 *
 * <pre>{@code
 * settings.baseResolution(480, 270).stretchMode(StretchMode.VIEWPORT).integerScaling(true);   // pixel art
 * }</pre>
 */
public enum StretchMode {
    /** No base resolution: one logical unit is one window point. */
    DISABLED,
    /** Draws at window resolution with coordinates scaled from the base resolution: sharp text and shapes. */
    CANVAS,
    /** Draws the world into a buffer of the base resolution, then scales it up: pixel art. */
    VIEWPORT
}
