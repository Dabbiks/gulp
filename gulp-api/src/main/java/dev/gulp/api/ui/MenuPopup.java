package dev.gulp.api.ui;

import dev.gulp.api.render.Draw;
import dev.gulp.api.spi.UiAccess;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import org.jspecify.annotations.Nullable;

/**
 * The popup of a {@link Menu}: a column of items that closes when one is chosen, when cancelled, or when the pointer
 * presses outside. Context menus and {@link Dropdown} lists use it; {@link #open} shows one anywhere. Theme types:
 * {@code menu} and {@code menu_item}.
 *
 * <pre>{@code
 * new MenuPopup(menu(item("Copy", this::copy), item("Paste", this::paste))).open(button, button.x(), button.y() + button.height());
 * }</pre>
 */
public final class MenuPopup extends Node<MenuPopup> {

    private final Scroll scroll;

    /**
     * Creates the popup of a menu.
     *
     * @param menu the menu
     */
    public MenuPopup(Menu menu) {
        Column items = new Column().gap(0f);
        for (Menu.Item item : menu.items()) {
            items.add(new Entry(this, item));
        }
        scroll = new Scroll(items);
        addChild(scroll);
    }

    @Override
    protected String styleType() {
        return "menu";
    }

    @Override
    protected MouseFilter defaultMouseFilter() {
        return MouseFilter.STOP;
    }

    /**
     * Shows the popup near a point, focusing its first item.
     *
     * @param owner the node the focus returns to
     * @param px preferred left, UI points
     * @param py preferred top, UI points
     * @return this popup
     */
    public MenuPopup open(Node<?> owner, float px, float py) {
        return open(owner, px, py, 0f, 0);
    }

    MenuPopup open(Node<?> owner, float px, float py, float minWidth, int focusIndex) {
        UiAccess.backend().openPopup(this, owner, px, py, minWidth);
        java.util.List<Entry> entries = findAll(Entry.class);
        if (!entries.isEmpty()) {
            entries.get(Math.max(0, Math.min(entries.size() - 1, focusIndex))).requestFocus();
        }
        return this;
    }

    /** Closes the popup. */
    public void close() {
        if (UiAccess.hasBackend()) {
            UiAccess.backend().closePopup(this);
        }
    }

    @Override
    protected Size measure() {
        return new Size(
                scroll.children().get(0).minWidth(),
                Math.min(scroll.children().get(0).minHeight(), 320f));
    }

    @Override
    protected void arrange() {
        Insets pad = padding();
        place(scroll, x + pad.left(), y + pad.top(), width - pad.horizontal(), height - pad.vertical());
    }

    @Override
    protected boolean navigate(UiAction action) {
        if (action == UiAction.CANCEL) {
            close();
            return true;
        }
        return false;
    }

    /** One item. */
    private static final class Entry extends Node<Entry> {
        private final MenuPopup popup;
        private final Menu.Item item;
        private @Nullable TextLayout layout;
        private @Nullable Style layoutStyle;

        Entry(MenuPopup popup, Menu.Item item) {
            this.popup = popup;
            this.item = item;
            enabled(item.enabled());
        }

        @Override
        protected String styleType() {
            return "menu_item";
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
        protected boolean isClickable() {
            return true;
        }

        @Override
        protected void click() {
            super.click();
            popup.close();
            item.action().run();
        }

        @Override
        protected boolean navigate(UiAction action) {
            if (action == UiAction.CANCEL) {
                popup.close();
                return true;
            }
            return super.navigate(action);
        }

        private TextLayout layout() {
            Style style = style();
            TextLayout current = layout;
            if (current == null || layoutStyle != style) {
                current = layoutText(Text.of(item.label()), style.textStyle(), TextBox.NONE);
                layout = current;
                layoutStyle = style;
            }
            return current;
        }

        @Override
        protected Size measure() {
            TextLayout current = layout();
            return new Size(current.width(), current.height());
        }

        @Override
        protected void draw(Draw draw) {
            drawBackground(draw);
            TextLayout current = layout();
            draw.text(current, x + padding().left(), y + (height - current.height()) / 2f);
        }
    }
}
