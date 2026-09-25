package dev.gulp.api.ui;

/**
 * One child at its minimum size in the middle of the available space. Created by {@link Ui#center}. Theme type:
 * {@code center}.
 *
 * <pre>{@code
 * center(column(label("Paused").variant("title"), button("Resume").onClick(ui()::pop)).gap(12));
 * }</pre>
 */
public final class Center extends Container<Center> {

    /**
     * Creates a centring container.
     *
     * @param child the child
     */
    public Center(Node<?> child) {
        super(child);
    }

    @Override
    protected String styleType() {
        return "center";
    }

    @Override
    protected Size measure() {
        return new Size(maxMin(true), maxMin(false));
    }

    @Override
    protected void arrange() {
        for (int i = 0; i < children.size(); i++) {
            Node<?> child = children.get(i);
            if (child.isVisible()) {
                float cw = Math.min(child.minWidth(), width);
                float ch = Math.min(child.minHeight(), height);
                place(child, x + (width - cw) / 2f, y + (height - ch) / 2f, cw, ch);
            }
        }
    }
}
