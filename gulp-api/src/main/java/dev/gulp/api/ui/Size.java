package dev.gulp.api.ui;

/**
 * A width and a height in UI points, for example the minimum size a node measured.
 *
 * <pre>{@code
 * Size min = button.minSize();
 * }</pre>
 *
 * @param width horizontal extent
 * @param height vertical extent
 */
public record Size(float width, float height) {

    /** Zero by zero. */
    public static final Size ZERO = new Size(0f, 0f);

    /**
     * Creates a size.
     *
     * @param width horizontal extent
     * @param height vertical extent
     * @return the size
     */
    public static Size of(float width, float height) {
        return new Size(width, height);
    }
}
