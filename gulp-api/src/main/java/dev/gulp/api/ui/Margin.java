package dev.gulp.api.ui;

/**
 * Space around one child, which fills the rest. Created by {@link Ui#margin}. Theme type: {@code margin}.
 *
 * <pre>{@code
 * margin(Insets.all(24), column(label("Credits"), label("Made with Gulp")));
 * }</pre>
 */
public final class Margin extends Container<Margin> {

    private Insets insets;

    /**
     * Creates a margin.
     *
     * @param insets the space
     * @param child the child
     */
    public Margin(Insets insets, Node<?> child) {
        super(child);
        this.insets = insets;
    }

    @Override
    protected String styleType() {
        return "margin";
    }

    /**
     * Changes the space.
     *
     * @param value the insets
     * @return this margin
     */
    public Margin insets(Insets value) {
        this.insets = value;
        invalidate();
        return this;
    }

    @Override
    protected Size measure() {
        return new Size(maxMin(true) + insets.horizontal(), maxMin(false) + insets.vertical());
    }

    @Override
    protected void arrange() {
        for (int i = 0; i < children.size(); i++) {
            Node<?> child = children.get(i);
            if (child.isVisible()) {
                place(
                        child,
                        x + insets.left(),
                        y + insets.top(),
                        Math.max(0f, width - insets.horizontal()),
                        Math.max(0f, height - insets.vertical()));
            }
        }
    }
}
