package dev.gulp.api.ui;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.text.Text;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The UI: screens, the HUD, the overlay, notifications, scale, theme and focus ({@code ui()} in games, modules, screens
 * and components), and the factories that build every container and widget ({@code import static
 * dev.gulp.api.ui.Ui.*}).
 *
 * <p>The UI works in points of the base resolution times {@link #scale()}; on a {@code VIEWPORT} display it is drawn
 * at window resolution so text stays sharp, unless {@link #pixelPerfect(boolean)} moves it into the base buffer. It
 * gets input before the game: the in-game console first, then popups, the focused node and the node under the
 * pointer; what it uses is marked {@code isConsumedByUi()} and gameplay actions do not see it. Layers from the bottom:
 * world UI ({@code WorldUi}) and HUD on the {@code ui} render layer; then on the {@code overlay} render layer the game's
 * own drawing, overlay drawers, screens, popups and tooltips, notifications, the inspector and the console, so that
 * menus cover everything.
 *
 * <pre>{@code
 * import static dev.gulp.api.ui.Ui.*;
 *
 * public final class MainMenu extends Screen {
 *     @Override
 *     protected Node<?> build() {
 *         return center(column(
 *                         label(tr("menu.title")).variant("title"),
 *                         button(tr("menu.play")).onClick(() -> worlds().switchTo("level1", Transitions.fade(0.4f))),
 *                         button(tr("menu.settings")).onClick(() -> ui().push(new SettingsScreen())),
 *                         button(tr("menu.quit")).onClick(() -> engine().stop()))
 *                 .gap(12).width(240));
 *     }
 * }
 * }</pre>
 */
public interface Ui {

    // ================================================================== screens

    /**
     * Closes every screen and opens one.
     *
     * @param screen the screen
     */
    void open(Screen screen);

    /**
     * Opens a screen on top of the current one.
     *
     * @param screen the screen
     */
    void push(Screen screen);

    /**
     * Closes the top screen.
     *
     * @return the closed screen, or {@code null} if there was none
     */
    @Nullable Screen pop();

    /**
     * Closes one screen, wherever it is on the stack.
     *
     * @param screen the screen
     */
    void close(Screen screen);

    /**
     * Returns the top screen.
     *
     * @return the screen, or {@code null} if none is open
     */
    @Nullable Screen current();

    /**
     * Returns the open screens.
     *
     * @return bottom first
     */
    List<Screen> screens();

    // ================================================================== layers

    /**
     * Returns the HUD.
     *
     * @return the HUD layer
     */
    Hud hud();

    /**
     * Returns the overlay for immediate drawing.
     *
     * @return the overlay
     */
    Overlay overlay();

    /**
     * Shows a short notification in the corner of the screen.
     *
     * @param message the text
     */
    void toast(String message);

    /**
     * Shows a notification with a theme variant ({@code success}, {@code danger}) for a time.
     *
     * @param message the text
     * @param variant the toast variant, or an empty string
     * @param seconds how long it stays
     */
    void toast(String message, String variant, float seconds);

    // ================================================================== settings

    /**
     * Returns the player's UI scale.
     *
     * @return the factor, 1 by default
     */
    float scale();

    /**
     * Changes the UI scale (saved in preferences).
     *
     * @param scale from 0.75 to 2, clamped
     */
    void setScale(float scale);

    /**
     * Returns whether the UI draws into the base buffer on a {@code VIEWPORT} display.
     *
     * @return {@code false} by default
     */
    boolean isPixelPerfect();

    /**
     * Draws the UI into the base buffer (pixel art) instead of at window resolution.
     *
     * @param value whether to draw pixel-perfect
     */
    void pixelPerfect(boolean value);

    /**
     * Returns the global theme.
     *
     * @return the theme, {@link Theme#DEFAULT} unless changed
     */
    Theme theme();

    /**
     * Changes the global theme.
     *
     * @param theme the theme
     */
    void setTheme(Theme theme);

    /**
     * Returns the focused node.
     *
     * @return the node, or {@code null}
     */
    @Nullable Node<?> focused();

    /**
     * Moves the focus.
     *
     * @param node the node, or {@code null} to clear
     */
    void focus(@Nullable Node<?> node);

    /**
     * Returns the node under the pointer.
     *
     * @return the topmost node that takes the pointer, or {@code null}
     */
    @Nullable Node<?> hovered();

    /**
     * Shows or hides the UI inspector (also F12 in development builds): node frames, sizes, flags and anchors.
     *
     * @param open whether it shows
     */
    void inspector(boolean open);

    /**
     * Returns whether the inspector shows.
     *
     * @return {@code true} if open
     */
    boolean isInspectorOpen();

    /**
     * Opens or closes the in-game developer console (also {@code ~}, when the console is available).
     *
     * @param open whether it shows
     */
    void console(boolean open);

    /**
     * Returns whether the console shows.
     *
     * @return {@code true} if open
     */
    boolean isConsoleOpen();

    // ================================================================== containers

    /**
     * Children top to bottom.
     *
     * @param nodes the children
     * @return the column
     */
    static Column column(Node<?>... nodes) {
        return new Column(nodes);
    }

    /**
     * Children left to right.
     *
     * @param nodes the children
     * @return the row
     */
    static Row row(Node<?>... nodes) {
        return new Row(nodes);
    }

    /**
     * Children in a fixed number of columns.
     *
     * @param columns the number of columns
     * @param nodes the children, row by row
     * @return the grid
     */
    static Grid grid(int columns, Node<?>... nodes) {
        return new Grid(columns, nodes);
    }

    /**
     * Children on top of each other, placed by their anchors.
     *
     * @param nodes the children, bottom first
     * @return the stack
     */
    static Stack stack(Node<?>... nodes) {
        return new Stack(nodes);
    }

    /**
     * A child at its minimum size in the middle.
     *
     * @param child the child
     * @return the container
     */
    static Center center(Node<?> child) {
        return new Center(child);
    }

    /**
     * Space around a child.
     *
     * @param insets the space
     * @param child the child
     * @return the container
     */
    static Margin margin(Insets insets, Node<?> child) {
        return new Margin(insets, child);
    }

    /**
     * The theme's panel background with padding.
     *
     * @param child the content
     * @return the panel
     */
    static Panel panel(Node<?> child) {
        return new Panel(child);
    }

    /**
     * A scrolling view of a larger child.
     *
     * @param child the content
     * @return the scroll container
     */
    static Scroll scroll(Node<?> child) {
        return new Scroll(child);
    }

    /**
     * Two panes with a draggable divider.
     *
     * @param first the left (or top) pane
     * @param second the right (or bottom) pane
     * @return the split
     */
    static Split split(Node<?> first, Node<?> second) {
        return new Split(first, second);
    }

    /**
     * Children wrapping to the next line.
     *
     * @param nodes the children
     * @return the flow
     */
    static Flow flow(Node<?>... nodes) {
        return new Flow(nodes);
    }

    /**
     * Pages with tab buttons.
     *
     * @param tabs the pages
     * @return the tabs
     */
    static Tabs tabs(Tabs.Tab... tabs) {
        return new Tabs(tabs);
    }

    /**
     * One page of {@link #tabs}.
     *
     * @param title the button text
     * @param content the page content; several nodes become a column
     * @return the page
     */
    static Tabs.Tab tab(String title, Node<?>... content) {
        return new Tabs.Tab(title, content.length == 1 ? content[0] : new Column(content));
    }

    /**
     * A section that folds open and closed.
     *
     * @param title the header text
     * @param content the content
     * @return the section
     */
    static Foldable foldable(String title, Node<?> content) {
        return new Foldable(title, content);
    }

    /**
     * A child kept at a width-to-height ratio.
     *
     * @param ratio width divided by height
     * @param child the child
     * @return the container
     */
    static Aspect aspect(float ratio, Node<?> child) {
        return new Aspect(ratio, child);
    }

    /**
     * An empty node that expands.
     *
     * @return the spacer
     */
    static Spacer spacer() {
        return new Spacer();
    }

    // ================================================================== widgets

    /**
     * Text.
     *
     * @param text the text
     * @return the label
     */
    static Label label(String text) {
        return new Label(text);
    }

    /**
     * Formatted text.
     *
     * @param text the text
     * @return the label
     */
    static Label label(Text text) {
        return new Label(text);
    }

    /**
     * Text that follows a value.
     *
     * @param source the value, shown with {@code String.valueOf}
     * @return the label
     */
    static Label label(Observable<?> source) {
        return new Label(source);
    }

    /**
     * Wrapping text from markup, with links and animated effects.
     *
     * @param markup the markup
     * @return the rich text
     */
    static RichText richText(String markup) {
        return new RichText(markup);
    }

    /**
     * An image of a region asset.
     *
     * @param region the region key
     * @return the image
     */
    static Image image(AssetKey<TextureRegion> region) {
        return new Image(region);
    }

    /**
     * An image of a region.
     *
     * @param region the region
     * @return the image
     */
    static Image image(TextureRegion region) {
        return new Image(region);
    }

    /**
     * A text button.
     *
     * @param text the text
     * @return the button
     */
    static Button button(String text) {
        return new Button(text);
    }

    /**
     * A button whose text follows a value.
     *
     * @param text the text
     * @return the button
     */
    static Button button(Observable<String> text) {
        return new Button(text);
    }

    /**
     * A button with an icon and text.
     *
     * @param text the text
     * @param icon the icon region key
     * @return the button
     */
    static Button button(String text, AssetKey<TextureRegion> icon) {
        return new Button(text, icon);
    }

    /**
     * A button with only an icon; give it a tooltip.
     *
     * @param icon the icon region key
     * @return the button
     */
    static Button iconButton(AssetKey<TextureRegion> icon) {
        return new Button("", icon);
    }

    /**
     * A button with only an icon; give it a tooltip.
     *
     * @param icon the icon region
     * @return the button
     */
    static Button iconButton(TextureRegion icon) {
        return new Button("", icon);
    }

    /**
     * A checkbox.
     *
     * @param text the text
     * @return the checkbox
     */
    static Checkbox checkbox(String text) {
        return new Checkbox(text);
    }

    /**
     * An option of a group.
     *
     * @param text the text
     * @param group the group
     * @param value the value this option chooses
     * @param <T> the value type
     * @return the option
     */
    static <T> Radio<T> radio(String text, ButtonGroup<T> group, T value) {
        return new Radio<>(text, group, value);
    }

    /**
     * An on/off switch.
     *
     * @param text the text
     * @return the switch
     */
    static Toggle toggle(String text) {
        return new Toggle(text);
    }

    /**
     * A slider.
     *
     * @param min the lowest value
     * @param max the highest value
     * @return the slider
     */
    static Slider slider(float min, float max) {
        return new Slider(min, max);
    }

    /**
     * A progress bar at a fixed value.
     *
     * @param value from 0 to 1
     * @return the bar
     */
    static ProgressBar progressBar(float value) {
        return new ProgressBar().value(value);
    }

    /**
     * A progress bar that follows a value.
     *
     * @param value from 0 to 1
     * @return the bar
     */
    static ProgressBar progressBar(Observable<Float> value) {
        return new ProgressBar(value);
    }

    /**
     * A number with minus and plus buttons.
     *
     * @param min the lowest value
     * @param max the highest value
     * @param step the change per press
     * @return the spin box
     */
    static SpinBox spinBox(float min, float max, float step) {
        return new SpinBox(min, max, step);
    }

    /**
     * One line of editable text.
     *
     * @return the field
     */
    static TextField textField() {
        return new TextField();
    }

    /**
     * Several lines of editable text.
     *
     * @return the area
     */
    static TextArea textArea() {
        return new TextArea();
    }

    /**
     * A choice from a list in a popup.
     *
     * @param options the options
     * @param labels the text of each option
     * @param <T> the option type
     * @return the dropdown
     */
    static <T> Dropdown<T> dropdown(List<? extends T> options, Function<? super T, String> labels) {
        return new Dropdown<>(options, labels);
    }

    /**
     * A virtual list of rows.
     *
     * @param items the items
     * @param rows makes the node of one item
     * @param <T> the item type
     * @return the list
     */
    static <T> ListView<T> listView(ListState<T> items, Function<? super T, ? extends Node<?>> rows) {
        return new ListView<>(items, rows);
    }

    /**
     * A tree with expandable branches.
     *
     * @param root the root item
     * @param labels the text of each value
     * @param <T> the value type
     * @return the tree
     */
    static <T> Tree<T> tree(TreeItem<T> root, Function<? super T, String> labels) {
        return new Tree<>(root, labels);
    }

    /**
     * A dialog screen; push it with {@code ui().push(...)}.
     *
     * @param title the title
     * @param content the content
     * @return the dialog, to add buttons to
     */
    static Dialog dialog(String title, Node<?> content) {
        return new Dialog(title, content);
    }

    /**
     * A screen showing one node over a dimmed background; push it with {@code ui().push(...)}.
     *
     * @param content the node
     * @return the screen
     */
    static Modal modal(Node<?> content) {
        return new Modal(content);
    }

    /**
     * A floating, draggable, closable panel.
     *
     * @param title the title
     * @param content the content
     * @return the window
     */
    static Window window(String title, Node<?> content) {
        return new Window(title, content);
    }

    /**
     * A colour picker.
     *
     * @return the picker
     */
    static ColorPicker colorPicker() {
        return new ColorPicker();
    }

    /**
     * Inventory slots with drag and drop.
     *
     * @param columns slots per row
     * @param slots the contents, {@code null} for empty slots
     * @param items makes the node of an item
     * @param <T> the item type
     * @return the grid
     */
    static <T extends @Nullable Object> ItemGrid<T> itemGrid(
            int columns, ListState<T> slots, Function<? super T, ? extends Node<?>> items) {
        return new ItemGrid<>(columns, slots, items);
    }

    /**
     * A thin separating line.
     *
     * @return the separator
     */
    static Separator separator() {
        return new Separator();
    }

    /**
     * A button that rebinds one slot of an action.
     *
     * @param action the action
     * @param slot the binding slot, from 0
     * @return the button
     */
    static KeybindButton keybindButton(InputAction action, int slot) {
        return new KeybindButton(action, slot);
    }

    /**
     * An on-screen stick for touch screens.
     *
     * @return the joystick
     */
    static VirtualJoystick virtualJoystick() {
        return new VirtualJoystick();
    }

    /**
     * A context menu.
     *
     * @param items the entries
     * @return the menu
     */
    static Menu menu(Menu.Item... items) {
        return new Menu(Arrays.asList(items));
    }

    /**
     * An entry of a context menu.
     *
     * @param text the text
     * @param action runs when chosen
     * @return the entry
     */
    static Menu.Item item(String text, Runnable action) {
        return new Menu.Item(text, action, true);
    }
}
