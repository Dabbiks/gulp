package dev.gulp.platform;

/**
 * Initial window parameters, derived by {@code gulp-core} from the game's settings.
 *
 * <pre>{@code
 * var config = new WindowConfig("Coin Hunter", 960, 540, true, false, true);
 * }</pre>
 *
 * @param title window title, or browser tab title on the web
 * @param width width in logical points, at least 1
 * @param height height in logical points, at least 1
 * @param resizable whether the user can resize the window
 * @param fullscreen whether to start in fullscreen
 * @param vsync whether to synchronise buffer swaps with the display
 */
public record WindowConfig(String title, int width, int height, boolean resizable, boolean fullscreen, boolean vsync) {

    /**
     * Validates the size.
     *
     * @throws IllegalArgumentException if width or height is smaller than 1
     */
    public WindowConfig {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Window size must be at least 1x1, got " + width + "x" + height);
        }
    }
}
