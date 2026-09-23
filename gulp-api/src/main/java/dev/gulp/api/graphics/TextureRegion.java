package dev.gulp.api.graphics;

/**
 * A rectangular part of a texture, with texture coordinates. Atlas regions may be packed rotated and have transparent
 * borders trimmed; {@link #offsetX()}, {@link #offsetY()}, {@link #originalWidth()} and {@link #originalHeight()}
 * describe the untrimmed image so drawing places it correctly.
 *
 * <pre>{@code
 * TextureRegion frame = sheet.region(32, 0, 16, 16);
 * draw.image(frame.flipX(), x, y, 1, 1);
 * }</pre>
 */
public final class TextureRegion {

    private final Texture texture;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final boolean flipX;
    private final boolean flipY;
    private final boolean rotated;
    private final int offsetX;
    private final int offsetY;
    private final int originalWidth;
    private final int originalHeight;

    /**
     * Creates a plain region.
     *
     * @param texture the texture
     * @param x left edge in pixels
     * @param y top edge in pixels
     * @param width width in pixels
     * @param height height in pixels
     * @throws IllegalArgumentException if the region is empty
     */
    public TextureRegion(Texture texture, int x, int y, int width, int height) {
        this(texture, x, y, width, height, false, false, false, 0, 0, width, height);
    }

    /**
     * Creates a region with every attribute, as atlas loaders do.
     *
     * @param texture the texture
     * @param x left edge in pixels
     * @param y top edge in pixels
     * @param width width of the packed area in pixels
     * @param height height of the packed area in pixels
     * @param flipX whether to mirror horizontally
     * @param flipY whether to mirror vertically
     * @param rotated whether the image is stored rotated 90 degrees clockwise
     * @param offsetX left trim of the original image in pixels
     * @param offsetY top trim of the original image in pixels
     * @param originalWidth width before trimming
     * @param originalHeight height before trimming
     * @throws IllegalArgumentException if the region is empty
     */
    public TextureRegion(
            Texture texture,
            int x,
            int y,
            int width,
            int height,
            boolean flipX,
            boolean flipY,
            boolean rotated,
            int offsetX,
            int offsetY,
            int originalWidth,
            int originalHeight) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Region must not be empty: " + width + "x" + height);
        }
        this.texture = texture;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.flipX = flipX;
        this.flipY = flipY;
        this.rotated = rotated;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.originalWidth = originalWidth;
        this.originalHeight = originalHeight;
    }

    /**
     * The texture.
     *
     * @return the texture
     */
    public Texture texture() {
        return texture;
    }

    /**
     * Left edge in the texture.
     *
     * @return pixels
     */
    public int x() {
        return x;
    }

    /**
     * Top edge in the texture.
     *
     * @return pixels
     */
    public int y() {
        return y;
    }

    /**
     * Width of the image as displayed (after un-rotating).
     *
     * @return pixels
     */
    public int width() {
        return rotated ? height : width;
    }

    /**
     * Height of the image as displayed (after un-rotating).
     *
     * @return pixels
     */
    public int height() {
        return rotated ? width : height;
    }

    /**
     * Left texture coordinate.
     *
     * @return {@code 0..1}
     */
    public float u() {
        return (flipX ? x + width : x) / (float) texture.width();
    }

    /**
     * Top texture coordinate.
     *
     * @return {@code 0..1}
     */
    public float v() {
        return (flipY ? y + height : y) / (float) texture.height();
    }

    /**
     * Right texture coordinate.
     *
     * @return {@code 0..1}
     */
    public float u2() {
        return (flipX ? x : x + width) / (float) texture.width();
    }

    /**
     * Bottom texture coordinate.
     *
     * @return {@code 0..1}
     */
    public float v2() {
        return (flipY ? y : y + height) / (float) texture.height();
    }

    /**
     * Whether the region is mirrored horizontally.
     *
     * @return {@code true} if flipped
     */
    public boolean isFlipX() {
        return flipX;
    }

    /**
     * Whether the region is mirrored vertically.
     *
     * @return {@code true} if flipped
     */
    public boolean isFlipY() {
        return flipY;
    }

    /**
     * Whether the image is stored rotated 90 degrees clockwise in the texture.
     *
     * @return {@code true} if rotated
     */
    public boolean isRotated() {
        return rotated;
    }

    /**
     * Left trim.
     *
     * @return pixels removed from the left of the original image
     */
    public int offsetX() {
        return offsetX;
    }

    /**
     * Top trim.
     *
     * @return pixels removed from the top of the original image
     */
    public int offsetY() {
        return offsetY;
    }

    /**
     * Width before trimming.
     *
     * @return pixels
     */
    public int originalWidth() {
        return originalWidth;
    }

    /**
     * Height before trimming.
     *
     * @return pixels
     */
    public int originalHeight() {
        return originalHeight;
    }

    /**
     * Returns a horizontally mirrored copy.
     *
     * @return the mirrored region
     */
    public TextureRegion flipX() {
        return new TextureRegion(
                texture, x, y, width, height, !flipX, flipY, rotated, offsetX, offsetY, originalWidth, originalHeight);
    }

    /**
     * Returns a vertically mirrored copy.
     *
     * @return the mirrored region
     */
    public TextureRegion flipY() {
        return new TextureRegion(
                texture, x, y, width, height, flipX, !flipY, rotated, offsetX, offsetY, originalWidth, originalHeight);
    }

    /**
     * Returns a part of this region.
     *
     * @param subX left edge relative to this region
     * @param subY top edge relative to this region
     * @param subWidth width
     * @param subHeight height
     * @return the sub-region
     */
    public TextureRegion sub(int subX, int subY, int subWidth, int subHeight) {
        return new TextureRegion(texture, x + subX, y + subY, subWidth, subHeight);
    }

    @Override
    public String toString() {
        return "TextureRegion[" + x + "," + y + " " + width + "x" + height + "]";
    }
}
