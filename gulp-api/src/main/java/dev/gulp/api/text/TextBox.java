package dev.gulp.api.text;

/**
 * The box text is laid out in: width, wrapping, line limit and alignment of lines.
 *
 * <pre>{@code
 * TextBox box = TextBox.width(240).maxLines(2).ellipsis(true).align(TextAlign.TOP);
 * TextLayout layout = graphics().layout(Text.of(description), TextStyle.of(14), box);
 * }</pre>
 *
 * @param width the line width limit, {@link Float#POSITIVE_INFINITY} for none
 * @param wrap how lines break
 * @param maxLines most lines shown, 0 for no limit
 * @param ellipsis whether cut text ends with "…"
 * @param align horizontal alignment of lines within the width (only the horizontal part is used)
 */
public record TextBox(float width, TextWrap wrap, int maxLines, boolean ellipsis, TextAlign align) {

    /** No width limit, left aligned. */
    public static final TextBox NONE =
            new TextBox(Float.POSITIVE_INFINITY, TextWrap.WORDS, 0, false, TextAlign.TOP_LEFT);

    /**
     * Validates the values.
     *
     * @param width the width
     * @param wrap the wrapping
     * @param maxLines the line limit
     * @param ellipsis whether to add an ellipsis
     * @param align the alignment
     */
    public TextBox {
        if (!(width > 0f)) {
            throw new IllegalArgumentException("Text box width must be positive: " + width);
        }
        if (maxLines < 0) {
            throw new IllegalArgumentException("Line limit must not be negative: " + maxLines);
        }
    }

    /**
     * Returns a box of a given width that wraps by words.
     *
     * @param width the width
     * @return the box
     */
    public static TextBox width(float width) {
        return new TextBox(width, TextWrap.WORDS, 0, false, TextAlign.TOP_LEFT);
    }

    /**
     * Returns a copy with another wrapping.
     *
     * @param newWrap the wrapping
     * @return the box
     */
    public TextBox wrap(TextWrap newWrap) {
        return new TextBox(width, newWrap, maxLines, ellipsis, align);
    }

    /**
     * Returns a copy with a line limit.
     *
     * @param lines the limit, 0 for none
     * @return the box
     */
    public TextBox maxLines(int lines) {
        return new TextBox(width, wrap, lines, ellipsis, align);
    }

    /**
     * Returns a copy that ends cut text with "…".
     *
     * @param on whether to add the ellipsis
     * @return the box
     */
    public TextBox ellipsis(boolean on) {
        return new TextBox(width, wrap, maxLines, on, align);
    }

    /**
     * Returns a copy with another line alignment.
     *
     * @param newAlign the alignment
     * @return the box
     */
    public TextBox align(TextAlign newAlign) {
        return new TextBox(width, wrap, maxLines, ellipsis, newAlign);
    }
}
