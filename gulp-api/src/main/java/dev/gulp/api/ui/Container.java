package dev.gulp.api.ui;

/**
 * A node that holds and places children. Containers draw nothing themselves (except {@link Panel}) and let the pointer
 * through to what is below, so a HUD built from rows and columns does not block clicks on the world.
 *
 * <pre>{@code
 * Column list = column();
 * list.add(label("First"), label("Second"));
 * list.remove(list.children().get(0));
 * }</pre>
 *
 * @param <C> the container's own type
 */
public abstract class Container<C extends Container<C>> extends Node<C> {

    /**
     * Creates a container with children.
     *
     * @param nodes the children
     */
    @SuppressWarnings("this-escape")
    protected Container(Node<?>... nodes) {
        for (Node<?> node : nodes) {
            addChild(node);
        }
    }

    /**
     * Appends children.
     *
     * @param nodes the children, moved from their previous parents
     * @return this container
     */
    public C add(Node<?>... nodes) {
        for (Node<?> node : nodes) {
            addChild(node);
        }
        return self();
    }

    /**
     * Inserts a child.
     *
     * @param index the position
     * @param node the child
     * @return this container
     */
    public C insert(int index, Node<?> node) {
        addChild(index, node);
        return self();
    }

    /**
     * Removes a child.
     *
     * @param node the child
     * @return {@code true} if it was a child
     */
    public boolean remove(Node<?> node) {
        return removeChild(node);
    }

    /**
     * Removes every child.
     *
     * @return this container
     */
    public C clear() {
        clearChildren();
        return self();
    }

    @Override
    protected boolean usesPadding() {
        return false;
    }

    /**
     * Returns the sum of the visible children's minimum sizes along an axis.
     *
     * @param horizontal whether to sum widths
     * @return the sum
     */
    final float sumMin(boolean horizontal) {
        float sum = 0f;
        for (int i = 0; i < children.size(); i++) {
            Node<?> child = children.get(i);
            if (child.isVisible()) {
                sum += horizontal ? child.minWidth() : child.minHeight();
            }
        }
        return sum;
    }

    /**
     * Returns the largest minimum size of the visible children along an axis.
     *
     * @param horizontal whether to compare widths
     * @return the maximum
     */
    final float maxMin(boolean horizontal) {
        float max = 0f;
        for (int i = 0; i < children.size(); i++) {
            Node<?> child = children.get(i);
            if (child.isVisible()) {
                max = Math.max(max, horizontal ? child.minWidth() : child.minHeight());
            }
        }
        return max;
    }

    /**
     * Returns the number of visible children.
     *
     * @return the count
     */
    final int visibleCount() {
        int count = 0;
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).isVisible()) {
                count++;
            }
        }
        return count;
    }
}
