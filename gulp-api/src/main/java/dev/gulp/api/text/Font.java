package dev.gulp.api.text;

import org.jspecify.annotations.Nullable;

/**
 * A loaded font. Load one with {@code assets().load(AssetKey.font("coins:fonts/title"))}; the file decides the kind:
 * {@code .msdf.json} ({@link FontKind#MSDF}), {@code .fnt} ({@link FontKind#BITMAP}) or {@code .ttf} / {@code .otf}
 * ({@link FontKind#DYNAMIC}). Characters a font lacks are taken from its {@link #fallback()} chain.
 *
 * <pre>{@code
 * Font title = assets().get(TITLE_FONT);
 * title.setFallback(assets().get(DYNAMIC_FONT)); // for chat names in any script
 * draw.text("Wynik: 10", 8, 8, TextStyle.of(24).font(title));
 * }</pre>
 */
public interface Font {

    /**
     * Returns the name, usually the asset key.
     *
     * @return the name
     */
    String name();

    /**
     * Returns how glyphs are drawn.
     *
     * @return the kind
     */
    FontKind kind();

    /**
     * Returns whether this font itself (not its fallbacks) has a character.
     *
     * @param codePoint the character
     * @return {@code true} if it has a glyph
     */
    boolean hasGlyph(int codePoint);

    /**
     * Returns the distance between baselines of consecutive lines.
     *
     * @param size font size in drawing units
     * @return the line height
     */
    float lineHeight(float size);

    /**
     * Returns the height of the tallest letters above the baseline.
     *
     * @param size font size
     * @return the ascent
     */
    float ascent(float size);

    /**
     * Returns the depth of descenders below the baseline, as a positive number.
     *
     * @param size font size
     * @return the descent
     */
    float descent(float size);

    /**
     * Returns the width of a single line of text, with kerning, falling back for missing characters.
     *
     * @param text the text
     * @param size font size
     * @return the advance width
     */
    float measure(String text, float size);

    /**
     * Returns the font used for characters this one lacks.
     *
     * @return the next font of the chain, or {@code null}
     */
    @Nullable Font fallback();

    /**
     * Sets the font used for characters this one lacks.
     *
     * @param fallback the next font, or {@code null} for none
     * @return this font
     * @throws IllegalArgumentException if the chain would loop
     */
    Font setFallback(@Nullable Font fallback);
}
