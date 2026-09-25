package dev.gulp.api.ui;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.render.Draw;

/**
 * Progress from 0 to 1, as a horizontal bar or (after {@link #circular()}) a ring; the fill moves smoothly towards new
 * values. Created by {@link Ui#progressBar}. Theme type: {@code progress} (accent for the fill, track for the rest).
 *
 * <pre>{@code
 * progressBar(Computed.of(() -> hp.get() / (float) maxHp.get())).width(160).variant("danger");
 * progressBar(loading).circular().size(48, 48);
 * }</pre>
 */
public final class ProgressBar extends Node<ProgressBar> {

    private float value;
    private float shown;
    private boolean circular;
    private boolean smooth = true;

    /**
     * Creates an empty bar.
     */
    public ProgressBar() {}

    /**
     * Creates a bar that follows a value.
     *
     * @param source progress from 0 to 1
     */
    public ProgressBar(Observable<Float> source) {
        bind(source, this::value);
    }

    @Override
    protected String styleType() {
        return "progress";
    }

    @Override
    protected boolean usesPadding() {
        return false;
    }

    /**
     * Sets the progress.
     *
     * @param newValue from 0 to 1, clamped
     * @return this bar
     */
    public ProgressBar value(float newValue) {
        this.value = Math.max(0f, Math.min(1f, newValue));
        if (!smooth || !isMounted()) {
            shown = value;
        }
        return this;
    }

    /**
     * Returns the progress.
     *
     * @return from 0 to 1
     */
    public float value() {
        return value;
    }

    /**
     * Draws a ring instead of a bar.
     *
     * @return this bar
     */
    public ProgressBar circular() {
        this.circular = true;
        invalidate();
        return this;
    }

    /**
     * Moves the fill at once instead of smoothly.
     *
     * @param value whether to animate changes
     * @return this bar
     */
    public ProgressBar smooth(boolean value) {
        this.smooth = value;
        return this;
    }

    @Override
    protected Size measure() {
        return circular ? new Size(32f, 32f) : Size.ZERO;
    }

    @Override
    protected void update(float seconds) {
        if (shown != value) {
            float speed = Math.max(0.5f, Math.abs(value - shown) * 8f) * seconds;
            shown = shown < value ? Math.min(value, shown + speed) : Math.max(value, shown - speed);
        }
    }

    @Override
    protected void draw(Draw draw) {
        Style style = style();
        Color previous = draw.color();
        if (circular) {
            float r = Math.min(width, height) / 2f;
            float cx = x + width / 2f;
            float cy = y + height / 2f;
            float thickness = Math.max(3f, r * 0.22f);
            draw.color(style.track()).circleOutline(cx, cy, r - thickness / 2f, thickness);
            if (shown > 0f) {
                draw.color(style.accent());
                float sweep = 360f * shown;
                int pieces = Math.max(1, (int) Math.ceil(sweep / 10f));
                float step = sweep / pieces;
                for (int i = 0; i < pieces; i++) {
                    float a = -90f + i * step;
                    float b = a + step;
                    draw.line(
                            cx + (float) Math.cos(Math.toRadians(a)) * (r - thickness / 2f),
                            cy + (float) Math.sin(Math.toRadians(a)) * (r - thickness / 2f),
                            cx + (float) Math.cos(Math.toRadians(b)) * (r - thickness / 2f),
                            cy + (float) Math.sin(Math.toRadians(b)) * (r - thickness / 2f),
                            thickness);
                }
            }
        } else {
            float radius = Math.min(height / 2f, 6f);
            draw.color(style.track()).roundedRect(x, y, width, height, radius);
            if (shown > 0f) {
                draw.color(style.accent()).roundedRect(x, y, Math.max(height, width * shown), height, radius);
            }
        }
        draw.color(previous);
    }
}
