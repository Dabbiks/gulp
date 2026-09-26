package dev.gulp.api.ui;

import dev.gulp.api.render.Draw;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Base of {@link Checkbox}, {@link Toggle} and {@link Radio}: a mark and a text, switched by click or accept.
 *
 * <pre>{@code
 * checkbox("Fullscreen").bind(fullscreen).onChange(on -> display().window().setFullscreen(on));
 * }</pre>
 *
 * @param <N> the control's own type
 */
@SuppressWarnings("this-escape")
public abstract class CheckControl<N extends CheckControl<N>> extends Node<N> {

    private final Text text;
    private boolean checked;
    private @Nullable TextLayout layout;
    private @Nullable Style layoutStyle;
    private int layoutRevision;

    CheckControl(String label) {
        this.text = Text.of(label);
    }

    @Override
    protected String styleType() {
        return "checkbox";
    }

    @Override
    protected boolean isFocusableByDefault() {
        return true;
    }

    @Override
    protected MouseFilter defaultMouseFilter() {
        return MouseFilter.STOP;
    }

    @Override
    protected boolean isClickable() {
        return true;
    }

    /**
     * Returns whether the control is on.
     *
     * @return {@code true} if checked
     */
    public boolean isChecked() {
        return checked;
    }

    /**
     * Turns the control on or off without firing a change.
     *
     * @param value whether checked
     * @return this control
     */
    public N checked(boolean value) {
        if (checked != value) {
            checked = value;
            refreshState();
        }
        return self();
    }

    /**
     * Keeps the value in a state, both ways.
     *
     * @param state the value
     * @return this control
     */
    public N bind(State<Boolean> state) {
        bind(state, this::checked);
        onChange(state::set);
        return self();
    }

    /**
     * Runs an action when the user switches the control.
     *
     * @param action receives the new value
     * @return this control
     */
    public N onChange(Consumer<Boolean> action) {
        return on(NodeEvent.Change.class, e -> action.accept((Boolean) e.value()));
    }

    /**
     * Sets the value because the user acted, firing a change.
     *
     * @param value the new value
     */
    final void userSet(boolean value) {
        if (checked != value) {
            checked(value);
            fireChange(value);
        }
    }

    @Override
    protected void click() {
        super.click();
        userSet(!checked);
    }

    /**
     * Returns the size of the mark.
     *
     * @param size the font size
     * @return the mark width
     */
    abstract float markWidth(float size);

    /**
     * Draws the mark.
     *
     * @param draw where to draw
     * @param mx left
     * @param my top
     * @param size the font size
     */
    abstract void drawMark(Draw draw, float mx, float my, float size);

    private @Nullable TextLayout layout() {
        if (text.plain().isEmpty()) {
            return null;
        }
        Style style = style();
        TextLayout current = layout;
        if (current == null || layoutStyle != style || layoutRevision != textRevision()) {
            current = layoutText(text, style.textStyle(), TextBox.NONE);
            layout = current;
            layoutStyle = style;
            layoutRevision = textRevision();
        }
        return current;
    }

    @Override
    protected Size measure() {
        float size = style().fontSize();
        TextLayout current = layout();
        float w = markWidth(size) + (current != null ? 8f + current.width() : 0f);
        float h = Math.max(size * 1.1f, current != null ? current.height() : 0f);
        return new Size(w, h);
    }

    @Override
    protected void draw(Draw draw) {
        drawBackground(draw);
        float size = style().fontSize();
        Insets pad = padding();
        float markHeight = size * 1.1f;
        drawMark(draw, x + pad.left(), y + (height - markHeight) / 2f, size);
        TextLayout current = layout();
        if (current != null) {
            draw.text(current, x + pad.left() + markWidth(size) + 8f, y + (height - current.height()) / 2f);
        }
    }
}
