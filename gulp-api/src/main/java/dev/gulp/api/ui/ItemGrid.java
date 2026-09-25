package dev.gulp.api.ui;

import dev.gulp.api.event.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Slots of an inventory: a grid over a {@link ListState} whose {@code null} elements are empty slots. Items are dragged
 * between slots (also between grids) with the mouse or touch, or picked up with accept and dropped with accept on the
 * gamepad; the two slots swap. Created by {@link Ui#itemGrid}. Theme type: {@code slot} (a slot under a dragged item
 * that accepts it is {@link WidgetState#CHECKED}).
 *
 * <pre>{@code
 * ListState<ItemStack> backpack = ListState.of(Collections.nCopies(20, null));
 * itemGrid(5, backpack, stack -> stack == null ? spacer() : image(stack.icon()))
 *         .onChange(slots -> save(slots));
 * }</pre>
 *
 * @param <T> the item type; {@code null} marks an empty slot
 */
public final class ItemGrid<T extends @Nullable Object> extends Node<ItemGrid<T>> {

    /**
     * What a dragged slot carries.
     *
     * @param grid the grid the item comes from
     * @param index the slot
     */
    public record SlotRef(ItemGrid<?> grid, int index) {}

    private final ListState<T> slots;
    private final Function<? super T, ? extends Node<?>> render;
    private final Grid grid;
    private final List<Slot> nodes = new ArrayList<>();
    private @Nullable Subscription changes;

    /**
     * Creates an item grid.
     *
     * @param columns slots per row
     * @param slots the slot contents, {@code null} for empty
     * @param render makes the node of an item (not called for empty slots)
     */
    public ItemGrid(int columns, ListState<T> slots, Function<? super T, ? extends Node<?>> render) {
        this.slots = slots;
        this.render = render;
        this.grid = new Grid(columns).gap(4f);
        addChild(grid);
        rebuild();
    }

    @Override
    protected String styleType() {
        return "item_grid";
    }

    @Override
    protected boolean usesPadding() {
        return false;
    }

    /**
     * Returns the slots.
     *
     * @return the observable contents
     */
    public ListState<T> slots() {
        return slots;
    }

    /**
     * Runs an action after items moved.
     *
     * @param action receives the slot contents
     * @return this grid
     */
    @SuppressWarnings("unchecked")
    public ItemGrid<T> onChange(Consumer<List<T>> action) {
        return on(NodeEvent.Change.class, e -> action.accept((List<T>) e.value()));
    }

    /**
     * Returns the node of a slot, for focus or tests.
     *
     * @param index the slot
     * @return the slot node
     */
    public Node<?> slot(int index) {
        return nodes.get(index);
    }

    @Override
    protected void mounted() {
        changes = slots.subscribeChanges(change -> {
            if (change.kind() == ListState.Kind.SET && change.index() < nodes.size()) {
                nodes.get(change.index()).refill();
            } else {
                rebuild();
            }
        });
        rebuild();
    }

    @Override
    protected void unmounted() {
        Subscription current = changes;
        if (current != null) {
            current.cancel();
            changes = null;
        }
    }

    private void rebuild() {
        grid.clear();
        nodes.clear();
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = new Slot(this, i);
            nodes.add(slot);
            grid.add(slot);
        }
    }

    @SuppressWarnings("unchecked")
    private void move(SlotRef from, int to) {
        if (from.grid() == this) {
            if (from.index() != to) {
                slots.swap(from.index(), to);
            }
        } else {
            ItemGrid<T> other = (ItemGrid<T>) from.grid();
            T incoming = other.slots.get(from.index());
            T outgoing = slots.get(to);
            other.slots.set(from.index(), outgoing);
            slots.set(to, incoming);
            other.fireChange(other.slots.get());
        }
        fireChange(slots.get());
    }

    @Override
    protected Size measure() {
        return new Size(grid.minWidth(), grid.minHeight());
    }

    @Override
    protected void arrange() {
        place(grid, x, y, grid.minWidth(), grid.minHeight());
    }

    /** One slot. */
    private static final class Slot extends Container<Slot> {
        private final ItemGrid<?> owner;
        private final int index;

        Slot(ItemGrid<?> owner, int index) {
            this.owner = owner;
            this.index = index;
            draggable(new SlotRef(owner, index));
            dropTarget(payload -> payload instanceof SlotRef, payload -> drop((SlotRef) payload));
            refill();
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void drop(SlotRef from) {
            ((ItemGrid) owner).move(from, index);
        }

        @SuppressWarnings("unchecked")
        void refill() {
            clearChildren();
            Object item = owner.slots.get(index);
            if (item != null) {
                addChild(((Function<Object, Node<?>>) owner.render).apply(item));
            }
        }

        @Override
        protected String styleType() {
            return "slot";
        }

        @Override
        protected boolean usesPadding() {
            return true;
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
                        width - pad.horizontal(),
                        height - pad.vertical());
            }
        }
    }
}
