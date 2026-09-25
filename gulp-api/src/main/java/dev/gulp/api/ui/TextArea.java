package dev.gulp.api.ui;

/**
 * Several lines of editable text; Enter adds a line and Control+Enter submits. It scrolls to keep the caret visible.
 * See {@link EditableText} for editing. Created by {@link Ui#textArea}. Theme type: {@code field}.
 *
 * <pre>{@code
 * textArea().rows(6).placeholder("Notes").bind(notes).grow();
 * }</pre>
 */
public final class TextArea extends EditableText<TextArea> {

    private int rows = 4;

    /** Creates an empty area four lines tall. */
    public TextArea() {
        super(true);
    }

    /**
     * Sets the height in lines.
     *
     * @param value the number of lines
     * @return this area
     */
    public TextArea rows(int value) {
        this.rows = Math.max(1, value);
        invalidate();
        return this;
    }

    @Override
    int rows() {
        return rows;
    }
}
