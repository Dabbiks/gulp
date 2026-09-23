package dev.gulp.core.text;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import dev.gulp.api.text.TextStyle;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A finished layout: parallel arrays with one entry per drawn character or image, positions relative to the top-left
 * corner, Y down. Drawing reads the arrays without allocating.
 */
public final class TextLayoutImpl implements TextLayout {

    /** Flag: imitate bold. */
    public static final int FAKE_BOLD = 1;

    /** Flag: imitate italic. */
    public static final int FAKE_ITALIC = 2;

    /** A clickable area. */
    record LinkArea(String link, float x0, float y0, float x1, float y1) {}

    final Text text;
    final TextStyle style;
    final TextBox box;
    final int count;
    /** Pen x of each character. */
    final float[] x;
    /** Baseline y of each character. */
    final float[] y;

    final float[] size;
    final Glyph[] glyphs;
    final FontImpl[] fonts;
    final Color[] colors;
    final int[] flags;
    final int[] effects;
    final TextureRegion[] images;
    final List<LinkArea> links;
    final float width;
    final float height;
    final int lines;
    final boolean truncated;

    TextLayoutImpl(
            Text text,
            TextStyle style,
            TextBox box,
            int count,
            float[] x,
            float[] y,
            float[] size,
            Glyph[] glyphs,
            FontImpl[] fonts,
            Color[] colors,
            int[] flags,
            int[] effects,
            TextureRegion[] images,
            List<LinkArea> links,
            float width,
            float height,
            int lines,
            boolean truncated) {
        this.text = text;
        this.style = style;
        this.box = box;
        this.count = count;
        this.x = x;
        this.y = y;
        this.size = size;
        this.glyphs = glyphs;
        this.fonts = fonts;
        this.colors = colors;
        this.flags = flags;
        this.effects = effects;
        this.images = images;
        this.links = links;
        this.width = width;
        this.height = height;
        this.lines = lines;
        this.truncated = truncated;
    }

    /**
     * Number of drawn entries.
     *
     * @return the count
     */
    public int count() {
        return count;
    }

    /**
     * Pen x of an entry.
     *
     * @param i the entry
     * @return x relative to the layout
     */
    public float x(int i) {
        return x[i];
    }

    /**
     * Baseline y of an entry.
     *
     * @param i the entry
     * @return y relative to the layout
     */
    public float y(int i) {
        return y[i];
    }

    /**
     * Font size of an entry.
     *
     * @param i the entry
     * @return the size
     */
    public float size(int i) {
        return size[i];
    }

    /**
     * Glyph of an entry, or {@code null} for an image.
     *
     * @param i the entry
     * @return the glyph
     */
    public @Nullable Glyph glyph(int i) {
        return glyphs[i];
    }

    /**
     * Font of an entry, or {@code null} for an image.
     *
     * @param i the entry
     * @return the font
     */
    public @Nullable FontImpl font(int i) {
        return fonts[i];
    }

    /**
     * Color of an entry.
     *
     * @param i the entry
     * @return the color
     */
    public Color color(int i) {
        return colors[i];
    }

    /**
     * Flags of an entry ({@link #FAKE_BOLD}, {@link #FAKE_ITALIC}).
     *
     * @param i the entry
     * @return the flags
     */
    public int flags(int i) {
        return flags[i];
    }

    /**
     * Effects of an entry as a bit set of {@code TextEffect} ordinals.
     *
     * @param i the entry
     * @return the bits
     */
    public int effects(int i) {
        return effects[i];
    }

    /**
     * Inline image of an entry, or {@code null} for a character.
     *
     * @param i the entry
     * @return the region
     */
    public @Nullable TextureRegion image(int i) {
        return images[i];
    }

    @Override
    public Text text() {
        return text;
    }

    @Override
    public TextStyle style() {
        return style;
    }

    @Override
    public TextBox box() {
        return box;
    }

    @Override
    public float width() {
        return width;
    }

    @Override
    public float height() {
        return height;
    }

    @Override
    public int lineCount() {
        return lines;
    }

    @Override
    public int characterCount() {
        return count;
    }

    @Override
    public boolean isTruncated() {
        return truncated;
    }

    @Override
    public @Nullable String linkAt(float px, float py) {
        for (LinkArea area : links) {
            if (px >= area.x0() && px < area.x1() && py >= area.y0() && py < area.y1()) {
                return area.link();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "TextLayout[" + lines + " lines, " + width + "x" + height + "]";
    }
}
