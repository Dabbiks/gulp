package dev.gulp.platform;

/**
 * A font file opened for rasterising glyphs at runtime (FreeType on desktop, Canvas2D on the web).
 *
 * <pre>{@code
 * decoders.openFont(ttfBytes, callback); // later, in the callback:
 * GlyphBitmap a = face.rasterize(face.glyphIndex('A'), 24f);
 * }</pre>
 */
public interface PlatformFontFace {

    /**
     * Maps a code point to a glyph in this font.
     *
     * @param codePoint the Unicode code point
     * @return the glyph index, or {@code 0} if the font has no such glyph
     */
    int glyphIndex(int codePoint);

    /**
     * Rasterises a glyph.
     *
     * @param glyphIndex the glyph index
     * @param sizePixels font size in pixels
     * @return the coverage bitmap and metrics
     */
    GlyphBitmap rasterize(int glyphIndex, float sizePixels);

    /**
     * Returns the kerning between two glyphs.
     *
     * @param leftGlyph the first glyph
     * @param rightGlyph the following glyph
     * @param sizePixels font size in pixels
     * @return horizontal adjustment in pixels, usually negative or zero
     */
    float kerning(int leftGlyph, int rightGlyph, float sizePixels);

    /**
     * Returns the ascent at a size.
     *
     * @param sizePixels font size in pixels
     * @return distance from baseline to the top of tall glyphs
     */
    float ascent(float sizePixels);

    /**
     * Returns the descent at a size.
     *
     * @param sizePixels font size in pixels
     * @return distance from baseline to the bottom of descenders, positive
     */
    float descent(float sizePixels);

    /**
     * Returns the recommended distance between baselines at a size.
     *
     * @param sizePixels font size in pixels
     * @return the line height
     */
    float lineHeight(float sizePixels);

    /** Releases the font. */
    void dispose();
}
