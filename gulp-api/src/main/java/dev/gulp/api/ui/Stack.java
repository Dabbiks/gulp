package dev.gulp.api.ui;

/**
 * Children on top of each other, later ones above. Each child is placed by its {@link Node#anchor} and {@link
 * Node#offset}; a child without an anchor covers the whole stack. Created by {@link Ui#stack}. Theme type: {@code
 * stack}.
 *
 * <pre>{@code
 * stack(
 *         image(GameAssets.Textures.MAP),
 *         label("You are here").anchor(Anchor.TOP_LEFT).offset(40, 60),
 *         button("Close").anchor(Anchor.TOP_RIGHT).offset(8, 8));
 * }</pre>
 */
public final class Stack extends Container<Stack> {

    /**
     * Creates a stack.
     *
     * @param nodes the children, bottom first
     */
    public Stack(Node<?>... nodes) {
        super(nodes);
    }

    @Override
    protected String styleType() {
        return "stack";
    }

    @Override
    protected Size measure() {
        float w = 0f;
        float h = 0f;
        for (int i = 0; i < children.size(); i++) {
            Node<?> child = children.get(i);
            if (child.isVisible()) {
                w = Math.max(w, child.minWidth() + Math.abs(child.offsetX) * (child.anchor() != null ? 2f : 0f));
                h = Math.max(h, child.minHeight() + Math.abs(child.offsetY) * (child.anchor() != null ? 2f : 0f));
            }
        }
        return new Size(w, h);
    }

    @Override
    protected void arrange() {
        for (int i = 0; i < children.size(); i++) {
            Node<?> child = children.get(i);
            if (child.isVisible()) {
                placeAnchored(child, x, y, width, height);
            }
        }
    }
}
