package dev.gulp.core.text;

import dev.gulp.api.text.Font;
import org.jspecify.annotations.Nullable;

/** Base of the engine's fonts: metrics in em, glyph lookup with the fallback chain, measuring. */
public abstract class FontImpl implements Font {

    private final String name;
    private @Nullable Font fallback;

    /**
     * Creates the font.
     *
     * @param name the name, usually the asset key
     */
    protected FontImpl(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    /**
     * Returns the glyph of a character in this font only.
     *
     * @param codePoint the character
     * @return the glyph, or {@code null} if the font lacks it
     */
    public abstract @Nullable Glyph glyph(int codePoint);

    /**
     * Returns the glyph image to draw at a size; only dynamic fonts rasterise per size.
     *
     * @param glyph a glyph of this font
     * @param pixelSize the font size in screen pixels
     * @return the glyph with its image
     */
    public Glyph image(Glyph glyph, float pixelSize) {
        return glyph;
    }

    /**
     * Returns the kerning between two characters of this font.
     *
     * @param first the left character
     * @param second the right character
     * @return the adjustment in em, usually negative
     */
    public abstract float kerning(int first, int second);

    /**
     * Returns the ascent in em.
     *
     * @return the ascent
     */
    public abstract float ascentEm();

    /**
     * Returns the descent in em, positive.
     *
     * @return the descent
     */
    public abstract float descentEm();

    /**
     * Returns the line height in em.
     *
     * @return the line height
     */
    public abstract float lineHeightEm();

    /**
     * Returns the distance field range in atlas pixels, for MSDF fonts.
     *
     * @return the range, 0 for other fonts
     */
    public float distanceRange() {
        return 0f;
    }

    /** Frees GPU memory owned by the font itself (not asset dependencies). */
    public void dispose() {}

    /**
     * Finds the font of the chain that has a character.
     *
     * @param codePoint the character
     * @return this font, a fallback, or {@code null} if none has it
     */
    public @Nullable FontImpl resolve(int codePoint) {
        Font font = this;
        while (font instanceof FontImpl impl) {
            if (impl.glyph(codePoint) != null) {
                return impl;
            }
            font = impl.fallback;
        }
        return null;
    }

    @Override
    public boolean hasGlyph(int codePoint) {
        return glyph(codePoint) != null;
    }

    @Override
    public float lineHeight(float size) {
        return lineHeightEm() * size;
    }

    @Override
    public float ascent(float size) {
        return ascentEm() * size;
    }

    @Override
    public float descent(float size) {
        return descentEm() * size;
    }

    @Override
    public float measure(String text, float size) {
        float width = 0f;
        float lineWidth = 0f;
        int previous = -1;
        FontImpl previousFont = null;
        for (int i = 0; i < text.length(); ) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            if (c == '\n') {
                width = Math.max(width, lineWidth);
                lineWidth = 0f;
                previous = -1;
                continue;
            }
            FontImpl font = resolve(c);
            if (font == null) {
                continue;
            }
            Glyph glyph = font.glyph(c);
            if (glyph == null) {
                continue;
            }
            if (previous >= 0 && font == previousFont) {
                lineWidth += font.kerning(previous, c) * size;
            }
            lineWidth += glyph.advance * size;
            previous = c;
            previousFont = font;
        }
        return Math.max(width, lineWidth);
    }

    @Override
    public @Nullable Font fallback() {
        return fallback;
    }

    @Override
    public Font setFallback(@Nullable Font next) {
        Font font = next;
        while (font != null) {
            if (font == this) {
                throw new IllegalArgumentException("Font fallback chain of '" + name + "' would loop");
            }
            font = font.fallback();
        }
        this.fallback = next;
        return this;
    }

    @Override
    public String toString() {
        return kind() + " font " + name;
    }
}
