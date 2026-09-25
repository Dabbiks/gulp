package dev.gulp.api.ui;

/**
 * Children left to right; see {@link LinearBox}. Created by {@link Ui#row}. Theme type: {@code row} (only its gap is
 * used).
 *
 * <pre>{@code
 * row(image(GameAssets.Sprites.COIN), label(coins.map(String::valueOf))).gap(4).align(Align.CENTER);
 * }</pre>
 */
public final class Row extends LinearBox<Row> {

    /**
     * Creates a row.
     *
     * @param nodes the children
     */
    public Row(Node<?>... nodes) {
        super(true, nodes);
    }
}
