package dev.gulp.api.graphics;

/**
 * An image on the GPU. Created by {@link Graphics#texture(Pixmap)}; stored with premultiplied alpha.
 *
 * <pre>{@code
 * Texture texture = graphics().texture(pixmap);
 * texture.setFilter(TextureFilter.NEAREST);
 * draw.image(texture.region(), 10, 10);
 * texture.dispose();
 * }</pre>
 */
public interface Texture {

    /**
     * Width in pixels.
     *
     * @return the width
     */
    int width();

    /**
     * Height in pixels.
     *
     * @return the height
     */
    int height();

    /**
     * Returns the sampling filter.
     *
     * @return the filter
     */
    TextureFilter filter();

    /**
     * Changes the sampling filter; mipmap filters generate mipmaps.
     *
     * @param filter the filter
     */
    void setFilter(TextureFilter filter);

    /**
     * Returns the wrap mode.
     *
     * @return the wrap mode
     */
    TextureWrap wrap();

    /**
     * Changes the wrap mode.
     *
     * @param wrap the wrap mode
     */
    void setWrap(TextureWrap wrap);

    /**
     * Replaces part of the texture with a pixmap.
     *
     * @param pixmap the new pixels
     * @param x left edge of the updated area
     * @param y top edge of the updated area
     * @throws IllegalArgumentException if the pixmap does not fit at that position
     */
    void update(Pixmap pixmap, int x, int y);

    /**
     * The whole texture as a region.
     *
     * @return the region
     */
    TextureRegion region();

    /**
     * Part of the texture as a region.
     *
     * @param x left edge in pixels
     * @param y top edge in pixels
     * @param width width in pixels
     * @param height height in pixels
     * @return the region
     */
    TextureRegion region(int x, int y, int width, int height);

    /** Frees the GPU memory. Drawing a disposed texture draws the fallback texture. */
    void dispose();

    /**
     * Returns whether {@link #dispose()} was called.
     *
     * @return {@code true} if disposed
     */
    boolean isDisposed();
}
