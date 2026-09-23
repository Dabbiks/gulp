package dev.gulp.api.text;

import org.jspecify.annotations.Nullable;

/**
 * Text measured and broken into lines, ready to draw many times. Create one with {@code graphics().layout(...)} and
 * keep it while the text and style stay the same; {@code Draw.text(String, ...)} caches layouts by itself.
 *
 * <pre>{@code
 * TextLayout layout = graphics().layout(Text.of(story), TextStyle.of(16), TextBox.width(300));
 * draw.text(layout, 20, 40, revealed); // typewriter: only the first 'revealed' characters
 * }</pre>
 */
public interface TextLayout {

    /**
     * Returns the laid out text.
     *
     * @return the text
     */
    Text text();

    /**
     * Returns the base style.
     *
     * @return the style
     */
    TextStyle style();

    /**
     * Returns the box it was laid out in.
     *
     * @return the box
     */
    TextBox box();

    /**
     * Returns the width of the widest line.
     *
     * @return the width in drawing units
     */
    float width();

    /**
     * Returns the height of all lines.
     *
     * @return the height in drawing units
     */
    float height();

    /**
     * Returns the number of lines.
     *
     * @return the line count
     */
    int lineCount();

    /**
     * Returns the number of visible characters and images, the unit of {@code visibleCharacters}.
     *
     * @return the character count
     */
    int characterCount();

    /**
     * Returns whether lines were cut by the line limit.
     *
     * @return {@code true} if text was left out
     */
    boolean isTruncated();

    /**
     * Returns the link under a point.
     *
     * @param x horizontal position relative to the layout's top-left corner
     * @param y vertical position relative to the layout's top-left corner
     * @return the link id, or {@code null}
     */
    @Nullable String linkAt(float x, float y);
}
