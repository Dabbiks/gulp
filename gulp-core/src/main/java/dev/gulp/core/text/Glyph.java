package dev.gulp.core.text;

import dev.gulp.api.graphics.Texture;
import org.jspecify.annotations.Nullable;

/**
 * One character of a font. Metrics are in em (multiply by the font size); the quad is relative to the pen position on
 * the baseline, with Y down, so {@code top} is usually negative.
 */
public final class Glyph {

    /** The character. */
    public final int codePoint;

    /** Horizontal pen movement in em. */
    public final float advance;

    /** Page texture, or {@code null} for characters without an image (space) or not rasterised yet (dynamic fonts). */
    public @Nullable Texture texture;

    /** Texture coordinates of the quad. */
    public float u;

    /** Texture coordinates of the quad. */
    public float v;

    /** Texture coordinates of the quad. */
    public float u2;

    /** Texture coordinates of the quad. */
    public float v2;

    /** Quad edges relative to the pen, in em. */
    public float left;

    /** Quad edges relative to the pen, in em. */
    public float top;

    /** Quad edges relative to the pen, in em. */
    public float right;

    /** Quad edges relative to the pen, in em. */
    public float bottom;

    /**
     * Creates a glyph without an image.
     *
     * @param codePoint the character
     * @param advance pen movement in em
     */
    public Glyph(int codePoint, float advance) {
        this.codePoint = codePoint;
        this.advance = advance;
    }

    /**
     * Sets the image of the glyph.
     *
     * @param page the page texture
     * @param x left edge on the page in pixels
     * @param y top edge on the page in pixels
     * @param width width in pixels
     * @param height height in pixels
     * @param left quad left edge in em
     * @param top quad top edge in em
     * @param right quad right edge in em
     * @param bottom quad bottom edge in em
     * @return this glyph
     */
    public Glyph image(
            Texture page, int x, int y, int width, int height, float left, float top, float right, float bottom) {
        this.texture = page;
        this.u = x / (float) page.width();
        this.v = y / (float) page.height();
        this.u2 = (x + width) / (float) page.width();
        this.v2 = (y + height) / (float) page.height();
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        return this;
    }

    /**
     * Returns whether the glyph has an image to draw.
     *
     * @return {@code true} if a quad is drawn
     */
    public boolean hasImage() {
        return texture != null && right > left;
    }
}
