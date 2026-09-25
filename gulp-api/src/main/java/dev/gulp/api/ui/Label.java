package dev.gulp.api.ui;

import dev.gulp.api.render.Draw;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextAlign;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import dev.gulp.api.text.TextWrap;
import org.jspecify.annotations.Nullable;

/**
 * Text in the theme's font. It can follow an {@link Observable} and refresh by itself, wrap to its width, and limit
 * the number of lines with an ellipsis. Created by {@link Ui#label}. Theme type: {@code label}; variants {@code
 * title}, {@code heading}, {@code caption}, {@code danger}, {@code success}.
 *
 * <pre>{@code
 * label(tr("menu.title")).variant("title");
 * label(coins.map(c -> tr("hud.coins", c)));
 * label(longDescription).wrap(true).maxLines(3).width(300);
 * }</pre>
 */
@SuppressWarnings("this-escape")
public class Label extends Node<Label> {

    private Text text = Text.empty();
    private boolean wrap;
    private int maxLines;
    private TextAlign align = TextAlign.LEFT;
    private @Nullable TextLayout layout;
    private @Nullable Style layoutStyle;
    private float layoutWidth = -1f;
    private float measuredFor = -1f;

    /**
     * Creates a label.
     *
     * @param content the text
     */
    public Label(String content) {
        this.text = Text.of(content);
    }

    /**
     * Creates a label with formatted text.
     *
     * @param content the text
     */
    public Label(Text content) {
        this.text = content;
    }

    /**
     * Creates a label that follows a value.
     *
     * @param source the value, shown with {@code String.valueOf}
     */
    public Label(Observable<?> source) {
        bind(source, value -> text(String.valueOf(value)));
    }

    @Override
    protected String styleType() {
        return "label";
    }

    /**
     * Changes the text.
     *
     * @param content the new text
     * @return this label
     */
    public Label text(String content) {
        return text(Text.of(content));
    }

    /**
     * Changes the text to formatted text.
     *
     * @param content the new text
     * @return this label
     */
    public Label text(Text content) {
        if (!content.equals(text)) {
            text = content;
            layout = null;
            invalidate();
        }
        return this;
    }

    /**
     * Returns the text.
     *
     * @return the text
     */
    public Text text() {
        return text;
    }

    /**
     * Wraps the text at the label's width (set by the container, {@link #width} or {@link #maxSize}).
     *
     * @param value whether to wrap
     * @return this label
     */
    public Label wrap(boolean value) {
        this.wrap = value;
        layout = null;
        invalidate();
        return this;
    }

    /**
     * Limits the number of lines; the last one ends with an ellipsis if text is cut.
     *
     * @param lines the limit, 0 for none
     * @return this label
     */
    public Label maxLines(int lines) {
        this.maxLines = Math.max(0, lines);
        layout = null;
        invalidate();
        return this;
    }

    /**
     * Sets where the text sits inside the label.
     *
     * @param value the alignment
     * @return this label
     */
    public Label align(TextAlign value) {
        this.align = value;
        return this;
    }

    /**
     * Returns the laid-out text.
     *
     * @return the layout from the last measure
     */
    protected final TextLayout layout() {
        Style style = style();
        float boxWidth = wrapWidth();
        TextLayout current = layout;
        if (current == null || layoutStyle != style || layoutWidth != boxWidth) {
            TextBox box = boxWidth > 0f
                    ? TextBox.width(boxWidth)
                            .wrap(TextWrap.WORDS)
                            .maxLines(maxLines)
                            .ellipsis(maxLines > 0)
                    : TextBox.NONE.maxLines(maxLines).ellipsis(maxLines > 0);
            current = layoutText(text, style.textStyle(), box);
            layout = current;
            layoutStyle = style;
            layoutWidth = boxWidth;
        }
        return current;
    }

    private float wrapWidth() {
        if (!wrap) {
            return -1f;
        }
        float limit = Float.isNaN(fixedWidth) ? maxWidth() : fixedWidth;
        float inner = limit - padding().horizontal();
        if (!Float.isInfinite(limit)) {
            return Math.max(1f, inner);
        }
        return width > 0f ? Math.max(1f, width - padding().horizontal()) : -1f;
    }

    @Override
    protected Size measure() {
        TextLayout current = layout();
        measuredFor = width;
        return new Size(wrap ? Math.min(current.width(), 40f) : current.width(), current.height());
    }

    @Override
    void afterArrange() {
        if (wrap && Float.isNaN(fixedWidth) && Float.isInfinite(maxWidth()) && Math.abs(width - measuredFor) > 0.5f) {
            invalidate();
        }
    }

    @Override
    protected void draw(Draw draw) {
        drawBackground(draw);
        TextLayout current = layout();
        Insets pad = padding();
        float innerW = width - pad.horizontal();
        float innerH = height - pad.vertical();
        float tx = x + pad.left() + (innerW - current.width()) * align.horizontal();
        float ty = y + pad.top() + (innerH - current.height()) * align.vertical();
        drawText(draw, current, tx, ty);
    }

    /**
     * Draws the laid-out text; subclasses change how (for example revealing characters).
     *
     * @param draw where to draw
     * @param current the layout
     * @param tx left
     * @param ty top
     */
    protected void drawText(Draw draw, TextLayout current, float tx, float ty) {
        draw.text(current, tx, ty);
    }
}
