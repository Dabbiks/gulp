package dev.gulp.api.ui;

import org.jspecify.annotations.Nullable;

/**
 * Children left to right at their minimum sizes, wrapping to the next line when the width runs out, like words in a
 * paragraph. Created by {@link Ui#flow}. Theme type: {@code flow}.
 *
 * <pre>{@code
 * flow(tags.stream().map(t -> button(t).variant("flat")).toArray(Node[]::new)).gap(4);
 * }</pre>
 */
public final class Flow extends Container<Flow> {

    private @Nullable Float gap;
    private float measuredFor = -1f;

    /**
     * Creates a flow.
     *
     * @param nodes the children
     */
    public Flow(Node<?>... nodes) {
        super(nodes);
    }

    @Override
    protected String styleType() {
        return "flow";
    }

    /**
     * Sets the space between children and between lines.
     *
     * @param value UI points
     * @return this flow
     */
    public Flow gap(float value) {
        this.gap = value;
        invalidate();
        return this;
    }

    private float gapValue() {
        Float own = gap;
        return own != null ? own : style().gap();
    }

    /** Lays the children out for a width; returns the total height (placing them when {@code place} is set). */
    private float run(float available, boolean placing) {
        float spacing = gapValue();
        float cx = 0f;
        float cy = 0f;
        float line = 0f;
        for (int i = 0; i < children.size(); i++) {
            Node<?> child = children.get(i);
            if (!child.isVisible()) {
                continue;
            }
            float cw = child.minWidth();
            if (cx > 0f && cx + cw > available) {
                cx = 0f;
                cy += line + spacing;
                line = 0f;
            }
            if (placing) {
                place(child, x + cx, y + cy, Math.min(cw, available), child.minHeight());
            }
            cx += cw + spacing;
            line = Math.max(line, child.minHeight());
        }
        return cy + line;
    }

    @Override
    protected Size measure() {
        float widest = maxMin(true);
        float available = width > 0f ? Math.max(width, widest) : Float.POSITIVE_INFINITY;
        measuredFor = width;
        return new Size(widest, run(available, false));
    }

    @Override
    protected void arrange() {
        run(width, true);
    }

    @Override
    void afterArrange() {
        if (Math.abs(width - measuredFor) > 0.5f) {
            invalidate();
        }
    }
}
