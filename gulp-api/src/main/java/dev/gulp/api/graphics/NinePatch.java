package dev.gulp.api.graphics;

/**
 * A region split into nine parts: the corners keep their size, the edges and center stretch. Used for panels and
 * buttons of any size.
 *
 * <pre>{@code
 * NinePatch panel = new NinePatch(texture.region(), 4, 4, 4, 4);
 * draw.ninePatch(panel, Rect.of(10, 10, 200, 80));
 * }</pre>
 *
 * @param region the source region
 * @param left width of the left column in pixels
 * @param right width of the right column in pixels
 * @param top height of the top row in pixels
 * @param bottom height of the bottom row in pixels
 */
public record NinePatch(TextureRegion region, int left, int right, int top, int bottom) {

    /**
     * Validates the borders.
     *
     * @throws IllegalArgumentException if a border is negative or the borders do not fit the region
     */
    public NinePatch {
        if (left < 0 || right < 0 || top < 0 || bottom < 0) {
            throw new IllegalArgumentException("Nine-patch borders must not be negative");
        }
        if (left + right > region.width() || top + bottom > region.height()) {
            throw new IllegalArgumentException("Nine-patch borders do not fit the region");
        }
    }
}
