package dev.gulp.api.ui;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.render.Draw;

/**
 * One option of a {@link ButtonGroup}: choosing it unchecks the others. Created by {@link Ui#radio}. Theme type:
 * {@code checkbox}.
 *
 * <pre>{@code
 * ButtonGroup<Difficulty> difficulty = new ButtonGroup<>(Difficulty.NORMAL);
 * column(radio("Easy", difficulty, Difficulty.EASY), radio("Normal", difficulty, Difficulty.NORMAL));
 * difficulty.value().subscribe(d -> settings.setDifficulty(d));
 * }</pre>
 *
 * @param <T> the value type of the group
 */
public final class Radio<T> extends CheckControl<Radio<T>> {

    private final ButtonGroup<T> group;
    private final T value;

    /**
     * Creates an option.
     *
     * @param label the text
     * @param group the group
     * @param value the value chosen by this option
     */
    public Radio(String label, ButtonGroup<T> group, T value) {
        super(label);
        this.group = group;
        this.value = value;
        bind(group.value(), current -> checked(value.equals(current)));
    }

    /**
     * Returns the value of this option.
     *
     * @return the value
     */
    public T value() {
        return value;
    }

    @Override
    protected void click() {
        if (!isChecked()) {
            super.click();
            group.value().set(value);
        }
    }

    @Override
    float markWidth(float size) {
        return size * 1.1f;
    }

    @Override
    void drawMark(Draw draw, float mx, float my, float size) {
        Style style = style();
        float r = size * 0.55f;
        Color previous = draw.color();
        draw.color(isChecked() ? style.accent() : style.track()).circle(mx + r, my + r, r);
        if (isChecked()) {
            draw.color(Color.WHITE).circle(mx + r, my + r, r * 0.4f);
        }
        draw.color(previous);
    }
}
