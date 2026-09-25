package dev.gulp.api.ui;

import org.jspecify.annotations.Nullable;

/**
 * Children in a fixed number of columns, filled row by row. Each column is as wide as its widest child and each row as
 * tall as its tallest; columns with a child that expands horizontally ({@link Node#expand()} or {@link Node#expandX})
 * share the free width, rows with a child that expands vertically share the free height. Children sit in their cells
 * by their alignment (fill by default). Created by {@link Ui#grid}. Theme type: {@code grid}.
 *
 * <pre>{@code
 * grid(2,
 *         label("Name"), textField().grow(),
 *         label("Class"), dropdown(List.of("Mage", "Rogue"), c -> c).grow())
 *     .hGap(12).vGap(8);
 * }</pre>
 */
public final class Grid extends Container<Grid> {

    private final int columns;
    private @Nullable Float hGap;
    private @Nullable Float vGap;
    private float[] colWidth = new float[0];
    private float[] rowHeight = new float[0];
    private float[] colRatio = new float[0];
    private float[] rowRatio = new float[0];

    /**
     * Creates a grid.
     *
     * @param columns number of columns
     * @param nodes the children
     * @throws IllegalArgumentException if {@code columns < 1}
     */
    public Grid(int columns, Node<?>... nodes) {
        super(nodes);
        if (columns < 1) {
            throw new IllegalArgumentException("A grid needs at least one column");
        }
        this.columns = columns;
    }

    @Override
    protected String styleType() {
        return "grid";
    }

    /**
     * Sets the space between columns.
     *
     * @param value UI points
     * @return this grid
     */
    public Grid hGap(float value) {
        this.hGap = value;
        invalidate();
        return this;
    }

    /**
     * Sets the space between rows.
     *
     * @param value UI points
     * @return this grid
     */
    public Grid vGap(float value) {
        this.vGap = value;
        invalidate();
        return this;
    }

    /**
     * Sets both gaps.
     *
     * @param value UI points
     * @return this grid
     */
    public Grid gap(float value) {
        return hGap(value).vGap(value);
    }

    /**
     * Returns the number of columns.
     *
     * @return the count
     */
    public int columns() {
        return columns;
    }

    private float hGapValue() {
        Float own = hGap;
        return own != null ? own : style().gap();
    }

    private float vGapValue() {
        Float own = vGap;
        return own != null ? own : style().gap();
    }

    private int rows() {
        return (visibleCount() + columns - 1) / columns;
    }

    private void computeCells() {
        int rows = rows();
        if (colWidth.length < columns) {
            colWidth = new float[columns];
            colRatio = new float[columns];
        }
        if (rowHeight.length < rows) {
            rowHeight = new float[rows];
            rowRatio = new float[rows];
        }
        java.util.Arrays.fill(colWidth, 0f);
        java.util.Arrays.fill(colRatio, 0f);
        java.util.Arrays.fill(rowHeight, 0f);
        java.util.Arrays.fill(rowRatio, 0f);
        int index = 0;
        for (int i = 0; i < children.size(); i++) {
            Node<?> child = children.get(i);
            if (!child.isVisible()) {
                continue;
            }
            int c = index % columns;
            int r = index / columns;
            colWidth[c] = Math.max(colWidth[c], child.minWidth());
            rowHeight[r] = Math.max(rowHeight[r], child.minHeight());
            colRatio[c] = Math.max(colRatio[c], Math.max(child.expandMain, child.expandX));
            rowRatio[r] = Math.max(rowRatio[r], Math.max(child.expandMain, child.expandY));
            index++;
        }
    }

    @Override
    protected Size measure() {
        computeCells();
        int rows = rows();
        float w = hGapValue() * (Math.min(columns, Math.max(1, visibleCount())) - 1);
        for (int c = 0; c < columns; c++) {
            w += colWidth[c];
        }
        float h = vGapValue() * Math.max(0, rows - 1);
        for (int r = 0; r < rows; r++) {
            h += rowHeight[r];
        }
        return new Size(Math.max(0f, w), h);
    }

    @Override
    protected void arrange() {
        computeCells();
        int rows = rows();
        share(colWidth, colRatio, columns, width - measureAxis(colWidth, columns, hGapValue()));
        share(rowHeight, rowRatio, rows, height - measureAxis(rowHeight, rows, vGapValue()));
        int index = 0;
        float cy = y;
        float cx = x;
        for (int i = 0; i < children.size(); i++) {
            Node<?> child = children.get(i);
            if (!child.isVisible()) {
                continue;
            }
            int c = index % columns;
            int r = index / columns;
            if (c == 0) {
                cx = x;
                if (r > 0) {
                    cy += rowHeight[r - 1] + vGapValue();
                }
            }
            float cw = colWidth[c];
            float ch = rowHeight[r];
            place(
                    child,
                    alignIn(child, true, cx, cw, Align.FILL, true),
                    alignIn(child, false, cy, ch, Align.FILL, true),
                    alignIn(child, true, cx, cw, Align.FILL, false),
                    alignIn(child, false, cy, ch, Align.FILL, false));
            cx += cw + hGapValue();
            index++;
        }
    }

    private static float measureAxis(float[] sizes, int count, float gap) {
        float sum = gap * Math.max(0, count - 1);
        for (int i = 0; i < count; i++) {
            sum += sizes[i];
        }
        return sum;
    }

    private static void share(float[] sizes, float[] ratios, int count, float free) {
        if (free <= 0f) {
            return;
        }
        float total = 0f;
        for (int i = 0; i < count; i++) {
            total += ratios[i];
        }
        if (total <= 0f) {
            return;
        }
        for (int i = 0; i < count; i++) {
            sizes[i] += free * ratios[i] / total;
        }
    }
}
