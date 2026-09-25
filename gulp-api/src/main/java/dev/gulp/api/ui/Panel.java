package dev.gulp.api.ui;

/**
 * The theme's panel background with padding around one child. A panel stops the pointer, so clicks on it do not reach
 * the world. Created by {@link Ui#panel}. Theme type: {@code panel}.
 *
 * <pre>{@code
 * panel(column(label("Inventory").variant("heading"), itemGrid)).anchor(Anchor.RIGHT).offset(16, 0);
 * }</pre>
 */
public final class Panel extends Container<Panel> {

    /**
     * Creates a panel.
     *
     * @param child the content
     */
    public Panel(Node<?> child) {
        super(child);
    }

    @Override
    protected String styleType() {
        return "panel";
    }

    @Override
    protected boolean usesPadding() {
        return true;
    }

    @Override
    protected MouseFilter defaultMouseFilter() {
        return MouseFilter.STOP;
    }

    @Override
    protected Size measure() {
        return new Size(maxMin(true), maxMin(false));
    }

    @Override
    protected void arrange() {
        Insets pad = padding();
        for (int i = 0; i < children.size(); i++) {
            Node<?> child = children.get(i);
            if (child.isVisible()) {
                place(
                        child,
                        x + pad.left(),
                        y + pad.top(),
                        Math.max(0f, width - pad.horizontal()),
                        Math.max(0f, height - pad.vertical()));
            }
        }
    }
}
