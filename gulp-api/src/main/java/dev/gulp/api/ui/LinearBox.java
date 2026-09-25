package dev.gulp.api.ui;

import org.jspecify.annotations.Nullable;

/**
 * Children one after another along an axis: {@link Row} (left to right) or {@link Column} (top to bottom). Free space
 * along the axis goes to children that expand, in proportion to their ratios, and is shared again when one of them
 * reaches its maximum size; without expanding children it is left at the side chosen by {@link #justify}. Across the
 * axis children fill the box unless they, or {@link #align}, say otherwise.
 *
 * <pre>{@code
 * row(label("Volume"), spacer(), slider(0, 1).width(160)).gap(8).align(Align.CENTER);
 * column(button("Play"), button("Quit")).gap(12).justify(Align.CENTER);
 * }</pre>
 *
 * @param <C> the box's own type
 */
@SuppressWarnings("this-escape")
public abstract class LinearBox<C extends LinearBox<C>> extends Container<C> {

    private final boolean horizontal;
    private @Nullable Float gap;
    private Align cross = Align.FILL;
    private Align justify = Align.START;
    private float[] sizes = new float[0];
    private boolean[] frozen = new boolean[0];

    LinearBox(boolean horizontal, Node<?>... nodes) {
        super(nodes);
        this.horizontal = horizontal;
    }

    @Override
    protected String styleType() {
        return horizontal ? "row" : "column";
    }

    /**
     * Sets the space between children.
     *
     * @param value UI points; by default the theme's gap
     * @return this box
     */
    public C gap(float value) {
        this.gap = value;
        invalidate();
        return self();
    }

    /**
     * Returns the space between children.
     *
     * @return UI points
     */
    public float gap() {
        Float own = gap;
        return own != null ? own : style().gap();
    }

    /**
     * Sets how children without their own alignment sit across the axis (vertically in a row).
     *
     * @param value fill (the default), start, centre or end
     * @return this box
     */
    public C align(Align value) {
        this.cross = value;
        invalidate();
        return self();
    }

    /**
     * Sets where the children sit along the axis when none of them expands.
     *
     * @param value start (the default), centre or end; fill behaves like start
     * @return this box
     */
    public C justify(Align value) {
        this.justify = value;
        invalidate();
        return self();
    }

    /**
     * Returns whether the box runs left to right.
     *
     * @return {@code true} for a row
     */
    public boolean isHorizontal() {
        return horizontal;
    }

    @Override
    protected Size measure() {
        int count = visibleCount();
        float main = sumMin(horizontal) + gap() * Math.max(0, count - 1);
        float across = maxMin(!horizontal);
        return horizontal ? new Size(main, across) : new Size(across, main);
    }

    private float ratio(Node<?> child) {
        return Math.max(child.expandMain, horizontal ? child.expandX : child.expandY);
    }

    @Override
    protected void arrange() {
        int n = children.size();
        if (sizes.length < n) {
            sizes = new float[n];
            frozen = new boolean[n];
        }
        float start = horizontal ? x : y;
        float length = horizontal ? width : height;
        float crossStart = horizontal ? y : x;
        float crossLength = horizontal ? height : width;
        float spacing = gap();
        int count = visibleCount();
        float free = length - sumMin(horizontal) - spacing * Math.max(0, count - 1);
        float totalRatio = 0f;
        for (int i = 0; i < n; i++) {
            Node<?> child = children.get(i);
            sizes[i] = horizontal ? child.minWidth() : child.minHeight();
            frozen[i] = !child.isVisible() || ratio(child) <= 0f;
            if (!frozen[i]) {
                totalRatio += ratio(child);
            }
        }
        // Share the free space by ratio; a child that hits its maximum keeps it and the rest is shared again.
        for (int pass = 0; pass < n && free > 0.01f && totalRatio > 0f; pass++) {
            float shared = free;
            float nextRatio = 0f;
            boolean clamped = false;
            for (int i = 0; i < n; i++) {
                if (frozen[i]) {
                    continue;
                }
                Node<?> child = children.get(i);
                float max = horizontal ? child.maxWidth() : child.maxHeight();
                float want = sizes[i] + shared * ratio(child) / totalRatio;
                if (want >= max) {
                    free -= max - sizes[i];
                    sizes[i] = max;
                    frozen[i] = true;
                    clamped = true;
                } else {
                    nextRatio += ratio(child);
                }
            }
            if (!clamped) {
                for (int i = 0; i < n; i++) {
                    if (!frozen[i]) {
                        sizes[i] += shared * ratio(children.get(i)) / totalRatio;
                    }
                }
                free = 0f;
            }
            totalRatio = nextRatio;
        }
        float cursor = start + (free > 0f && justify != Align.FILL ? free * justify.factor() : 0f);
        for (int i = 0; i < n; i++) {
            Node<?> child = children.get(i);
            if (!child.isVisible()) {
                continue;
            }
            float slot = sizes[i];
            Align mainFallback = Align.FILL;
            float mainPos = alignIn(child, horizontal, cursor, slot, mainFallback, true);
            float mainSize = alignIn(child, horizontal, cursor, slot, mainFallback, false);
            float crossPos = alignIn(child, !horizontal, crossStart, crossLength, cross, true);
            float crossSize = alignIn(child, !horizontal, crossStart, crossLength, cross, false);
            if (horizontal) {
                place(child, mainPos, crossPos, mainSize, crossSize);
            } else {
                place(child, crossPos, mainPos, crossSize, mainSize);
            }
            cursor += slot + spacing;
        }
    }
}
