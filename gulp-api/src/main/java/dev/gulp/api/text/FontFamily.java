package dev.gulp.api.text;

import org.jspecify.annotations.Nullable;

/**
 * The regular, bold, italic and bold-italic variants of a font. Missing variants are imitated: bold by thickening,
 * italic by slanting the regular glyphs.
 *
 * <pre>{@code
 * FontFamily inter = new FontFamily(assets().get(REGULAR), assets().get(BOLD), null, null);
 * draw.text(Text.of("Ważne").bold(), 8, 8, TextStyle.of(20).font(inter));
 * }</pre>
 *
 * @param regular the regular face
 * @param bold the bold face, or {@code null}
 * @param italic the italic face, or {@code null}
 * @param boldItalic the bold italic face, or {@code null}
 */
public record FontFamily(
        Font regular,
        @Nullable Font bold,
        @Nullable Font italic,
        @Nullable Font boldItalic) {

    /**
     * Creates a family with only a regular face.
     *
     * @param regular the font
     * @return the family
     */
    public static FontFamily of(Font regular) {
        return new FontFamily(regular, null, null, null);
    }

    /**
     * Picks the face for a style.
     *
     * @param wantBold whether bold is wanted
     * @param wantItalic whether italic is wanted
     * @return the best face; imitation is up to the renderer when the result is not the wanted variant
     */
    public Font pick(boolean wantBold, boolean wantItalic) {
        if (wantBold && wantItalic && boldItalic != null) {
            return boldItalic;
        }
        if (wantBold && bold != null) {
            return bold;
        }
        if (wantItalic && italic != null) {
            return italic;
        }
        return regular;
    }

    /**
     * Returns whether a real (not imitated) variant exists.
     *
     * @param wantBold bold
     * @param wantItalic italic
     * @return {@code true} if {@link #pick(boolean, boolean)} returns exactly that variant
     */
    public boolean hasVariant(boolean wantBold, boolean wantItalic) {
        if (wantBold && wantItalic) {
            return boldItalic != null;
        }
        if (wantBold) {
            return bold != null;
        }
        if (wantItalic) {
            return italic != null;
        }
        return true;
    }
}
