package dev.gulp.core.world;

import dev.gulp.api.entity.Component;
import java.util.Arrays;

/**
 * Components of one class in a dense array for fast ticking. Removal swaps the last component into the gap; while a
 * tick runs, removals wait until the tick ends so the iteration stays valid.
 */
final class ComponentStore {

    final Class<?> type;
    final int tickOrder;
    final int order;
    Component[] items = new Component[8];
    EntityImpl[] owners = new EntityImpl[8];
    int size;

    ComponentStore(Class<?> type, int tickOrder, int order) {
        this.type = type;
        this.tickOrder = tickOrder;
        this.order = order;
    }

    int add(Component component, EntityImpl owner) {
        if (size == items.length) {
            items = Arrays.copyOf(items, size * 2);
            owners = Arrays.copyOf(owners, size * 2);
        }
        items[size] = component;
        owners[size] = owner;
        return size++;
    }

    /** Removes the entry at a slot and returns the slot index of the entry moved into it, or -1. */
    void remove(int slot) {
        int last = --size;
        if (slot != last) {
            items[slot] = items[last];
            owners[slot] = owners[last];
            owners[slot].setSlot(items[slot], slot);
        }
        items[last] = null;
        owners[last] = null;
    }
}
