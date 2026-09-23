package dev.gulp.api.graphics;

/**
 * How a texture is sampled when drawn smaller or larger than its size.
 *
 * <pre>{@code
 * texture.setFilter(TextureFilter.NEAREST);   // crisp pixel art
 * }</pre>
 */
public enum TextureFilter {
    /** Nearest texel: sharp pixels, for pixel art. */
    NEAREST,
    /** Bilinear: smooth scaling. */
    LINEAR,
    /** Nearest texel from the nearest mipmap level. */
    MIPMAP_NEAREST,
    /** Trilinear: smooth, and clean when strongly downscaled. */
    MIPMAP_LINEAR
}
