package dev.gulp.api.ui;

import dev.gulp.api.event.Subscription;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.render.Draw;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A scrolling list of rows made from items by a function. It is virtual: only rows in view exist as nodes, so lists of
 * thousands of items stay cheap; all rows are as tall as the first one. With a {@link ListState} it updates only the
 * rows that changed. Rows can be selected one at a time or several ({@link SelectionMode}); up and down move through
 * them, accept selects and activates. Created by {@link Ui#listView}. Theme types: {@code list} and {@code list_item}
 * (selected rows are {@link WidgetState#CHECKED}).
 *
 * <pre>{@code
 * ListState<Quest> quests = ListState.of();
 * listView(quests, q -> row(label(q.name()), spacer(), label(q.progress())))
 *         .selection(ListView.SelectionMode.SINGLE)
 *         .onActivate(q -> ui().push(new QuestScreen(q)))
 *         .grow();
 * }</pre>
 *
 * @param <T> the item type
 */
public final class ListView<T> extends Node<ListView<T>> {

    /** How rows are selected. */
    public enum SelectionMode {
        /** Rows cannot be selected. */
        NONE,
        /** One row at a time. */
        SINGLE,
        /** Any number of rows; accept or click toggles a row. */
        MULTIPLE
    }

    private final ListState<T> items;
    private final Function<? super T, ? extends Node<?>> factory;
    private final Map<Integer, Row> rows = new HashMap<>();
    private final TreeSet<Integer> selected = new TreeSet<>();
    private SelectionMode mode = SelectionMode.SINGLE;
    private int cursor;
    private float rowHeight = -1f;
    private float scroll;
    private @Nullable Subscription changes;
    private boolean dragging;
    private float lastY;
    private float dragDistance;
    private int visibleRows = 6;

    /**
     * Creates a list over an observable list.
     *
     * @param items the items
     * @param factory makes the node of one item
     */
    public ListView(ListState<T> items, Function<? super T, ? extends Node<?>> factory) {
        this.items = items;
        this.factory = factory;
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

    @Override
    public boolean clipsChildren() {
        return true;
    }

    /**
     * Sets how rows are selected.
     *
     * @param value the mode; {@code SINGLE} by default
     * @return this list
     */
    public ListView<T> selection(SelectionMode value) {
        this.mode = value;
        selected.clear();
        return this;
    }

    /**
     * Sets how many rows tall the list is when nothing else decides its height.
     *
     * @param count the rows
     * @return this list
     */
    public ListView<T> visibleRows(int count) {
        this.visibleRows = Math.max(1, count);
        invalidate();
        return this;
    }

    /**
     * Returns the selected items in list order.
     *
     * @return a new list
     */
    public List<T> selectedItems() {
        List<T> result = new ArrayList<>();
        for (int index : selected) {
            if (index < items.size()) {
                result.add(items.get(index));
            }
        }
        return result;
    }

    /**
     * Selects one row, replacing the selection.
     *
     * @param index the row
     * @return this list
     */
    public ListView<T> select(int index) {
        selected.clear();
        if (index >= 0 && index < items.size() && mode != SelectionMode.NONE) {
            selected.add(index);
            cursor = index;
        }
        refreshRows();
        return this;
    }

    /**
     * Runs an action when the selection changes by the user.
     *
     * @param action receives the selected items
     * @return this list
     */
    @SuppressWarnings("unchecked")
    public ListView<T> onSelect(Consumer<List<T>> action) {
        return on(NodeEvent.Change.class, e -> action.accept((List<T>) e.value()));
    }

    /**
     * Runs an action when a row is activated (accept, double click or click in {@code NONE} mode).
     *
     * @param action receives the item
     * @return this list
     */
    @SuppressWarnings("unchecked")
    public ListView<T> onActivate(Consumer<T> action) {
        return on(NodeEvent.Submit.class, e -> {
            if (cursor >= 0 && cursor < items.size()) {
                action.accept(items.get(cursor));
            }
        });
    }

    /**
     * Returns the row the keyboard cursor is on.
     *
     * @return the index
     */
    public int cursorIndex() {
        return cursor;
    }

    @Override
    protected void mounted() {
        changes = items.subscribeChanges(this::changed);
        dropRows();
    }

    @Override
    protected void unmounted() {
        Subscription current = changes;
        if (current != null) {
            current.cancel();
            changes = null;
        }
        dropRows();
    }

    private void changed(ListState.Change<T> change) {
        if (change.kind() == ListState.Kind.SET) {
            Row row = rows.remove(change.index());
            if (row != null) {
                removeChild(row);
            }
        } else {
            dropRows();
            if (change.kind() == ListState.Kind.RESET) {
                selected.clear();
            } else {
                shiftSelection(change.index(), change.kind() == ListState.Kind.ADD ? 1 : -1);
            }
        }
        cursor = Math.max(0, Math.min(cursor, items.size() - 1));
        invalidate();
    }

    private void shiftSelection(int from, int delta) {
        TreeSet<Integer> moved = new TreeSet<>();
        for (int index : selected) {
            if (index < from) {
                moved.add(index);
            } else if (delta > 0 || index > from) {
                moved.add(index + delta);
            }
        }
        selected.clear();
        selected.addAll(moved);
    }

    private void dropRows() {
        for (Row row : rows.values()) {
            removeChild(row);
        }
        rows.clear();
        rowHeight = -1f;
    }

    private void refreshRows() {
        for (Row row : rows.values()) {
            row.refreshState();
        }
    }

    private Row row(int index) {
        Row row = rows.get(index);
        if (row == null) {
            row = new Row(this, index, factory.apply(items.get(index)));
            rows.put(index, row);
            addChild(row);
        }
        return row;
    }

    private float rowHeight() {
        if (rowHeight < 0f && items.size() > 0) {
            Row first = row(0);
            first.measureTree();
            rowHeight = Math.max(1f, first.minHeight());
        }
        return rowHeight < 0f ? 28f : rowHeight;
    }

    @Override
    protected Size measure() {
        float h = rowHeight();
        float w = 0f;
        for (Row row : rows.values()) {
            w = Math.max(w, row.minWidth());
        }
        return new Size(w, h * Math.min(visibleRows, Math.max(1, items.size())));
    }

    private float innerHeight() {
        return height - padding().vertical();
    }

    private float maxScroll() {
        return Math.max(0f, items.size() * rowHeight() - innerHeight());
    }

    private void scrollTo(float value) {
        float clamped = Math.max(0f, Math.min(maxScroll(), value));
        if (clamped != scroll) {
            scroll = clamped;
            dirty = true;
            invalidate();
        }
    }

    @Override
    protected void arrange() {
        float h = rowHeight();
        Insets pad = padding();
        scroll = Math.max(0f, Math.min(maxScroll(), scroll));
        int first = Math.max(0, (int) (scroll / h) - 1);
        int last = Math.min(items.size() - 1, (int) ((scroll + innerHeight()) / h) + 1);
        Iterator<Map.Entry<Integer, Row>> it = rows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Row> entry = it.next();
            if (entry.getKey() < first - 4 || entry.getKey() > last + 4 || entry.getKey() >= items.size()) {
                removeChild(entry.getValue());
                it.remove();
            }
        }
        for (int i = first; i <= last; i++) {
            Row row = row(i);
            row.measureTree();
            place(row, x + pad.left(), y + pad.top() + i * h - scroll, width - pad.horizontal(), h);
        }
        for (Map.Entry<Integer, Row> entry : rows.entrySet()) {
            int index = entry.getKey();
            if (index < first || index > last) {
                place(entry.getValue(), x, y - 10_000f, 0f, 0f);
            }
        }
    }

    private void moveCursor(int to) {
        if (items.size() == 0) {
            return;
        }
        cursor = Math.max(0, Math.min(items.size() - 1, to));
        float h = rowHeight();
        float top = cursor * h;
        if (top < scroll) {
            scrollTo(top);
        } else if (top + h > scroll + innerHeight()) {
            scrollTo(top + h - innerHeight());
        }
        if (mode == SelectionMode.SINGLE) {
            selectByUser(cursor, false);
        }
        refreshRows();
    }

    private void selectByUser(int index, boolean toggle) {
        if (mode == SelectionMode.NONE) {
            return;
        }
        if (mode == SelectionMode.MULTIPLE && toggle) {
            if (!selected.remove(index)) {
                selected.add(index);
            }
        } else {
            if (selected.size() == 1 && selected.contains(index)) {
                return;
            }
            selected.clear();
            selected.add(index);
        }
        refreshRows();
        fireChange(selectedItems());
    }

    @Override
    protected boolean navigate(UiAction action) {
        switch (action) {
            case UP -> {
                if (cursor <= 0) {
                    return false;
                }
                moveCursor(cursor - 1);
                return true;
            }
            case DOWN -> {
                if (cursor >= items.size() - 1) {
                    return false;
                }
                moveCursor(cursor + 1);
                return true;
            }
            case ACCEPT -> {
                if (items.size() > 0) {
                    selectByUser(cursor, true);
                    fireSubmit("");
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    protected boolean scrolled(float dx, float dy) {
        float before = scroll;
        scrollTo(scroll + dy * rowHeight() * 2f);
        return before != scroll;
    }

    @Override
    protected boolean pointerDown(float px, float py, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        dragging = true;
        lastY = py;
        dragDistance = 0f;
        return true;
    }

    @Override
    protected void pointerDrag(float px, float py) {
        if (dragging) {
            dragDistance += Math.abs(py - lastY);
            scrollTo(scroll + lastY - py);
            lastY = py;
        }
    }

    @Override
    protected void pointerUp(float px, float py, boolean inside) {
        dragging = false;
        if (!inside || dragDistance > 6f || items.size() == 0) {
            return;
        }
        int index = (int) ((py - y - padding().top() + scroll) / rowHeight());
        if (index < 0 || index >= items.size()) {
            return;
        }
        requestFocus();
        boolean again = index == cursor && selected.contains(index);
        cursor = index;
        selectByUser(index, true);
        if (again || mode == SelectionMode.NONE) {
            fireSubmit("");
        }
    }

    @Override
    protected void drawChildren(Draw draw, float inherited) {
        for (Row row : rows.values()) {
            if (row.height() > 0f) {
                row.drawTree(draw, inherited);
            }
        }
    }

    /** The wrapper of one item's node. */
    private static final class Row extends Container<Row> {
        private final ListView<?> list;
        private final int index;

        Row(ListView<?> list, int index, Node<?> content) {
            super(content);
            this.list = list;
            this.index = index;
        }

        @Override
        protected String styleType() {
            return "list_item";
        }

        @Override
        protected boolean usesPadding() {
            return true;
        }

        @Override
        protected WidgetState state() {
            if (list.selected.contains(index)) {
                return WidgetState.CHECKED;
            }
            if (list.cursor == index && list.focused) {
                return WidgetState.FOCUSED;
            }
            return super.state();
        }

        @Override
        protected Size measure() {
            return new Size(maxMin(true), maxMin(false));
        }

        @Override
        protected void arrange() {
            Insets pad = padding();
            for (int i = 0; i < children.size(); i++) {
                place(
                        children.get(i),
                        x + pad.left(),
                        y + pad.top(),
                        Math.max(0f, width - pad.horizontal()),
                        Math.max(0f, height - pad.vertical()));
            }
        }
    }

    @Override
    protected void focusChanged(boolean gained) {
        refreshRows();
    }
}
