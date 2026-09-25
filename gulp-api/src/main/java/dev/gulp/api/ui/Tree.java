package dev.gulp.api.ui;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.render.Draw;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A tree of {@link TreeItem}s with expandable branches. Up and down move through visible items, right expands or
 * enters a branch, left collapses or goes to the parent, accept selects. Clicking the arrow toggles a branch; clicking
 * the text selects. Created by {@link Ui#tree}. Theme types: {@code list} (the box) and {@code list_item} (the rows).
 *
 * <pre>{@code
 * tree(root, File::name).onSelect(file -> preview(file)).grow();
 * }</pre>
 *
 * @param <T> the value type
 */
public final class Tree<T> extends Node<Tree<T>> {

    private static final float INDENT = 16f;

    private final TreeItem<T> root;
    private final Function<? super T, String> labels;
    private final boolean showRoot;
    private final List<TreeItem<T>> visibleItems = new ArrayList<>();
    private final Column rows = new Column();
    private int cursor;
    private @Nullable TreeItem<T> selectedItem;

    /**
     * Creates a tree showing the root item.
     *
     * @param root the root
     * @param labels the text of each value
     */
    public Tree(TreeItem<T> root, Function<? super T, String> labels) {
        this(root, labels, true);
    }

    /**
     * Creates a tree.
     *
     * @param root the root
     * @param labels the text of each value
     * @param showRoot whether the root is a row or only its children are
     */
    public Tree(TreeItem<T> root, Function<? super T, String> labels, boolean showRoot) {
        this.root = root;
        this.labels = labels;
        this.showRoot = showRoot;
        rows.gap(0f);
        addChild(rows);
        root.attach(this);
        if (!showRoot) {
            root.expanded(true);
        }
        rebuild();
    }

    @Override
    protected String styleType() {
        return "list";
    }

    @Override
    protected boolean isFocusableByDefault() {
        return true;
    }

    @Override
    protected MouseFilter defaultMouseFilter() {
        return MouseFilter.STOP;
    }

    /**
     * Returns the selected item.
     *
     * @return the item, or {@code null}
     */
    public @Nullable TreeItem<T> selected() {
        return selectedItem;
    }

    /**
     * Runs an action when the user selects an item.
     *
     * @param action receives the value
     * @return this tree
     */
    @SuppressWarnings("unchecked")
    public Tree<T> onSelect(Consumer<T> action) {
        return on(NodeEvent.Change.class, e -> action.accept(((TreeItem<T>) e.value()).value()));
    }

    void rebuild() {
        visibleItems.clear();
        if (showRoot) {
            collect(root);
        } else {
            for (TreeItem<T> child : root.children()) {
                collect(child);
            }
        }
        rows.clear();
        for (int i = 0; i < visibleItems.size(); i++) {
            rows.add(new TreeRow(this, i));
        }
        cursor = Math.max(0, Math.min(cursor, visibleItems.size() - 1));
        invalidate();
    }

    private void collect(TreeItem<T> item) {
        visibleItems.add(item);
        if (item.isExpanded()) {
            for (TreeItem<T> child : item.children()) {
                collect(child);
            }
        }
    }

    /**
     * Returns the items currently shown, top to bottom.
     *
     * @return the rows' items
     */
    public List<TreeItem<T>> visibleItems() {
        return List.copyOf(visibleItems);
    }

    private void select(int index) {
        if (index < 0 || index >= visibleItems.size()) {
            return;
        }
        cursor = index;
        TreeItem<T> item = visibleItems.get(index);
        if (selectedItem != item) {
            selectedItem = item;
            fireChange(item);
        }
        restyleRows();
    }

    private void restyleRows() {
        for (Node<?> row : rows.children()) {
            row.refreshState();
        }
    }

    @Override
    protected boolean navigate(UiAction action) {
        if (visibleItems.isEmpty()) {
            return false;
        }
        TreeItem<T> item = visibleItems.get(cursor);
        switch (action) {
            case UP -> {
                if (cursor == 0) {
                    return false;
                }
                cursor--;
            }
            case DOWN -> {
                if (cursor >= visibleItems.size() - 1) {
                    return false;
                }
                cursor++;
            }
            case RIGHT -> {
                if (item.children().isEmpty()) {
                    return true;
                }
                if (!item.isExpanded()) {
                    item.expanded(true);
                } else {
                    cursor++;
                }
            }
            case LEFT -> {
                TreeItem<T> parent = item.parent();
                if (item.isExpanded() && !item.children().isEmpty()) {
                    item.expanded(false);
                } else if (parent != null && visibleItems.contains(parent)) {
                    cursor = visibleItems.indexOf(parent);
                }
            }
            case ACCEPT -> select(cursor);
            default -> {
                return false;
            }
        }
        restyleRows();
        return true;
    }

    @Override
    protected void focusChanged(boolean gained) {
        restyleRows();
    }

    @Override
    protected Size measure() {
        return new Size(rows.minWidth(), rows.minHeight());
    }

    @Override
    protected void arrange() {
        Insets pad = padding();
        place(rows, x + pad.left(), y + pad.top(), width - pad.horizontal(), height - pad.vertical());
    }

    /** One visible item. */
    private static final class TreeRow extends Node<TreeRow> {
        private final Tree<?> tree;
        private final int index;
        private @Nullable TextLayout layout;
        private @Nullable Style layoutStyle;

        TreeRow(Tree<?> tree, int index) {
            this.tree = tree;
            this.index = index;
        }

        @Override
        protected String styleType() {
            return "list_item";
        }

        @Override
        protected MouseFilter defaultMouseFilter() {
            return MouseFilter.STOP;
        }

        private TreeItem<?> item() {
            return tree.visibleItems.get(index);
        }

        @Override
        protected WidgetState state() {
            if (tree.selectedItem == item()) {
                return WidgetState.CHECKED;
            }
            if (tree.cursor == index && tree.focused) {
                return WidgetState.FOCUSED;
            }
            return super.state();
        }

        private float indent() {
            return (item().depth() - (tree.showRoot ? 0 : 1)) * INDENT;
        }

        @SuppressWarnings("unchecked")
        private TextLayout layout() {
            Style style = style();
            TextLayout current = layout;
            if (current == null || layoutStyle != style) {
                String label = ((Function<Object, String>) tree.labels).apply(item().value());
                current = layoutText(Text.of(label), style.textStyle(), TextBox.NONE);
                layout = current;
                layoutStyle = style;
            }
            return current;
        }

        @Override
        protected Size measure() {
            TextLayout current = layout();
            return new Size(indent() + 16f + current.width(), current.height());
        }

        @Override
        protected boolean pointerDown(float px, float py, MouseButton button) {
            if (button != MouseButton.LEFT) {
                return false;
            }
            tree.requestFocus();
            float arrowEnd = x + padding().left() + indent() + 16f;
            TreeItem<?> item = item();
            if (px < arrowEnd && !item.children().isEmpty()) {
                item.expanded(!item.isExpanded());
            } else {
                tree.select(index);
            }
            return true;
        }

        @Override
        protected void draw(Draw draw) {
            drawBackground(draw);
            Style style = style();
            Insets pad = style.padding();
            TreeItem<?> item = item();
            float left = x + pad.left() + indent();
            float cy = y + height / 2f;
            Color previous = draw.color();
            if (!item.children().isEmpty()) {
                draw.color(style.mutedColor());
                if (item.isExpanded()) {
                    draw.triangle(left + 2f, cy - 2f, left + 10f, cy - 2f, left + 6f, cy + 3f);
                } else {
                    draw.triangle(left + 4f, cy - 4f, left + 9f, cy, left + 4f, cy + 4f);
                }
                draw.color(previous);
            }
            TextLayout current = layout();
            draw.text(current, left + 16f, y + (height - current.height()) / 2f);
        }
    }
}
