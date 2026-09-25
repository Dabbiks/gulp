package dev.gulp.api.ui;

import dev.gulp.api.input.MouseButton;
import dev.gulp.api.input.SystemCursor;
import dev.gulp.api.render.Draw;

/**
 * Two panes with a divider that can be dragged, side by side or (after {@link #vertical()}) one above the other. With
 * the keyboard or gamepad, accept on the focused split grabs the divider, directions move it, and accept or cancel
 * lets it go. Created by {@link Ui#split}. Theme type: {@code split} (track colour for the divider).
 *
 * <pre>{@code
 * split(fileTree, editor).ratio(0.3f).grow();
 * }</pre>
 */
public final class Split extends Container<Split> {

    private static final float DIVIDER = 6f;

    private boolean horizontal = true;
    private float ratio = 0.5f;
    private boolean dragging;
    private boolean grabbed;

    /**
     * Creates a split.
     *
     * @param first the left or top pane
     * @param second the right or bottom pane
     */
    public Split(Node<?> first, Node<?> second) {
        super(first, second);
        cursor(SystemCursor.RESIZE_HORIZONTAL);
    }

    @Override
    protected String styleType() {
        return "split";
    }

    @Override
    protected MouseFilter defaultMouseFilter() {
        return MouseFilter.PASS;
    }

    @Override
    protected boolean isFocusableByDefault() {
        return true;
    }

    /**
     * Puts the panes one above the other.
     *
     * @return this split
     */
    public Split vertical() {
        this.horizontal = false;
        cursor(SystemCursor.RESIZE_VERTICAL);
        invalidate();
        return this;
    }

    /**
     * Sets how much of the space the first pane gets.
     *
     * @param value {@code 0..1}
     * @return this split
     */
    public Split ratio(float value) {
        this.ratio = Math.max(0f, Math.min(1f, value));
        invalidate();
        return this;
    }

    /**
     * Returns the share of the first pane.
     *
     * @return {@code 0..1}
     */
    public float ratio() {
        return ratio;
    }

    @Override
    protected boolean isChecked() {
        return grabbed;
    }

    @Override
    protected Size measure() {
        if (horizontal) {
            return new Size(sumMin(true) + DIVIDER, maxMin(false));
        }
        return new Size(maxMin(true), sumMin(false) + DIVIDER);
    }

    private float firstLength() {
        Node<?> a = children.get(0);
        Node<?> b = children.get(1);
        float length = (horizontal ? width : height) - DIVIDER;
        float minA = horizontal ? a.minWidth() : a.minHeight();
        float minB = horizontal ? b.minWidth() : b.minHeight();
        return Math.max(minA, Math.min(length - minB, length * ratio));
    }

    @Override
    protected void arrange() {
        if (children.size() < 2) {
            return;
        }
        float first = firstLength();
        Node<?> a = children.get(0);
        Node<?> b = children.get(1);
        if (horizontal) {
            place(a, x, y, first, height);
            place(b, x + first + DIVIDER, y, width - first - DIVIDER, height);
        } else {
            place(a, x, y, width, first);
            place(b, x, y + first + DIVIDER, width, height - first - DIVIDER);
        }
    }

    @Override
    protected boolean pointerDown(float px, float py, MouseButton button) {
        if (button != MouseButton.LEFT || children.size() < 2) {
            return false;
        }
        float divider = (horizontal ? x : y) + firstLength();
        float at = horizontal ? px : py;
        dragging = at >= divider - 2f && at <= divider + DIVIDER + 2f;
        return dragging;
    }

    @Override
    protected void pointerDrag(float px, float py) {
        if (dragging) {
            float length = (horizontal ? width : height) - DIVIDER;
            float at = (horizontal ? px - x : py - y) - DIVIDER / 2f;
            ratio(length <= 0f ? 0.5f : at / length);
        }
    }

    @Override
    protected void pointerUp(float px, float py, boolean inside) {
        dragging = false;
    }

    @Override
    protected boolean navigate(UiAction action) {
        if (action == UiAction.ACCEPT || (grabbed && action == UiAction.CANCEL)) {
            grabbed = action == UiAction.ACCEPT && !grabbed;
            refreshState();
            return true;
        }
        if (!grabbed) {
            return false;
        }
        boolean decrease = action == (horizontal ? UiAction.LEFT : UiAction.UP);
        boolean increase = action == (horizontal ? UiAction.RIGHT : UiAction.DOWN);
        if (decrease || increase) {
            ratio(ratio + (increase ? 0.05f : -0.05f));
            fireChange(ratio);
            return true;
        }
        return action.direction() != null;
    }

    @Override
    protected void focusChanged(boolean gained) {
        if (!gained && grabbed) {
            grabbed = false;
            refreshState();
        }
    }

    @Override
    protected void drawOver(Draw draw) {
        if (children.size() < 2) {
            return;
        }
        Style style = style();
        float divider = (horizontal ? x : y) + firstLength();
        dev.gulp.api.graphics.Color previous = draw.color();
        draw.color(grabbed ? style.accent() : style.track());
        if (horizontal) {
            draw.rect(divider + DIVIDER / 2f - 1f, y, 2f, height);
        } else {
            draw.rect(x, divider + DIVIDER / 2f - 1f, width, 2f);
        }
        draw.color(previous);
    }
}
