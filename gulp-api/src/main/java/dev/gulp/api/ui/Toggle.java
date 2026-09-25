package dev.gulp.api.ui;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.render.Draw;

/**
 * An on/off switch with a text; the knob slides when it changes. Left and right on the gamepad also switch it. Created
 * by {@link Ui#toggle}. Theme type: {@code checkbox} (accent colour when on, track when off).
 *
 * <pre>{@code
 * toggle("VSync").checked(true).onChange(on -> display().window().setVsync(on));
 * }</pre>
 */
public final class Toggle extends CheckControl<Toggle> {

    private float knob;

    /**
     * Creates a switch.
     *
     * @param label the text
     */
    public Toggle(String label) {
        super(label);
    }

    @Override
    protected boolean navigate(UiAction action) {
        if (action == UiAction.LEFT && isChecked()) {
            userSet(false);
            return true;
        }
        if (action == UiAction.RIGHT && !isChecked()) {
            userSet(true);
            return true;
        }
        return super.navigate(action);
    }

    @Override
    protected void update(float seconds) {
        float target = isChecked() ? 1f : 0f;
        if (knob != target) {
            float step = seconds * 8f;
            knob = knob < target ? Math.min(target, knob + step) : Math.max(target, knob - step);
        }
    }

    @Override
    protected void mounted() {
        knob = isChecked() ? 1f : 0f;
    }

    @Override
    float markWidth(float size) {
        return size * 2f;
    }

    @Override
    void drawMark(Draw draw, float mx, float my, float size) {
        Style style = style();
        float h = size * 1.1f;
        float w = size * 2f;
        Color previous = draw.color();
        draw.color(style.track().lerp(style.accent(), knob)).roundedRect(mx, my, w, h, h / 2f);
        draw.color(Color.WHITE).circle(mx + h / 2f + (w - h) * knob, my + h / 2f, h / 2f - 2f);
        draw.color(previous);
    }
}
