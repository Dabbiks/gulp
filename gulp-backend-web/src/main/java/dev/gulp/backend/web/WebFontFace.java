package dev.gulp.backend.web;

import dev.gulp.platform.GlyphBitmap;
import dev.gulp.platform.PlatformFontFace;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A font registered with the browser's FontFace API and rasterised with Canvas2D. Glyph indices are the code points:
 * the browser does not reveal which characters a font has, and draws missing ones from its own fallback fonts.
 */
final class WebFontFace implements PlatformFontFace {

    private final String family;

    WebFontFace(String family) {
        this.family = family;
    }

    @Override
    public int glyphIndex(int codePoint) {
        return codePoint;
    }

    @Override
    public GlyphBitmap rasterize(int glyphIndex, float sizePixels) {
        Js.rasterize(family, glyphIndex, sizePixels);
        float[] header = Js.lastGlyphHeader().copyToJavaArray();
        byte[] data = Js.lastGlyphData().copyToJavaArray();
        ByteBuffer coverage = ByteBuffer.allocateDirect(data.length).order(ByteOrder.nativeOrder());
        coverage.put(data).flip();
        return new GlyphBitmap((int) header[0], (int) header[1], header[2], header[3], header[4], coverage);
    }

    @Override
    public float kerning(int leftGlyph, int rightGlyph, float sizePixels) {
        String left = new String(Character.toChars(leftGlyph));
        String right = new String(Character.toChars(rightGlyph));
        return (float) (Js.measure(family, sizePixels, left + right)
                - Js.measure(family, sizePixels, left)
                - Js.measure(family, sizePixels, right));
    }

    @Override
    public float ascent(float sizePixels) {
        return Js.fontMetrics(family, sizePixels).copyToJavaArray()[0];
    }

    @Override
    public float descent(float sizePixels) {
        return Js.fontMetrics(family, sizePixels).copyToJavaArray()[1];
    }

    @Override
    public float lineHeight(float sizePixels) {
        return Js.fontMetrics(family, sizePixels).copyToJavaArray()[2];
    }

    @Override
    public void dispose() {
        // The browser keeps registered fonts for the page's lifetime.
    }
}
