package dev.gulp.api.ui;

/**
 * Something that happened to one node. Node events go only to handlers on that node ({@link Node#on}), like signals,
 * and never through the game event bus.
 *
 * <pre>{@code
 * button("Save").on(NodeEvent.Click.class, e -> save());
 * slider(0, 1).on(NodeEvent.Change.class, e -> preview((Float) e.value()));
 * }</pre>
 */
public abstract sealed class NodeEvent {

    private final Node<?> node;

    NodeEvent(Node<?> node) {
        this.node = node;
    }

    /**
     * Returns the node.
     *
     * @return the node the event happened to
     */
    public final Node<?> node() {
        return node;
    }

    /** The node was clicked, tapped or accepted with the keyboard or gamepad. */
    public static final class Click extends NodeEvent {
        Click(Node<?> node) {
            super(node);
        }
    }

    /** The pointer entered or left the node. */
    public static final class Hover extends NodeEvent {
        private final boolean entered;

        Hover(Node<?> node, boolean entered) {
            super(node);
            this.entered = entered;
        }

        /**
         * Returns whether the pointer entered.
         *
         * @return {@code true} on enter, {@code false} on leave
         */
        public boolean entered() {
            return entered;
        }
    }

    /** The node gained or lost the focus. */
    public static final class Focus extends NodeEvent {
        private final boolean gained;

        Focus(Node<?> node, boolean gained) {
            super(node);
            this.gained = gained;
        }

        /**
         * Returns whether the focus was gained.
         *
         * @return {@code true} when gained, {@code false} when lost
         */
        public boolean gained() {
            return gained;
        }
    }

    /** The value of a widget changed through the user (slider, checkbox, text field, dropdown and so on). */
    public static final class Change extends NodeEvent {
        private final Object value;

        Change(Node<?> node, Object value) {
            super(node);
            this.value = value;
        }

        /**
         * Returns the new value.
         *
         * @return the value, of the widget's value type
         */
        public Object value() {
            return value;
        }
    }

    /** Text was submitted with Enter in a text field. */
    public static final class Submit extends NodeEvent {
        private final String text;

        Submit(Node<?> node, String text) {
            super(node);
            this.text = text;
        }

        /**
         * Returns the submitted text.
         *
         * @return the text
         */
        public String text() {
            return text;
        }
    }
}
