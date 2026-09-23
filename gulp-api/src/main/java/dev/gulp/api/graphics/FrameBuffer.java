package dev.gulp.api.graphics;

/**
 * An off-screen render target backed by a texture.
 *
 * <pre>{@code
 * FrameBuffer minimap = graphics().frameBuffer(128, 128);
 * draw.into(minimap, d -> d.rect(0, 0, 128, 128));
 * draw.image(minimap.region(), 10, 10);
 * }</pre>
 */
public interface FrameBuffer {

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
     * The color texture.
     *
     * @return the texture
     */
    Texture texture();

    /**
     * The color texture as a region, oriented so that drawing it shows the image upright.
     *
     * @return the region
     */
    TextureRegion region();

    /**
     * Returns whether a stencil buffer is attached.
     *
     * @return {@code true} if it has a stencil buffer
     */
    boolean hasStencil();

    /** Frees the GPU memory. */
    void dispose();
}
