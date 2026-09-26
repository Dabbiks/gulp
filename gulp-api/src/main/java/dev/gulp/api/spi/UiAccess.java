package dev.gulp.api.spi;

import dev.gulp.api.audio.Sound;
import dev.gulp.api.entity.component.WorldUi;
import dev.gulp.api.input.Input;
import dev.gulp.api.input.KeyboardKey;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.math.Rect;
import dev.gulp.api.render.Draw;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import dev.gulp.api.text.TextStyle;
import dev.gulp.api.ui.Direction;
import dev.gulp.api.ui.Menu;
import dev.gulp.api.ui.Node;
import dev.gulp.api.ui.Screen;
import dev.gulp.api.ui.Theme;
import dev.gulp.api.ui.UiAction;
import org.jspecify.annotations.Nullable;

/**
 * Bridge between UI nodes and the engine: the engine lays out, draws and feeds input to nodes through {@link Hooks}
 * (implemented inside {@link Node}, whose methods for this are protected), and nodes reach engine services through
 * {@link Backend} (implemented by {@code gulp-core}). For {@code gulp-core} only.
 *
 * <pre>{@code
 * UiAccess.hooks().layout(root, 0, 0, width, height);
 * UiAccess.hooks().draw(root, draw, 1f);
 * }</pre>
 */
public final class UiAccess {

    /** Engine-side operations on nodes; implemented inside {@link Node}. */
    public interface Hooks {
        /**
         * Measures and places a root node inside an area, by its anchor or filling it.
         *
         * @param root the root
         * @param x area left
         * @param y area top
         * @param width area width
         * @param height area height
         * @return {@code true} if a node asked for another pass (text wrapping to the new width)
         */
        boolean layout(Node<?> root, float x, float y, float width, float height);

        /**
         * Returns whether the subtree needs layout.
         *
         * @param root the root
         * @return {@code true} if dirty
         */
        boolean isDirty(Node<?> root);

        /**
         * Resolves the styles of a subtree again, after the global theme changed.
         *
         * @param root the root
         */
        void restyle(Node<?> root);

        /**
         * Draws a subtree.
         *
         * @param node the node
         * @param draw where to draw
         * @param alpha inherited opacity
         */
        void draw(Node<?> node, Draw draw, float alpha);

        /**
         * Advances animations of a subtree.
         *
         * @param node the node
         * @param seconds real seconds since the last frame
         */
        void update(Node<?> node, float seconds);

        /**
         * Attaches a subtree to the live UI: bindings subscribe.
         *
         * @param node the node
         */
        void mount(Node<?> node);

        /**
         * Detaches a subtree: bindings unsubscribe, focus and hover end.
         *
         * @param node the node
         */
        void unmount(Node<?> node);

        /**
         * Delivers a pointer press.
         *
         * @param node the node under the pointer
         * @param x UI x
         * @param y UI y
         * @param button the button
         * @return {@code true} if the node captures the pointer until release
         */
        boolean pointerDown(Node<?> node, float x, float y, MouseButton button);

        /**
         * Delivers pointer motion to the capturing node.
         *
         * @param node the node
         * @param x UI x
         * @param y UI y
         */
        void pointerDrag(Node<?> node, float x, float y);

        /**
         * Delivers the release to the capturing node.
         *
         * @param node the node
         * @param x UI x
         * @param y UI y
         * @param inside whether the pointer is still over the node
         */
        void pointerUp(Node<?> node, float x, float y, boolean inside);

        /**
         * Delivers a wheel turn.
         *
         * @param node the node under the pointer
         * @param dx horizontal amount
         * @param dy vertical amount, positive downwards
         * @return {@code true} if consumed
         */
        boolean scroll(Node<?> node, float dx, float dy);

        /**
         * Delivers a UI action to the focused node.
         *
         * @param node the node
         * @param action the action
         * @return {@code true} if consumed (otherwise focus moves or the screen handles it)
         */
        boolean navigate(Node<?> node, UiAction action);

        /**
         * Delivers a key press to the focused node.
         *
         * @param node the node
         * @param key the key
         * @param modifiers modifier bits (1 shift, 2 control, 4 alt, 8 super)
         * @param repeat whether the key repeats
         * @return {@code true} if consumed
         */
        boolean key(Node<?> node, KeyboardKey key, int modifiers, boolean repeat);

        /**
         * Delivers a typed character to the focused node.
         *
         * @param node the node
         * @param codePoint the character
         * @return {@code true} if consumed
         */
        boolean text(Node<?> node, int codePoint);

        /**
         * Tells a node that the pointer entered or left it.
         *
         * @param node the node
         * @param on whether hovered
         */
        void hover(Node<?> node, boolean on);

        /**
         * Tells a node that it gained or lost the focus.
         *
         * @param node the node
         * @param on whether focused
         */
        void focus(Node<?> node, boolean on);

        /**
         * Returns what dragging the node carries.
         *
         * @param node the node
         * @return the payload, or {@code null} if the node is not draggable
         */
        @Nullable Object dragPayload(Node<?> node);

        /**
         * Returns whether the node accepts a dropped payload.
         *
         * @param node the node
         * @param payload the payload
         * @return {@code true} if it is a drop target for it
         */
        boolean acceptsDrop(Node<?> node, Object payload);

        /**
         * Drops a payload on the node.
         *
         * @param node the target
         * @param payload the payload
         */
        void drop(Node<?> node, Object payload);

        /**
         * Returns the context menu of the node.
         *
         * @param node the node
         * @return the menu, or {@code null}
         */
        @Nullable Menu contextMenu(Node<?> node);

