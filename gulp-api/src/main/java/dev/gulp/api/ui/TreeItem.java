package dev.gulp.api.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One entry of a {@link Tree}, with child entries that show when it is expanded.
 *
 * <pre>{@code
 * TreeItem<String> root = new TreeItem<>("Assets");
 * TreeItem<String> sprites = root.add(new TreeItem<>("sprites"));
 * sprites.add(new TreeItem<>("player.png"));
 * root.expanded(true);
 * }</pre>
 *
 * @param <T> the value type
 */
public final class TreeItem<T> {

    private final T value;
    private final List<TreeItem<T>> children = new ArrayList<>();
    private @Nullable TreeItem<T> parent;
    private boolean expanded;
    private @Nullable Tree<T> tree;

    /**
     * Creates an item.
     *
     * @param value the value
     */
    public TreeItem(T value) {
        this.value = value;
    }

    /**
     * Returns the value.
     *
     * @return the value
     */
    public T value() {
        return value;
    }

    /**
     * Adds a child.
     *
     * @param child the child
     * @return the child
     */
    public TreeItem<T> add(TreeItem<T> child) {
        child.parent = this;
        children.add(child);
        changed();
        return child;
    }

    /**
     * Removes a child.
     *
     * @param child the child
     * @return {@code true} if it was a child
     */
    public boolean remove(TreeItem<T> child) {
        boolean removed = children.remove(child);
        if (removed) {
            child.parent = null;
            changed();
        }
        return removed;
    }

    /**
     * Returns the children.
     *
     * @return a read-only view
     */
    public List<TreeItem<T>> children() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Returns the parent.
     *
     * @return the parent, or {@code null} for a root
     */
    public @Nullable TreeItem<T> parent() {
        return parent;
    }

    /**
     * Returns whether the children show.
     *
     * @return {@code true} if expanded
     */
    public boolean isExpanded() {
        return expanded;
    }

    /**
     * Shows or hides the children.
     *
     * @param value whether expanded
     * @return this item
     */
    public TreeItem<T> expanded(boolean value) {
        if (expanded != value) {
            expanded = value;
            changed();
        }
        return this;
    }

    /**
     * Returns the depth below the root.
     *
     * @return 0 for a root
     */
    public int depth() {
        int depth = 0;
        for (TreeItem<T> p = parent; p != null; p = p.parent) {
            depth++;
        }
        return depth;
    }

    void attach(@Nullable Tree<T> owner) {
        this.tree = owner;
    }

    private void changed() {
        for (TreeItem<T> item = this; item != null; item = item.parent) {
            Tree<T> owner = item.tree;
            if (owner != null) {
                owner.rebuild();
                return;
            }
        }
    }
}
