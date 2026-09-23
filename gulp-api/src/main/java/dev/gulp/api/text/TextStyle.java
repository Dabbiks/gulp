package dev.gulp.api.text;

import dev.gulp.api.graphics.Color;
import org.jspecify.annotations.Nullable;

/**
 * How text looks: font, size, color, outline, shadow and spacing. Immutable; every method returns a changed copy, so
 * styles can be kept in constants and shared.
 *
 * <pre>{@code
 * static final TextStyle TITLE = TextStyle.of(32).color(Color.WHITE).outline(2, Color.BLACK).shadow(2, 2, Color.rgba(0x00000080));
 * draw.text("Coin Hunter", 20, 20, TITLE);
 * }</pre>
 *
 * @param font the font family, or {@code null} for the engine's default font
 * @param size font size in drawing units (logical pixels on screen layers)
 * @param color text color
 * @param outlineWidth outline width in drawing units, 0 for none
 * @param outlineColor outline color
 * @param shadowX shadow offset to the right
 * @param shadowY shadow offset down
 * @param shadowColor shadow color; fully transparent for none
 * @param letterSpacing extra space after every character
 * @param lineHeight line distance as a multiple of the font's own line height
 * @param isBold whether to use (or imitate) the bold face
 * @param isItalic whether to use (or imitate) the italic face
 */
public record TextStyle(
        @Nullable FontFamily font,
        float size,
        Color color,
        float outlineWidth,
        Color outlineColor,
        float shadowX,
        float shadowY,
        Color shadowColor,
        float letterSpacing,
        float lineHeight,
        boolean isBold,
        boolean isItalic) {

    /** 16 units, white, default font, no outline or shadow. */
    public static final TextStyle DEFAULT =
            new TextStyle(null, 16f, Color.WHITE, 0f, Color.BLACK, 0f, 0f, Color.CLEAR, 0f, 1f, false, false);

    /**
     * Validates the values.
     *
     * @param font the font family
     * @param size the size
     * @param color the color
     * @param outlineWidth the outline width
     * @param outlineColor the outline color
     * @param shadowX the shadow X offset
     * @param shadowY the shadow Y offset
     * @param shadowColor the shadow color
     * @param letterSpacing the letter spacing
     * @param lineHeight the line height factor
     * @param isBold bold
     * @param isItalic italic
     */
    public TextStyle {
        if (!(size > 0f) || Float.isInfinite(size)) {
            throw new IllegalArgumentException("Text size must be positive: " + size);
        }
        if (outlineWidth < 0f) {
            throw new IllegalArgumentException("Outline width must not be negative: " + outlineWidth);
        }
        if (!(lineHeight > 0f)) {
            throw new IllegalArgumentException("Line height must be positive: " + lineHeight);
        }
    }

    /**
     * Returns the default style with another size.
     *
     * @param size the size
     * @return the style
     */
    public static TextStyle of(float size) {
        return DEFAULT.size(size);
    }

    /**
     * Returns a copy with another font.
     *
     * @param newFont the font
     * @return the style
     */
    public TextStyle font(Font newFont) {
        return font(FontFamily.of(newFont));
    }

    /**
     * Returns a copy with another font family.
     *
     * @param family the family, or {@code null} for the default font
     * @return the style
     */
    public TextStyle font(@Nullable FontFamily family) {
        return new TextStyle(
                family,
                size,
                color,
                outlineWidth,
                outlineColor,
                shadowX,
                shadowY,
                shadowColor,
                letterSpacing,
                lineHeight,
                isBold,
                isItalic);
    }

    /**
     * Returns a copy with another size.
     *
     * @param newSize the size
     * @return the style
     */
    public TextStyle size(float newSize) {
        return new TextStyle(
                font,
                newSize,
                color,
                outlineWidth,
                outlineColor,
                shadowX,
                shadowY,
                shadowColor,
                letterSpacing,
                lineHeight,
                isBold,
                isItalic);
    }

    /**
     * Returns a copy with another color.
     *
     * @param newColor the color
     * @return the style
     */
    public TextStyle color(Color newColor) {
        return new TextStyle(
                font,
                size,
                newColor,
                outlineWidth,
                outlineColor,
                shadowX,
                shadowY,
                shadowColor,
                letterSpacing,
                lineHeight,
                isBold,
                isItalic);
    }

    /**
     * Returns a copy with an outline. MSDF fonts draw it in the shader; other fonts approximate it.
     *
     * @param width the width, 0 to remove it
     * @param newColor the outline color
     * @return the style
     */
    public TextStyle outline(float width, Color newColor) {
        return new TextStyle(
                font,
                size,
                color,
                width,
                newColor,
                shadowX,
                shadowY,
                shadowColor,
                letterSpacing,
                lineHeight,
                isBold,
                isItalic);
    }

    /**
     * Returns a copy with a drop shadow.
     *
     * @param dx offset to the right
     * @param dy offset down
     * @param newColor the shadow color
     * @return the style
     */
    public TextStyle shadow(float dx, float dy, Color newColor) {
        return new TextStyle(
                font,
                size,
                color,
                outlineWidth,
                outlineColor,
                dx,
                dy,
                newColor,
                letterSpacing,
                lineHeight,
                isBold,
                isItalic);
    }

    /**
     * Returns a copy with extra space between characters.
     *
     * @param spacing the extra space, may be negative
     * @return the style
     */
    public TextStyle letterSpacing(float spacing) {
        return new TextStyle(
                font,
                size,
                color,
                outlineWidth,
                outlineColor,
                shadowX,
                shadowY,
                shadowColor,
                spacing,
                lineHeight,
                isBold,
                isItalic);
    }

    /**
     * Returns a copy with another line distance.
     *
     * @param factor multiple of the font's line height
     * @return the style
     */
    public TextStyle lineHeight(float factor) {
        return new TextStyle(
                font,
                size,
                color,
                outlineWidth,
                outlineColor,
                shadowX,
                shadowY,
                shadowColor,
                letterSpacing,
                factor,
                isBold,
                isItalic);
    }

    /**
     * Returns a bold copy.
     *
     * @return the style
     */
    public TextStyle bold() {
        return bold(true);
    }

    /**
     * Returns a copy with bold on or off.
     *
     * @param on whether bold
     * @return the style
     */
    public TextStyle bold(boolean on) {
        return new TextStyle(
                font,
                size,
                color,
                outlineWidth,
                outlineColor,
                shadowX,
                shadowY,
                shadowColor,
                letterSpacing,
                lineHeight,
                on,
                isItalic);
    }

    /**
     * Returns an italic copy.
     *
     * @return the style
     */
    public TextStyle italic() {
        return italic(true);
    }

    /**
     * Returns a copy with italic on or off.
     *
     * @param on whether italic
     * @return the style
     */
    public TextStyle italic(boolean on) {
        return new TextStyle(
                font,
                size,
                color,
                outlineWidth,
                outlineColor,
                shadowX,
                shadowY,
                shadowColor,
                letterSpacing,
                lineHeight,
                isBold,
                on);
    }

    /**
     * Returns whether a shadow is drawn.
     *
     * @return {@code true} if the shadow color is not transparent
     */
    public boolean hasShadow() {
        return shadowColor.a() > 0f;
    }
}
