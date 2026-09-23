package dev.gulp.api.graphics;

/**
 * What a texture shows outside its {@code 0..1} coordinates.
 *
 * <pre>{@code
 * background.setWrap(TextureWrap.REPEAT);
 * }</pre>
 */
public enum TextureWrap {
    /** Repeats the edge texels. */
    CLAMP,
    /** Tiles the texture. */
    REPEAT,
    /** Tiles the texture, mirroring every other copy. */
    MIRROR
}
