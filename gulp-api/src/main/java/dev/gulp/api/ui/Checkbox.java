package dev.gulp.api.ui;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.render.Draw;

/**
 * A box with a tick and a text. Created by {@link Ui#checkbox}. Theme type: {@code checkbox} (accent colour for the
 * tick, track for the box).
 *
 * <pre>{@code
 * State<Boolean> subtitles = State.of(true);
 * checkbox("Subtitles").bind(subtitles);
 * }</pre>
 */
public final class Checkbox extends CheckControl<Checkbox> {

    /**
     * Creates a checkbox.
     *
     * @param label the text
     */
    public Checkbox(String label) {
        super(label);
    }

    @Override
    float markWidth(float size) {
        return size * 1.1f;
    }

    @Override
    void drawMark(Draw draw, float mx, float my, float size) {
        Style style = style();
        float s = size * 1.1f;
        Color previous = draw.color();
        draw.color(isChecked() ? style.accent() : style.track()).roundedRect(mx, my, s, s, Math.min(4f, s / 4f));
        if (isChecked()) {
            draw.color(Color.WHITE);
            draw.line(mx + s * 0.22f, my + s * 0.52f, mx + s * 0.42f, my + s * 0.72f, Math.max(2f, s / 8f));
            draw.line(mx + s * 0.42f, my + s * 0.72f, mx + s * 0.78f, my + s * 0.3f, Math.max(2f, s / 8f));
        }
        draw.color(previous);
    }
}
