package dev.gulp.api.text;

/**
 * How a font draws its glyphs.
 *
 * <pre>{@code
 * if (font.kind() == FontKind.MSDF) style = style.outline(2, Color.BLACK);
 * }</pre>
 */
public enum FontKind {
    /** Multi-channel signed distance field: sharp at any size, outline and shadow in the shader. */
    MSDF,
    /** Pre-rendered glyphs at one size: pixel art and hand-drawn fonts. */
    BITMAP,
    /** Rasterised on demand from a TTF or OTF file: any character, cached per size. */
    DYNAMIC
}
