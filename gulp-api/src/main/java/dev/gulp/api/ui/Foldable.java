package dev.gulp.api.ui;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.render.Draw;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import org.jspecify.annotations.Nullable;

/**
 * A section with a header that shows or hides its content. Foldables in one {@link FoldGroup} work as an accordion:
 * opening one closes the others. Created by {@link Ui#foldable}. Theme type: {@code foldable} (the header).
 *
 * <pre>{@code
 * FoldGroup faq = new FoldGroup();
 * column(
 *         foldable("How do I save?", label("Saves happen at campfires.").wrap(true)).group(faq),
 *         foldable("Can I pause?", label("Press Escape.")).group(faq).open(true));
 * }</pre>
 */
public final class Foldable extends Node<Foldable> {

    private final Header header;
    private final Node<?> content;
    private boolean open;
    private @Nullable FoldGroup group;

    /**
     * Creates a closed section.
     *
     * @param title the header text
     * @param content the content
     */
    public Foldable(String title, Node<?> content) {
        this.header = new Header(this, title);
        this.content = content;
        addChild(header);
        addChild(content);
        content.visible(false);
    }

    @Override
    protected String styleType() {
        return "foldable_section";
    }

    @Override
    protected boolean usesPadding() {
        return false;
    }

    /**
     * Opens or closes the section.
     *
     * @param value whether open
     * @return this node
     */
    public Foldable open(boolean value) {
        if (open == value) {
            return this;
        }
        open = value;
        content.visible(value);
        FoldGroup current = group;
        if (value && current != null) {
            current.opened(this);
        }
        fireChange(value);
        return this;
    }

    /**
     * Returns whether the section is open.
     *
     * @return {@code true} if the content shows
     */
    public boolean isOpen() {
        return open;
    }

    /**
     * Joins a group in which only one section is open.
     *
     * @param value the group
     * @return this node
     */
    public Foldable group(FoldGroup value) {
        this.group = value;
        value.join(this);
        if (open) {
            value.opened(this);
        }
        return this;
    }

    @Override
    protected Size measure() {
        float w = header.minWidth();
        float h = header.minHeight();
        if (open) {
            w = Math.max(w, content.minWidth());
            h += 4f + content.minHeight();
        }
        return new Size(w, h);
    }

    @Override
    protected void arrange() {
        place(header, x, y, width, header.minHeight());
        if (open) {
            float top = header.minHeight() + 4f;
            place(content, x, y + top, width, Math.max(0f, height - top));
        }
    }

    /** The clickable header. */
    private static final class Header extends Node<Header> {
        private final Foldable owner;
        private final String title;
        private @Nullable TextLayout text;
        private @Nullable Style textFor;

        Header(Foldable owner, String title) {
            this.owner = owner;
            this.title = title;
        }

        @Override
        protected String styleType() {
            return "foldable";
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
            owner.open(!owner.open);
        }

        @Override
        protected boolean navigate(UiAction action) {
            if (action == UiAction.RIGHT && !owner.open) {
                owner.open(true);
                return true;
            }
            if (action == UiAction.LEFT && owner.open) {
                owner.open(false);
                return true;
            }
            return super.navigate(action);
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
            return new Size(layout.width() + 18f, layout.height());
        }

        @Override
        protected void draw(Draw draw) {
            drawBackground(draw);
            Style style = style();
            Insets pad = style.padding();
            TextLayout layout = text();
            float cy = y + height / 2f;
            float ax = x + pad.left() + 5f;
            Color previous = draw.color();
            draw.color(style.textColor());
            if (owner.open) {
                draw.triangle(ax - 4f, cy - 2f, ax + 4f, cy - 2f, ax, cy + 3f);
            } else {
                draw.triangle(ax - 2f, cy - 4f, ax + 3f, cy, ax - 2f, cy + 4f);
            }
            draw.color(previous);
            draw.text(layout, x + pad.left() + 18f, y + (height - layout.height()) / 2f);
        }
    }
}
