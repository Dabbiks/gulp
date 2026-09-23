package dev.gulp.platform;

import java.nio.ByteBuffer;

/**
 * One rasterised glyph as an 8-bit coverage bitmap, plus its placement.
 *
 * <pre>{@code
 * GlyphBitmap glyph = face.rasterize(face.glyphIndex('A'), 32f);
 * }</pre>
 *
 * @param width bitmap width in pixels
 * @param height bitmap height in pixels
 * @param offsetX distance from the pen position to the bitmap's left edge
 * @param offsetY distance from the baseline up to the bitmap's top edge
 * @param advance horizontal pen advance in pixels
 * @param coverage {@code width * height} bytes, rows from top to bottom
 */
public record GlyphBitmap(int width, int height, float offsetX, float offsetY, float advance, ByteBuffer coverage) {}
