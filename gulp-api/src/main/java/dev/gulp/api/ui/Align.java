package dev.gulp.api.ui;

/**
 * How a node uses the space its container gives it along one axis: stretch to fill it, or keep its minimum size at the
 * start, centre or end.
 *
 * <pre>{@code
 * button("OK").alignX(Align.CENTER);        // keep the button narrow, centred in the column
 * column(items).align(Align.START);         // every child keeps its width, on the left
 * }</pre>
 */
public enum Align {
    /** Minimum size at the start (left or top). */
    START,
    /** Minimum size in the middle. */
    CENTER,
    /** Minimum size at the end (right or bottom). */
    END,
    /** Stretch over the whole space. */
    FILL;

    /**
     * Returns the fraction of the free space before the node.
     *
     * @return {@code 0} for start and fill, {@code 0.5} for centre, {@code 1} for end
     */
    public float factor() {
        return switch (this) {
            case START, FILL -> 0f;
            case CENTER -> 0.5f;
            case END -> 1f;
        };
    }
}
