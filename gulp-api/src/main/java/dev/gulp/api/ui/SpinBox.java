package dev.gulp.api.ui;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.render.Draw;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * A number with minus and plus buttons, changed by clicking them, the wheel, or left and right with the keyboard or
 * gamepad. Whole steps show whole numbers. Created by {@link Ui#spinBox}. Theme type: {@code field}.
 *
 * <pre>{@code
 * spinBox(1, 99, 1).value(10).onChange(n -> stackSize = n.intValue());
 * }</pre>
 */
public final class SpinBox extends Node<SpinBox> {

    private final float min;
    private final float max;
    private final float step;
    private float value;
    private @Nullable TextLayout layout;
    private @Nullable Style layoutStyle;
    private float layoutValue = Float.NaN;

    /**
     * Creates a spin box at the minimum.
     *
     * @param min the lowest value
     * @param max the highest value
     * @param step the change per press
     * @throws IllegalArgumentException if the range or step is invalid
     */
    public SpinBox(float min, float max, float step) {
        if (!(max >= min) || !(step > 0f)) {
            throw new IllegalArgumentException("Invalid spin box range " + min + ".." + max + " step " + step);
        }
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = min;
    }

    @Override
    protected String styleType() {
        return "field";
    }

    @Override
    protected boolean isFocusableByDefault() {
        return true;
    }

    @Override
    protected MouseFilter defaultMouseFilter() {
        return MouseFilter.STOP;
    }

    /**
     * Sets the value without firing a change.
     *
     * @param newValue the value, clamped
     * @return this spin box
     */
    public SpinBox value(float newValue) {
        this.value = Math.max(min, Math.min(max, newValue));
        return this;
    }

    /**
     * Returns the value.
     *
     * @return the value
     */
    public float value() {
        return value;
    }

    /**
     * Keeps the value in a state, both ways.
     *
     * @param state the value
     * @return this spin box
     */
    public SpinBox bind(State<Float> state) {
        bind(state, this::value);
        onChange(state::set);
        return this;
    }

    /**
     * Runs an action when the user changes the value.
     *
     * @param action receives the new value
     * @return this spin box
     */
    public SpinBox onChange(Consumer<Float> action) {
        return on(NodeEvent.Change.class, e -> action.accept((Float) e.value()));
    }

    private void change(int direction) {
        float next = Math.max(min, Math.min(max, value + direction * step));
        if (next != value) {
            value = next;
            fireChange(next);
        }
    }

    private String format() {
        if (step == Math.rint(step) && value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        long hundredths = Math.round(Math.abs(value) * 100.0);
        long fraction = hundredths % 100;
        return (value < 0f ? "-" : "") + hundredths / 100 + (fraction < 10 ? ".0" : ".") + fraction;
    }

    private TextLayout layout() {
        Style style = style();
        TextLayout current = layout;
        if (current == null || layoutStyle != style || layoutValue != value) {
            current = layoutText(Text.of(format()), style.textStyle(), TextBox.NONE);
            layout = current;
            layoutStyle = style;
            layoutValue = value;
        }
        return current;
    }

    @Override
    protected Size measure() {
        float size = style().fontSize();
        return new Size(size * 4f + size * 3f, layout().height());
    }

    @Override
    protected boolean pointerDown(float px, float py, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        float buttonWidth = height;
        if (px < x + buttonWidth) {
            change(-1);
        } else if (px > x + width - buttonWidth) {
            change(1);
        }
        return true;
    }

    @Override
    protected boolean scrolled(float dx, float dy) {
        change(dy > 0f || dx > 0f ? -1 : 1);
        return true;
    }

    @Override
    protected boolean navigate(UiAction action) {
        if (action == UiAction.LEFT) {
            change(-1);
            return true;
        }
        if (action == UiAction.RIGHT) {
            change(1);
            return true;
        }
        return false;
    }

    @Override
    protected void draw(Draw draw) {
        drawBackground(draw);
        Style style = style();
        TextLayout current = layout();
        draw.text(current, x + (width - current.width()) / 2f, y + (height - current.height()) / 2f);
        float cy = y + height / 2f;
        float arm = Math.min(height * 0.18f, 6f);
        Color previous = draw.color();
        draw.color(value > min ? style.textColor() : style.mutedColor());
        draw.rect(x + height / 2f - arm, cy - 1f, arm * 2f, 2f);
        draw.color(value < max ? style.textColor() : style.mutedColor());
        float px = x + width - height / 2f;
        draw.rect(px - arm, cy - 1f, arm * 2f, 2f);
        draw.rect(px - 1f, cy - arm, 2f, arm * 2f);
        draw.color(previous);
    }
}
