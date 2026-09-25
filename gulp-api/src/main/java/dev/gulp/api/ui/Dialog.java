package dev.gulp.api.ui;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A modal dialog screen: a title, content and a row of buttons over a dimmed background. Every button closes the
 * dialog before running its action; cancel closes it without one. Created by {@link Ui#dialog}. Theme type: {@code
 * dialog}.
 *
 * <pre>{@code
 * ui().push(dialog(tr("quit.title"), label(tr("quit.text")).wrap(true))
 *         .button(tr("common.cancel"), null)
 *         .button(tr("quit.confirm"), "danger", () -> engine().stop()));
 * }</pre>
 */
public final class Dialog extends Screen {

    private final String title;
    private final Node<?> content;
    private final List<Button> buttons = new ArrayList<>();
    private float width = 420f;

    /**
     * Creates a dialog.
     *
     * @param title the title
     * @param content the content
     */
    public Dialog(String title, Node<?> content) {
        this.title = title;
        this.content = content;
        dimBackground(true);
        enter(ScreenTransition.scale(0.15f));
    }

    /**
     * Adds a button.
     *
     * @param label the text
     * @param action runs after the dialog closed, or {@code null} to only close
     * @return this dialog
     */
    public Dialog button(String label, @Nullable Runnable action) {
        return button(label, "", action);
    }

    /**
     * Adds a button with a theme variant.
     *
     * @param label the text
     * @param variant the button variant, such as {@code primary} or {@code danger}
     * @param action runs after the dialog closed, or {@code null} to only close
     * @return this dialog
     */
    public Dialog button(String label, String variant, @Nullable Runnable action) {
        Button button = new Button(label).variant(variant).onClick(() -> {
            close();
            if (action != null) {
                action.run();
            }
        });
        buttons.add(button);
        return this;
    }

    /**
     * Sets the dialog width.
     *
     * @param value UI points; 420 by default
     * @return this dialog
     */
    public Dialog width(float value) {
        this.width = value;
        return this;
    }

    @Override
    protected Node<?> build() {
        Row row = new Row().gap(8f).justify(Align.END);
        for (Button button : buttons) {
            row.add(button);
        }
        Column column = new Column(new Label(title).variant("heading"), content).gap(12f);
        if (!buttons.isEmpty()) {
            column.add(row);
        }
        return new Center(new DialogPanel(column).width(width));
    }

    @Override
    protected @Nullable Node<?> defaultFocus() {
        return buttons.isEmpty() ? null : buttons.get(buttons.size() - 1);
    }

    /** The dialog's background. */
    private static final class DialogPanel extends Container<DialogPanel> {
        DialogPanel(Node<?> child) {
            super(child);
        }

        @Override
        protected String styleType() {
            return "dialog";
        }

        @Override
        protected boolean usesPadding() {
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
