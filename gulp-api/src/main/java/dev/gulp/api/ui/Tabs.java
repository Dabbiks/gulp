package dev.gulp.api.ui;

import dev.gulp.api.render.Draw;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Pages with a row of tab buttons above them; one page shows at a time and the others keep their state. The shoulder
 * buttons (and Page Up / Page Down) switch tabs from anywhere inside, through {@code ui_next_tab} and {@code
 * ui_prev_tab}. Created by {@link Ui#tabs}. Theme types: {@code tabs}, and {@code tab} for the buttons (the selected
 * one is {@link WidgetState#CHECKED}).
 *
 * <pre>{@code
 * tabs(tab("Graphics", graphicsPage()), tab("Sound", soundPage()), tab("Controls", controlsPage()))
 *         .onChange(i -> prefs.set("settings.tab", (Integer) i));
 * }</pre>
 */
public final class Tabs extends Node<Tabs> {

    /**
     * One page of {@link Tabs}; created by {@link Ui#tab}.
     *
     * @param title the button text
     * @param content the page
     */
    public record Tab(String title, Node<?> content) {}

    private final Row header = new Row();
    private final Stack pages = new Stack();
    private final List<TabButton> buttons = new ArrayList<>();
    private int selected;

    /**
     * Creates tabs.
     *
     * @param tabs the pages, the first one selected
     */
    public Tabs(Tab... tabs) {
        header.gap(2f);
        addChild(header);
        addChild(pages);
        for (Tab tab : tabs) {
            add(tab);
        }
    }

    @Override
    protected String styleType() {
        return "tabs";
    }

    @Override
    protected boolean usesPadding() {
        return false;
    }

    /**
     * Adds a page at the end.
     *
     * @param tab the page
     * @return this node
     */
    public Tabs add(Tab tab) {
        TabButton button = new TabButton(this, buttons.size(), tab.title());
        buttons.add(button);
        header.add(button);
        pages.add(tab.content());
        tab.content().visible(buttons.size() - 1 == selected);
        return this;
    }

    /**
     * Returns the number of pages.
     *
     * @return the count
     */
    public int count() {
        return buttons.size();
    }

    /**
     * Returns the selected page.
     *
     * @return the index
     */
    public int selected() {
        return selected;
    }

    /**
     * Shows a page.
     *
     * @param index the page, clamped to the valid range
     * @return this node
     */
    public Tabs select(int index) {
        if (buttons.isEmpty()) {
            return this;
        }
        int next = Math.max(0, Math.min(buttons.size() - 1, index));
        if (next == selected) {
            return this;
        }
        boolean focusInside = false;
        for (TabButton button : buttons) {
            focusInside |= button.hasFocus();
        }
        selected = next;
        List<Node<?>> contents = pages.children();
        for (int i = 0; i < contents.size(); i++) {
            contents.get(i).visible(i == next);
        }
        for (TabButton button : buttons) {
            button.refreshState();
        }
        if (focusInside) {
            buttons.get(next).requestFocus();
        }
        fireChange(next);
        return this;
    }

    /**
     * Shows the next page, wrapping around.
     *
     * @return this node
     */
    public Tabs next() {
        return buttons.isEmpty() ? this : select((selected + 1) % buttons.size());
    }

    /**
     * Shows the previous page, wrapping around.
     *
     * @return this node
     */
    public Tabs previous() {
        return buttons.isEmpty() ? this : select((selected + buttons.size() - 1) % buttons.size());
    }

    /**
     * Keeps the selected page in a state, both ways.
     *
     * @param state the page index
     * @return this node
     */
    public Tabs bind(State<Integer> state) {
        bind(state, this::select);
        onChange(value -> state.set(value));
        return this;
    }

    /**
     * Runs an action when the user switches pages.
     *
     * @param action receives the new index
     * @return this node
     */
    public Tabs onChange(java.util.function.Consumer<Integer> action) {
        return on(NodeEvent.Change.class, e -> action.accept((Integer) e.value()));
    }

    @Override
    protected Size measure() {
        return new Size(Math.max(header.minWidth(), pages.minWidth()), header.minHeight() + 6f + pages.minHeight());
    }

    @Override
    protected void arrange() {
        place(header, x, y, width, header.minHeight());
        float top = header.minHeight() + 6f;
        place(pages, x, y + top, width, Math.max(0f, height - top));
    }

    @Override
    protected void draw(Draw draw) {
        dev.gulp.api.graphics.Color previous = draw.color();
        draw.color(style().track()).rect(x, y + header.minHeight() + 2f, width, 1f);
        draw.color(previous);
    }

    /** One tab button. */
    private static final class TabButton extends Node<TabButton> {
        private final Tabs owner;
        private final int index;
        private final String title;
        private @Nullable TextLayout text;
        private @Nullable Style textFor;

        TabButton(Tabs owner, int index, String title) {
            this.owner = owner;
            this.index = index;
            this.title = title;
        }

        @Override
        protected String styleType() {
            return "tab";
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
        protected boolean isChecked() {
            return owner.selected == index;
        }

        @Override
        protected void click() {
            super.click();
            owner.select(index);
        }

        private TextLayout text() {
            Style style = style();
            TextLayout layout = text;
            if (layout == null || textFor != style) {
                layout = layoutText(Text.of(title), style.textStyle(), TextBox.NONE);
                text = layout;
                textFor = style;
            }
            return layout;
        }

        @Override
        protected Size measure() {
            TextLayout layout = text();
            return new Size(layout.width(), layout.height());
        }

        @Override
        protected void draw(Draw draw) {
            drawBackground(draw);
            TextLayout layout = text();
            draw.text(layout, x + (width - layout.width()) / 2f, y + (height - layout.height()) / 2f);
        }
    }
}
