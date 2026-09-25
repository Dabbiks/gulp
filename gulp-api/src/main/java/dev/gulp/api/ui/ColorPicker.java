package dev.gulp.api.ui;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.render.Draw;
import java.util.function.Consumer;

/**
 * Picks a colour from a saturation-value square and a hue bar, with a preview below. With the keyboard or gamepad,
 * accept grabs the square (left and right change saturation, up and down brightness), accept again grabs the hue bar
 * (up and down), and a third accept or cancel lets go. Created by {@link Ui#colorPicker}. Theme type: {@code
 * color_picker}.
 *
 * <pre>{@code
 * State<Color> shirt = State.of(Color.hex("#3b82f6"));
 * colorPicker().bind(shirt);
 * }</pre>
 */
public final class ColorPicker extends Node<ColorPicker> {

    private static final Color[] HUES = {
        Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
    };
    private static final float BAR = 18f;
    private static final float PREVIEW = 20f;
    private static final float GAP = 8f;

    private float hue;
    private float saturation = 1f;
    private float value = 1f;
    private float alpha = 1f;
    private Color color = Color.RED;
    private Color pure = Color.RED;
    private float pureHue;
    private int dragging;
    private int grabbed;

    /** Creates a picker showing red. */
    public ColorPicker() {}

    @Override
    protected String styleType() {
        return "color_picker";
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
    protected boolean usesPadding() {
        return false;
    }

    @Override
    protected boolean isChecked() {
        return grabbed != 0;
    }

    /**
     * Shows a colour without firing a change.
     *
     * @param newColor the colour
     * @return this picker
     */
    public ColorPicker color(Color newColor) {
        if (newColor.equals(color)) {
            return this;
        }
        color = newColor;
        float max = Math.max(newColor.r(), Math.max(newColor.g(), newColor.b()));
        float min = Math.min(newColor.r(), Math.min(newColor.g(), newColor.b()));
        float delta = max - min;
        value = max;
        saturation = max <= 0f ? 0f : delta / max;
        if (delta > 0f) {
            float h;
            if (max == newColor.r()) {
                h = (newColor.g() - newColor.b()) / delta;
            } else if (max == newColor.g()) {
                h = 2f + (newColor.b() - newColor.r()) / delta;
            } else {
                h = 4f + (newColor.r() - newColor.g()) / delta;
            }
            hue = ((h * 60f) % 360f + 360f) % 360f;
        }
        alpha = newColor.a();
        return this;
    }

    /**
     * Returns the colour.
     *
     * @return the colour
     */
    public Color color() {
        return color;
    }

    /**
     * Keeps the colour in a state, both ways.
     *
     * @param state the colour
     * @return this picker
     */
    public ColorPicker bind(State<Color> state) {
        bind(state, this::color);
        onChange(state::set);
        return this;
    }

    /**
     * Runs an action when the user changes the colour.
     *
     * @param action receives the colour
     * @return this picker
     */
    public ColorPicker onChange(Consumer<Color> action) {
        return on(NodeEvent.Change.class, e -> action.accept((Color) e.value()));
    }

    private void userSet(float h, float s, float v) {
        hue = ((h % 360f) + 360f) % 360f;
        saturation = Math.max(0f, Math.min(1f, s));
        value = Math.max(0f, Math.min(1f, v));
        Color next = Color.hsv(hue, saturation, value).withAlpha(alpha);
        if (!next.equals(color)) {
            color = next;
            fireChange(next);
        }
    }

    private float squareWidth() {
        return Math.max(1f, width - BAR - GAP);
    }

    private float squareHeight() {
        return Math.max(1f, height - PREVIEW - GAP);
    }

    @Override
    protected boolean pointerDown(float px, float py, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        requestFocus();
        dragging = px < x + squareWidth() + GAP / 2f ? 1 : 2;
        pointerDrag(px, py);
        return true;
    }

    @Override
    protected void pointerDrag(float px, float py) {
        float fy = Math.max(0f, Math.min(1f, (py - y) / squareHeight()));
        if (dragging == 1) {
            userSet(hue, (px - x) / squareWidth(), 1f - fy);
        } else if (dragging == 2) {
            userSet(Math.min(359.9f, fy * 360f), saturation, value);
        }
    }

    @Override
    protected void pointerUp(float px, float py, boolean inside) {
        dragging = 0;
    }

    @Override
    protected boolean navigate(UiAction action) {
        if (action == UiAction.ACCEPT) {
            grabbed = (grabbed + 1) % 3;
            refreshState();
            return true;
        }
        if (grabbed == 0) {
            return false;
        }
        if (action == UiAction.CANCEL) {
            grabbed = 0;
            refreshState();
            return true;
        }
        float step = 0.05f;
        if (grabbed == 1) {
            switch (action) {
                case LEFT -> userSet(hue, saturation - step, value);
                case RIGHT -> userSet(hue, saturation + step, value);
                case UP -> userSet(hue, saturation, value + step);
                case DOWN -> userSet(hue, saturation, value - step);
                default -> {
                    return false;
                }
            }
        } else {
            switch (action) {
                case UP -> userSet(hue - 10f, saturation, value);
                case DOWN -> userSet(hue + 10f, saturation, value);
                case LEFT, RIGHT -> {}
                default -> {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    protected void focusChanged(boolean gained) {
        if (!gained && grabbed != 0) {
            grabbed = 0;
            refreshState();
        }
    }

    @Override
    protected Size measure() {
        return new Size(160f + BAR + GAP, 120f + PREVIEW + GAP);
    }

    @Override
    protected void draw(Draw draw) {
        Style style = style();
        float sw = squareWidth();
        float sh = squareHeight();
        if (pureHue != hue) {
            pure = Color.hsv(hue, 1f, 1f);
            pureHue = hue;
        }
        Color previous = draw.color();
        draw.color(Color.WHITE);
        draw.gradientRect(x, y, sw, sh, Color.WHITE, pure, Color.BLACK, Color.BLACK);
        float bx = x + sw + GAP;
        float segment = sh / 6f;
        for (int i = 0; i < 6; i++) {
            draw.gradientRect(bx, y + i * segment, BAR, segment, HUES[i], HUES[i], HUES[i + 1], HUES[i + 1]);
        }
        float mx = x + saturation * sw;
        float my = y + (1f - value) * sh;
        draw.color(grabbed == 1 ? style.accent() : Color.WHITE).circleOutline(mx, my, 5f, 2f);
        float hy = y + hue / 360f * sh;
        draw.color(grabbed == 2 ? style.accent() : Color.WHITE).rectOutline(bx - 2f, hy - 3f, BAR + 4f, 6f, 2f);
        draw.color(color).roundedRect(x, y + sh + GAP, width, PREVIEW, 4f);
        draw.color(previous);
    }
}
