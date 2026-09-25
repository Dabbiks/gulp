package dev.gulp.api.ui;

import dev.gulp.api.input.MouseButton;
import dev.gulp.api.render.Draw;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import org.jspecify.annotations.Nullable;

/**
 * A floating panel with a title bar that can be dragged and a close button. It sits in a {@link Stack}, on the HUD or
 * at the root of a screen, placed by its anchor and offset (top-left by default); dragging moves the offset. With the
 * gamepad, the close button is focusable like any button. Created by {@link Ui#window}. Theme types: {@code window}
 * and {@code window_title}.
 *
 * <pre>{@code
 * ui().hud().add(this, window("Map", minimap).offset(40, 40).onClose(() -> mapOpen.set(false)));
 * }</pre>
 */
public final class Window extends Node<Window> {

    private final TitleBar bar;
    private final Node<?> content;
    private @Nullable Runnable onClose;

    /**
     * Creates a window.
     *
     * @param title the title
     * @param content the content
     */
    public Window(String title, Node<?> content) {
        this.bar = new TitleBar(this, title);
        this.content = content;
        addChild(bar);
        addChild(new Margin(Insets.all(12f), content));
        anchor(Anchor.TOP_LEFT);
    }

    @Override
    protected String styleType() {
        return "window";
    }

    @Override
    protected MouseFilter defaultMouseFilter() {
        return MouseFilter.STOP;
    }

    /**
     * Runs an action when the close button is pressed; the window hides itself first.
     *
     * @param action the action
     * @return this window
     */
    public Window onClose(Runnable action) {
        this.onClose = action;
        return this;
    }

    /**
     * Hides the window and runs the close action.
     */
    public void close() {
        visible(false);
        Runnable action = onClose;
        if (action != null) {
            action.run();
        }
    }

    /**
     * Returns the content.
     *
     * @return the node inside the window
     */
    public Node<?> content() {
        return content;
    }

    @Override
    protected Size measure() {
        Node<?> body = children.get(1);
        return new Size(Math.max(bar.minWidth(), body.minWidth()), bar.minHeight() + body.minHeight());
    }

    @Override
    protected void arrange() {
        Insets pad = padding();
        float left = x + pad.left();
        float top = y + pad.top();
        float inner = width - pad.horizontal();
        place(bar, left, top, inner, bar.minHeight());
        place(children.get(1), left, top + bar.minHeight(), inner, height - pad.vertical() - bar.minHeight());
    }

    /** The title bar: drags the window and holds the close button. */
    private static final class TitleBar extends Node<TitleBar> {
        private final Window window;
        private final String title;
        private final Button close;
        private @Nullable TextLayout layout;
        private @Nullable Style layoutStyle;
        private boolean dragging;
        private float grabX;
        private float grabY;

        TitleBar(Window window, String title) {
            this.window = window;
            this.title = title;
            this.close = new Button("x").variant("flat").onClick(window::close);
            addChild(close);
        }

        @Override
        protected String styleType() {
            return "window_title";
        }

        @Override
        protected MouseFilter defaultMouseFilter() {
            return MouseFilter.STOP;
        }

        private TextLayout layout() {
            Style style = style();
            TextLayout current = layout;
            if (current == null || layoutStyle != style) {
                current = layoutText(Text.of(title), style.textStyle(), TextBox.NONE);
                layout = current;
                layoutStyle = style;
            }
            return current;
        }

        @Override
        protected Size measure() {
            TextLayout current = layout();
            return new Size(current.width() + 12f + close.minWidth(), Math.max(current.height(), close.minHeight()));
        }

        @Override
        protected void arrange() {
            Insets pad = padding();
            float size = close.minWidth();
            place(
                    close,
                    x + width - pad.right() - size,
                    y + (height - close.minHeight()) / 2f,
                    size,
                    close.minHeight());
        }

        @Override
        protected boolean pointerDown(float px, float py, MouseButton button) {
            if (button != MouseButton.LEFT) {
                return false;
            }
            dragging = true;
            grabX = px - window.x;
            grabY = py - window.y;
            return true;
        }

        @Override
        protected void pointerDrag(float px, float py) {
            Node<?> parent = window.parent();
            if (!dragging) {
                return;
            }
            float originX = parent != null ? parent.x : 0f;
            float originY = parent != null ? parent.y : 0f;
            Anchor a = window.anchor();
            if (a == null || a.minX() != 0f || a.minY() != 0f || a.maxX() != 0f || a.maxY() != 0f) {
                window.anchor(Anchor.TOP_LEFT);
            }
            window.offset(Math.max(0f, px - grabX - originX), Math.max(0f, py - grabY - originY));
        }

        @Override
        protected void pointerUp(float px, float py, boolean inside) {
            dragging = false;
        }

        @Override
        protected void draw(Draw draw) {
            drawBackground(draw);
            TextLayout current = layout();
            draw.text(current, x + padding().left(), y + (height - current.height()) / 2f);
        }
    }
}
