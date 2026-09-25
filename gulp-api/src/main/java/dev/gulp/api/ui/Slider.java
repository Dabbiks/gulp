package dev.gulp.api.ui;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.render.Draw;
import java.util.function.Consumer;

/**
 * A value between a minimum and a maximum, changed by dragging the knob, clicking the track, the wheel, or left and
 * right with the keyboard or gamepad. Created by {@link Ui#slider}. Theme type: {@code slider} (accent for the fill
 * and knob, track for the rest).
 *
 * <pre>{@code
 * State<Float> music = State.of(0.8f);
 * slider(0, 1).bind(music).width(200);
 * slider(1, 10).step(1).value(3).onChange(v -> difficulty = v.intValue());
 * }</pre>
 */
public final class Slider extends Node<Slider> {

    private final float min;
    private final float max;
    private float step;
    private float value;
    private boolean dragging;

    /**
     * Creates a slider at the minimum.
     *
     * @param min the lowest value
     * @param max the highest value
     * @throws IllegalArgumentException if {@code max <= min}
     */
    public Slider(float min, float max) {
        if (!(max > min)) {
            throw new IllegalArgumentException("Slider range must be increasing: " + min + ".." + max);
        }
        this.min = min;
        this.max = max;
        this.value = min;
    }

    @Override
    protected String styleType() {
        return "slider";
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
     * Makes the value move in steps.
     *
     * @param size the step, 0 for continuous
     * @return this slider
     */
    public Slider step(float size) {
        this.step = Math.max(0f, size);
        return value(value);
    }

    /**
     * Sets the value without firing a change.
     *
     * @param newValue the value, clamped and snapped to the step
     * @return this slider
     */
    public Slider value(float newValue) {
        this.value = snap(newValue);
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
     * @return this slider
     */
    public Slider bind(State<Float> state) {
        bind(state, this::value);
        onChange(state::set);
        return this;
    }

    /**
     * Runs an action when the user moves the slider.
     *
     * @param action receives the new value
     * @return this slider
     */
    public Slider onChange(Consumer<Float> action) {
        return on(NodeEvent.Change.class, e -> action.accept((Float) e.value()));
    }

    private float snap(float raw) {
        float clamped = Math.max(min, Math.min(max, raw));
        if (step > 0f) {
            clamped = min + Math.round((clamped - min) / step) * step;
            clamped = Math.max(min, Math.min(max, clamped));
        }
        return clamped;
    }

    private void userSet(float raw) {
        float next = snap(raw);
        if (next != value) {
            value = next;
            fireChange(next);
        }
    }

    private float keyStep() {
        return step > 0f ? step : (max - min) / 20f;
    }

    private float fraction() {
        return (value - min) / (max - min);
    }

    @Override
    protected Size measure() {
        return new Size(0f, style().fontSize());
    }

    @Override
    protected boolean pointerDown(float px, float py, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        dragging = true;
        pressed = true;
        refreshState();
        pointerDrag(px, py);
        return true;
    }

    @Override
    protected void pointerDrag(float px, float py) {
        if (dragging) {
            float knob = height / 2f;
            float usable = Math.max(1f, width - 2f * knob);
            userSet(min + (px - x - knob) / usable * (max - min));
        }
    }

    @Override
    protected void pointerUp(float px, float py, boolean inside) {
        dragging = false;
        pressed = false;
        refreshState();
    }

    @Override
    protected boolean scrolled(float dx, float dy) {
        userSet(value - (dy + dx) * keyStep());
        return true;
    }

    @Override
    protected boolean navigate(UiAction action) {
        if (action == UiAction.LEFT) {
            userSet(value - keyStep());
            return true;
        }
        if (action == UiAction.RIGHT) {
            userSet(value + keyStep());
            return true;
        }
        return false;
    }

    @Override
    protected void draw(Draw draw) {
        Style style = style();
        float knob = Math.min(height / 2f, 10f);
        float trackH = Math.max(4f, knob * 0.6f);
        float usable = Math.max(1f, width - 2f * knob);
        float cy = y + height / 2f;
        float kx = x + knob + usable * fraction();
        Color previous = draw.color();
        draw.color(style.track()).roundedRect(x + knob, cy - trackH / 2f, usable, trackH, trackH / 2f);
        draw.color(style.accent()).roundedRect(x + knob, cy - trackH / 2f, kx - x - knob, trackH, trackH / 2f);
        draw.color(hovered || focused || dragging ? style.accent().lerp(Color.WHITE, 0.3f) : style.accent());
        draw.circle(kx, cy, knob);
        draw.color(Color.WHITE).circle(kx, cy, knob * 0.45f);
        draw.color(previous);
    }
}