        /**
         * Returns the focus neighbour set by hand.
         *
         * @param node the node
         * @param direction the direction
         * @return the neighbour, or {@code null} to use geometry
         */
        @Nullable Node<?> focusNeighbor(Node<?> node, Direction direction);

        /**
         * Returns the tooltip of the node.
         *
         * @param node the node
         * @return a string, a node, or {@code null}
         */
        @Nullable Object tooltip(Node<?> node);
    }

    /** Engine-side screen lifecycle; implemented inside {@link Screen}. */
    public interface ScreenHooks {
        /**
         * Marks a screen open and builds its tree.
         *
         * @param screen the screen
         * @return the root node
         */
        Node<?> open(Screen screen);

        /**
         * Calls {@code onOpen} after the tree is on screen.
         *
         * @param screen the screen
         */
        void opened(Screen screen);

        /**
         * Calls {@code onClose} and marks the screen closed.
         *
         * @param screen the screen
         */
        void close(Screen screen);

        /**
         * Calls {@code onBack}.
         *
         * @param screen the screen
         */
        void back(Screen screen);

        /**
         * Returns the node to focus first.
         *
         * @param screen the screen
         * @return the node, or {@code null}
         */
        @Nullable Node<?> defaultFocus(Screen screen);
    }

    /** Engine services for nodes; implemented by {@code gulp-core}. */
    public interface Backend {
        /**
         * Returns the theme of nodes without their own.
         *
         * @return the global theme
         */
        Theme theme();

        /**
         * Lays out text.
         *
         * @param text the text
         * @param style the style
         * @param box the box
         * @return the layout
         */
        TextLayout layout(Text text, TextStyle style, TextBox box);

        /**
         * Returns a number that changes whenever translated text may read differently, such as after a language
         * change; widgets lay out their text again when it moves.
         *
         * @return the revision
         */
        int textRevision();

        /**
         * Returns real time since the UI started.
         *
         * @return seconds
         */
        float time();

        /**
         * Marks the UI for layout in the next frame.
         */
        void requestLayout();

        /**
         * Moves the focus.
         *
         * @param node the node, or {@code null} to clear the focus
         */
        void focus(@Nullable Node<?> node);

        /**
         * Returns the focused node.
         *
         * @return the node, or {@code null}
         */
        @Nullable Node<?> focused();

        /**
         * Returns whether the focus frame shows (keyboard or gamepad was used last).
         *
         * @return {@code true} if visible
         */
        boolean isFocusVisible();

        /**
         * Opens a popup (dropdown list, menu) above everything, near a point.
         *
         * @param popup the popup root
         * @param owner the node that opened it; focus returns there when the popup closes
         * @param x preferred left, UI points
         * @param y preferred top, UI points
         * @param minWidth minimum width
         */
        void openPopup(Node<?> popup, Node<?> owner, float x, float y, float minWidth);

        /**
         * Closes a popup.
         *
         * @param popup the popup root
         */
        void closePopup(Node<?> popup);

        /**
         * Plays a UI sound.
         *
         * @param sound the sound, or {@code null} for none
         */
        void play(@Nullable Sound sound);

        /**
         * Returns the input service.
         *
         * @return the input
         */
        Input input();

        /**
         * Starts text input (IME, on-screen keyboard) for a field.
         *
         * @param area the field in UI points
         */
        void startTextInput(Rect area);

        /** Ends text input. */
        void stopTextInput();

        /**
         * Adds or removes a node pinned to an entity.
         *
         * @param component the component
         * @param attached whether the entity entered or left its world
         */
        void worldUi(WorldUi component, boolean attached);
    }

    private static @Nullable Hooks hooks;
    private static @Nullable ScreenHooks screenHooks;
    private static @Nullable Backend backend;

    private UiAccess() {}

    /**
     * Installs the hooks; called once by {@link Node}.
     *
     * @param installed the hooks
     */
    public static void install(Hooks installed) {
        if (hooks == null) {
            hooks = installed;
        }
    }

    /**
     * Installs the screen hooks; called once by {@link Screen}.
     *
     * @param installed the hooks
     */
    public static void installScreens(ScreenHooks installed) {
        if (screenHooks == null) {
            screenHooks = installed;
        }
    }

    /**
     * Returns the screen hooks.
     *
     * @return the hooks
     */
    public static ScreenHooks screens() {
        ScreenHooks current = screenHooks;
        if (current == null) {
            Screen.loadHooks();
            current = screenHooks;
            if (current == null) {
                throw new IllegalStateException("Screen hooks are not installed");
            }
        }
        return current;
    }

    /**
     * Installs the engine backend; replaced when an engine starts.
     *
     * @param installed the backend, or {@code null} when the engine stops
     */
    public static void install(@Nullable Backend installed) {
        backend = installed;
    }

    /**
     * Returns the hooks.
     *
     * @return the hooks
     */
    public static Hooks hooks() {
        Hooks current = hooks;
        if (current == null) {
            Node.loadHooks();
            current = hooks;
            if (current == null) {
                throw new IllegalStateException("UI hooks are not installed");
            }
        }
        return current;
    }

    /**
     * Returns the engine backend.
     *
     * @return the backend
     * @throws IllegalStateException if no engine runs
     */
    public static Backend backend() {
        Backend current = backend;
        if (current == null) {
            throw new IllegalStateException("UI needs a running engine");
        }
        return current;
    }

    /**
     * Returns whether an engine backend is installed.
     *
     * @return {@code true} while an engine runs
     */
    public static boolean hasBackend() {
        return backend != null;
    }
}
