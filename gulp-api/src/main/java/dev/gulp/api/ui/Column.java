package dev.gulp.api.ui;

/**
 * Children top to bottom; see {@link LinearBox}. Created by {@link Ui#column}. Theme type: {@code column} (only its gap
 * is used).
 *
 * <pre>{@code
 * column(label(tr("menu.title")).variant("title"), button(tr("menu.play")), button(tr("menu.quit")))
 *         .gap(12).width(240);
 * }</pre>
 */
public final class Column extends LinearBox<Column> {

    /**
     * Creates a column.
     *
     * @param nodes the children
     */
    public Column(Node<?>... nodes) {
        super(false, nodes);
    }
}
