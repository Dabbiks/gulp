package dev.gulp.api.ui;

import java.util.List;

/**
 * A context menu: items shown in a popup when the node is right-clicked, long-pressed, or when the west face button
 * (X on Xbox) is pressed on it with the gamepad.
 *
 * <pre>{@code
 * slot.contextMenu(menu(item("Use", () -> use(stack)), item("Split", () -> split(stack)), item("Drop", this::drop)));
 * }</pre>
 *
 * @param items the entries, top to bottom
 */
public record Menu(List<Item> items) {

    /**
     * Copies the items.
     *
     * @param items the entries
     */
    public Menu {
        items = List.copyOf(items);
    }

    /**
     * One entry of a menu.
     *
     * @param label the text
     * @param action runs when chosen
     * @param enabled whether it can be chosen
     */
    public record Item(String label, Runnable action, boolean enabled) {

        /**
         * Returns a disabled copy.
         *
         * @return the item, greyed out
         */
        public Item disabled() {
            return new Item(label, action, false);
        }
    }
}
