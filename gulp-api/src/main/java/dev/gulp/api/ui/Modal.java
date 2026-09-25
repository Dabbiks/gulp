package dev.gulp.api.ui;

/**
 * A screen showing one node in the centre over a dimmed background, closed by cancel. Created by {@link Ui#modal}.
 *
 * <pre>{@code
 * ui().push(modal(panel(column(label("Level complete!").variant("title"), button("Next").onClick(this::next)))));
 * }</pre>
 */
public final class Modal extends Screen {

    private final Node<?> content;

    /**
     * Creates a modal screen.
     *
     * @param content the node to show; placed by its anchor, or centred without one
     */
    public Modal(Node<?> content) {
        this.content = content;
        dimBackground(true);
    }

    @Override
    protected Node<?> build() {
        return content.anchor() != null ? new Stack(content) : new Center(content);
    }
}
