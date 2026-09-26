package dev.gulp.api.ui;

import dev.gulp.api.event.Subscription;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.Cursor;
import dev.gulp.api.input.KeyboardKey;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.math.Rect;
import dev.gulp.api.render.Draw;
import dev.gulp.api.spi.UiAccess;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import dev.gulp.api.text.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * A piece of UI: a container that places its children or a widget. Positions and sizes are computed by the engine;
 * game code only says how a node behaves inside its container ({@link #fill()}, {@link #expand()}, {@link #alignX}) or
 * where it sticks outside one ({@link #anchor}, {@link #offset}), in UI points of the base resolution times {@code
 * ui().scale()}.
 *
 * <p><b>Layout.</b> Each node measures its minimum size from its content (text, image, children, padding of its
 * style), limited by {@link #minSize} and {@link #maxSize} or fixed by {@link #width} and {@link #height}. Its parent
 * then gives it a rectangle. In a row or column the free space goes to children that {@link #expand(float)}, in
 * proportion to their ratios; across the axis a child fills the space ({@link Align#FILL}, the default) or keeps its
 * size at the start, centre or end. Changing text, size, visibility or children marks the node and its ancestors
 * dirty; layout runs once per frame for dirty subtrees only.
 *
 * <p><b>Looks.</b> The style comes from the theme by the node's type and state ({@link WidgetState}), its {@link
 * #variant} and its own {@link #style} overrides; state changes blend smoothly. {@link #alpha}, {@link #scale} and
 * {@link #rotation} are visual only and do not change the layout.
 *
 * <p><b>Interaction.</b> Handlers ({@link #onClick}, {@link #on}) belong to the node. Bindings to {@link Observable}
 * values subscribe while the node is on screen and end when it leaves.
 *
 * <pre>{@code
 * column(
 *         label("Settings").variant("title"),
 *         row(label("Music"), spacer(), slider(0, 1).bind(music).width(200)),
 *         button("Back").onClick(ui()::pop).alignX(Align.END))
 *     .gap(12).width(420);
 * }</pre>
 *
 * @param <N> the node's own type, returned by fluent methods
 */
@SuppressWarnings("this-escape")
public abstract class Node<N extends Node<N>> {

    /** Value of a size that is not set. */
    private static final float UNSET = Float.NaN;

    // tree
    @Nullable Node<?> parent;

    final List<Node<?>> children = new ArrayList<>(0);
    private final List<Node<?>> childView = Collections.unmodifiableList(children);
    boolean mounted;
    private boolean everMounted;

    // identity and looks
    private @Nullable String id;
    private String variant = "";
    private @Nullable Style override;
    private @Nullable Theme theme;

    // size and placement
    float fixedWidth = UNSET;
    float fixedHeight = UNSET;
    private float minWidth;
    private float minHeight;
    private float maxWidth = Float.POSITIVE_INFINITY;
    private float maxHeight = Float.POSITIVE_INFINITY;

    @Nullable Align alignX;

    @Nullable Align alignY;

    float expandX;
    float expandY;
    float expandMain;
    boolean growMain;
    private @Nullable Anchor anchor;
    float offsetX;
    float offsetY;

    // visuals
    private boolean visible = true;
    private float alpha = 1f;
    private float scale = 1f;
    private float rotation;
    private float pivotX = 0.5f;
    private float pivotY = 0.5f;
    private Color tint = Color.WHITE;

    // interaction
    private boolean enabled = true;
    private @Nullable Boolean focusable;
    private @Nullable MouseFilter mouseFilter;
    private @Nullable Object tooltip;
    private @Nullable Cursor cursor;
    private @Nullable EnumMap<Direction, Node<?>> neighbors;
    private @Nullable Object dragPayload;
    private @Nullable Predicate<Object> dropAccept;
    private @Nullable Consumer<Object> onDrop;
    private @Nullable Menu contextMenu;

    // state
    boolean hovered;
    boolean pressed;
    boolean focused;
    private WidgetState shownState = WidgetState.NORMAL;
    private @Nullable Style resolved;
    private @Nullable Theme resolvedTheme;
    private StyleBox fromBox = StyleBox.NONE;
    private float blend = 1f;

    // layout results
    float x;
    float y;
    float width;
    float height;
    float measuredWidth;
    float measuredHeight;
    boolean dirty = true;
    private @Nullable Rect bounds;

    // bindings and handlers
    private @Nullable List<Binding<?>> bindings;
    private @Nullable List<Handler<?>> handlers;

    // drawing without allocation
    private @Nullable Draw drawing;
    private float drawingAlpha;
    private final Runnable drawChildrenBody = () -> {
        Draw target = drawing;
        if (target != null) {
            drawChildren(target, drawingAlpha);
        }
    };

    /** Creates a node. */
    protected Node() {}

    /**
     * Installs the engine hooks of nodes. Called by {@link UiAccess}; an explicit call, not a static initializer,
     * because TeaVM initializes classes lazily.
     */
    public static void loadHooks() {
        UiAccess.install(new NodeHooks());
    }

    /**
     * Returns this node with its own type, for fluent methods of subclasses.
     *
     * @return this
     */
    @SuppressWarnings("unchecked")
    protected final N self() {
        return (N) this;
    }

    /**
     * Returns the theme widget type of this node, such as {@code button}; see {@link Theme}.
     *
     * @return the type name
     */
    protected abstract String styleType();

    // ================================================================== identity and tree

    /**
     * Gives the node an id for {@link #find}.
     *
     * @param value the id
     * @return this node
     */
    public N id(String value) {
        this.id = value;
        return self();
    }

    /**
     * Returns the id.
     *
     * @return the id, or {@code null}
     */
    public @Nullable String id() {
        return id;
    }

    /**
     * Returns the parent.
     *
     * @return the parent node, or {@code null} for a root
     */
    public @Nullable Node<?> parent() {
        return parent;
    }

    /**
     * Returns the children, in drawing order.
     *
     * @return a read-only live view
     */
    public List<Node<?>> children() {
        return childView;
    }

    /**
     * Returns whether the node is part of the live UI (a screen, the HUD, a popup or a world UI).
     *
     * @return {@code true} while on screen
     */
    public boolean isMounted() {
        return mounted;
    }

    /**
     * Finds a descendant (or this node) by id.
     *
     * @param nodeId the id
     * @return the first match in depth-first order, or {@code null}
     */
    public @Nullable Node<?> find(String nodeId) {
        if (nodeId.equals(id)) {
            return this;
        }
        for (int i = 0; i < children.size(); i++) {
            Node<?> found = children.get(i).find(nodeId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Finds all descendants (and this node) of a type.
     *
     * @param type the node class
     * @param <T> the node type
     * @return matches in depth-first order
     */
    public <T> List<T> findAll(Class<T> type) {
        List<T> found = new ArrayList<>();
        collect(type, found);
        return found;
    }

    private <T> void collect(Class<T> type, List<T> into) {
        if (type.isInstance(this)) {
            into.add(type.cast(this));
        }
        for (int i = 0; i < children.size(); i++) {
            children.get(i).collect(type, into);
        }
    }

    /** Removes this node from its parent. */
    public void removeFromParent() {
        Node<?> current = parent;
        if (current != null) {
            current.removeChild(this);
        }
    }

    /**
     * Adds a child at the end; for containers.
     *
     * @param child the child, moved from its previous parent
     */
    protected final void addChild(Node<?> child) {
        addChild(children.size(), child);
    }

    /**
     * Inserts a child; for containers.
     *
     * @param index the position
     * @param child the child, moved from its previous parent
     */
    protected final void addChild(int index, Node<?> child) {
        if (child == this || isDescendantOf(child)) {
            throw new IllegalArgumentException("A node cannot contain itself");
        }
        child.removeFromParent();
        child.parent = this;
        children.add(Math.min(index, children.size()), child);
        if (mounted) {
            child.mountTree();
        }
        invalidate();
    }

    /**
     * Removes a child; for containers.
     *
     * @param child the child
     * @return {@code true} if it was a child
     */
    protected final boolean removeChild(Node<?> child) {
        int index = children.indexOf(child);
        if (index < 0) {
            return false;
        }
        children.remove(index);
        child.parent = null;
        if (child.mounted) {
            child.unmountTree();
        }
        invalidate();
        return true;
    }

    /** Removes every child; for containers. */
    protected final void clearChildren() {
        for (int i = children.size() - 1; i >= 0; i--) {
            removeChild(children.get(i));
        }
    }

    private boolean isDescendantOf(Node<?> ancestor) {
        for (Node<?> p = parent; p != null; p = p.parent) {
            if (p == ancestor) {
                return true;
            }
        }
        return false;
    }

    // ================================================================== size and placement

    /**
     * Fixes the width.
     *
     * @param value UI points
     * @return this node
     */
    public N width(float value) {
        this.fixedWidth = value;
        invalidate();
        return self();
    }

    /**
     * Fixes the height.
     *
     * @param value UI points
     * @return this node
     */
    public N height(float value) {
        this.fixedHeight = value;
        invalidate();
        return self();
    }

    /**
     * Fixes both dimensions.
     *
     * @param w width in UI points
     * @param h height in UI points
     * @return this node
     */
    public N size(float w, float h) {
        this.fixedWidth = w;
        this.fixedHeight = h;
        invalidate();
        return self();
    }

    /**
     * Sets a minimum size; the content can make the node larger.
     *
     * @param w minimum width
     * @param h minimum height
     * @return this node
     */
    public N minSize(float w, float h) {
        this.minWidth = w;
        this.minHeight = h;
        invalidate();
        return self();
    }

    /**
     * Sets a maximum size; expanding stops there and wrapping text wraps at the width.
     *
     * @param w maximum width
     * @param h maximum height
     * @return this node
     */
    public N maxSize(float w, float h) {
        this.maxWidth = w;
        this.maxHeight = h;
        invalidate();
        return self();
    }

    /**
     * Stretches the node over its space in both axes (the default).
     *
     * @return this node
     */
    public N fill() {
        return align(Align.FILL, Align.FILL);
    }

    /**
     * Stretches the node horizontally.
     *
     * @return this node
     */
    public N fillX() {
        return alignX(Align.FILL);
    }

    /**
     * Stretches the node vertically.
     *
     * @return this node
     */
    public N fillY() {
        return alignY(Align.FILL);
    }

    /**
     * Sets how the node uses horizontal space.
     *
     * @param align fill, or keep the minimum size at the start, centre or end
     * @return this node
     */
    public N alignX(Align align) {
        this.alignX = align;
        invalidate();
        return self();
    }

    /**
     * Sets how the node uses vertical space.
     *
     * @param align fill, or keep the minimum size at the start, centre or end
     * @return this node
     */
    public N alignY(Align align) {
        this.alignY = align;
        invalidate();
        return self();
    }

    private N align(Align horizontal, Align vertical) {
        this.alignX = horizontal;
        this.alignY = vertical;
        invalidate();
        return self();
    }

    /**
     * Keeps the minimum size at the start (left and top).
     *
     * @return this node
     */
    public N shrinkStart() {
        return align(Align.START, Align.START);
    }

    /**
     * Keeps the minimum size in the middle.
     *
     * @return this node
     */
    public N shrinkCenter() {
        return align(Align.CENTER, Align.CENTER);
    }

    /**
     * Keeps the minimum size at the end (right and bottom).
     *
     * @return this node
     */
    public N shrinkEnd() {
        return align(Align.END, Align.END);
    }

    /**
     * Takes a share of the free space along the container's axis (ratio 1).
     *
     * @return this node
     */
    public N expand() {
        return expand(1f);
    }

    /**
     * Takes a share of the free space along the container's axis: a row gives width, a column height, a grid both.
     *
     * @param ratio the share relative to other expanding siblings
     * @return this node
     */
    public N expand(float ratio) {
        this.expandMain = Math.max(0f, ratio);
        invalidate();
        return self();
    }

    /**
     * Takes a share of the free width, in rows and grids.
     *
     * @param ratio the share
     * @return this node
     */
    public N expandX(float ratio) {
        this.expandX = Math.max(0f, ratio);
        invalidate();
        return self();
    }

    /**
     * Takes a share of the free height, in columns and grids.
     *
     * @param ratio the share
     * @return this node
     */
    public N expandY(float ratio) {
        this.expandY = Math.max(0f, ratio);
        invalidate();
        return self();
    }

    /**
     * Expands and fills along the container's axis: {@code expand()} plus fill.
     *
     * @return this node
     */
    public N grow() {
        this.growMain = true;
        return expand(1f);
    }

    /**
     * Sticks the node to a point or edge of its parent when the parent does not place it (a {@link Stack}, the HUD or
     * a screen root).
     *
     * @param value the anchor preset
     * @return this node
     */
    public N anchor(Anchor value) {
        this.anchor = value;
        invalidate();
        return self();
    }

    /**
     * Moves an anchored node inward from the edges it sticks to, or right and down from a centre anchor.
     *
     * @param dx horizontal UI points
     * @param dy vertical UI points
     * @return this node
     */
    public N offset(float dx, float dy) {
        this.offsetX = dx;
        this.offsetY = dy;
        invalidate();
        return self();
    }

    /**
     * Returns the horizontal offset.
     *
     * @return UI points
     */
    public float offsetX() {
        return offsetX;
    }

    /**
     * Returns the vertical offset.
     *
     * @return UI points
     */
    public float offsetY() {
        return offsetY;
    }

    /**
     * Stretches the node over its whole parent.
     *
     * @return this node
     */
    public N fillParent() {
        return anchor(Anchor.FULL).offset(0f, 0f);
    }

    /**
     * Stretches the node over its parent, leaving space at the edges.
     *
     * @param insets space at each edge; horizontal and vertical values are averaged per axis
     * @return this node
     */
    public N fillParent(Insets insets) {
        anchor(Anchor.FULL);
        return offset((insets.left() + insets.right()) / 2f, (insets.top() + insets.bottom()) / 2f);
    }

    /**
     * Returns the anchor.
     *
     * @return the anchor, or {@code null} to fill the parent
     */
    public @Nullable Anchor anchor() {
        return anchor;
    }

    // ================================================================== looks

    /**
     * Uses a named variant of the theme style, such as {@code "title"} for a label or {@code "danger"} for a button.
     *
     * @param name the variant
     * @return this node
     */
    public N variant(String name) {
        this.variant = name;
        restyle();
        return self();
    }

    /**
     * Returns the variant.
     *
     * @return the variant name, empty for none
     */
    public String variant() {
        return variant;
    }

    /**
     * Overrides single style values for this node, in every state.
     *
     * @param change edits the override
     * @return this node
     */
    public N style(Consumer<Style> change) {
        Style current = override;
        if (current == null) {
            current = new Style();
            override = current;
        }
        change.accept(current);
        restyle();
        return self();
    }

    /**
     * Sets the theme of this node and its subtree.
     *
     * @param value the theme
     * @return this node
     */
    public N theme(Theme value) {
        this.theme = value;
        restyleTree();
        return self();
    }

    /**
     * Shows or hides the node; a hidden node takes no space.
     *
     * @param value whether visible
     * @return this node
     */
    public N visible(boolean value) {
        if (visible != value) {
            visible = value;
            invalidate();
        }
        return self();
    }

    /**
     * Binds the visibility.
     *
     * @param source whether visible
     * @return this node
     */
    public N visible(Observable<Boolean> source) {
        bind(source, this::visible);
        return self();
    }

    /**
     * Returns whether the node is visible.
     *
     * @return {@code true} if shown
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Sets the opacity of the node and its subtree.
     *
     * @param value {@code 0..1}
     * @return this node
     */
    public N alpha(float value) {
        this.alpha = Math.max(0f, Math.min(1f, value));
        return self();
    }

    /**
     * Returns the opacity.
     *
     * @return {@code 0..1}
     */
    public float alpha() {
        return alpha;
    }

    /**
     * Scales the drawing around the pivot; the layout does not change.
     *
     * @param value the factor
     * @return this node
     */
    public N scale(float value) {
        this.scale = value;
        return self();
    }

    /**
     * Returns the visual scale.
     *
     * @return the factor
     */
    public float scale() {
        return scale;
    }

    /**
     * Rotates the drawing around the pivot; the layout does not change.
     *
     * @param degrees the angle
     * @return this node
     */
    public N rotation(float degrees) {
        this.rotation = degrees;
        return self();
    }

    /**
     * Returns the visual rotation.
     *
     * @return degrees
     */
    public float rotation() {
        return rotation;
    }

    /**
     * Sets the point that scale and rotation turn around, as fractions of the size.
     *
     * @param fx {@code 0} left, {@code 1} right
     * @param fy {@code 0} top, {@code 1} bottom
     * @return this node
     */
    public N pivot(float fx, float fy) {
        this.pivotX = fx;
        this.pivotY = fy;
        return self();
    }

    /**
     * Sets a colour that images of the node are multiplied by.
     *
     * @param color the tint
     * @return this node
     */
    public N tint(Color color) {
        this.tint = color;
        return self();
    }

    /**
     * Returns the tint.
     *
     * @return the colour
     */
    public Color tint() {
        return tint;
    }

    // ================================================================== interaction

    /**
     * Enables or disables the node and its subtree; disabled nodes do not react and look greyed out.
     *
     * @param value whether enabled
     * @return this node
     */
    public N enabled(boolean value) {
        if (enabled != value) {
            enabled = value;
            restyleTree();
            if (!value && focused && UiAccess.hasBackend()) {
                UiAccess.backend().focus(null);
            }
        }
        return self();
    }

    /**
     * Binds whether the node is enabled.
     *
     * @param source whether enabled
     * @return this node
     */
    public N enabled(Observable<Boolean> source) {
        bind(source, this::enabled);
        return self();
    }

    /**
     * Returns whether this node and all its ancestors are enabled.
     *
     * @return {@code true} if it reacts to input
     */
    public boolean isEnabled() {
        for (Node<?> n = this; n != null; n = n.parent) {
            if (!n.enabled) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sets whether keyboard and gamepad focus can land on the node; widgets that react to input are focusable by
     * default.
     *
     * @param value whether focusable
     * @return this node
     */
    public N focusable(boolean value) {
        this.focusable = value;
        return self();
    }

    /**
     * Returns whether focus can land on the node.
     *
     * @return {@code true} if focusable, visible and enabled
     */
    public boolean isFocusable() {
        Boolean explicit = focusable;
        boolean can = explicit != null ? explicit : isFocusableByDefault();
        return can && visible && isEnabled();
    }

    /**
     * Returns whether the node takes focus unless told otherwise; widgets that react to input override it.
     *
     * @return {@code false} for plain nodes
     */
    protected boolean isFocusableByDefault() {
        return hasHandler(NodeEvent.Click.class);
    }

    /**
     * Sets what the node does with the pointer.
     *
     * @param value the filter
     * @return this node
     */
    public N mouseFilter(MouseFilter value) {
        this.mouseFilter = value;
        return self();
    }

    /**
     * Returns what the node does with the pointer.
     *
     * @return the explicit filter, or the default: {@code STOP} for widgets and nodes with handlers, tooltips, drag
     *     or menus, {@code IGNORE} for layout containers
     */
    public MouseFilter mouseFilter() {
        MouseFilter explicit = mouseFilter;
        if (explicit != null) {
            return explicit;
        }
        if (handlers != null || tooltip != null || dragPayload != null || dropAccept != null || contextMenu != null) {
            return MouseFilter.STOP;
        }
        return defaultMouseFilter();
    }

    /**
     * Returns the filter used when none is set.
     *
     * @return {@link MouseFilter#IGNORE} for plain nodes; widgets return {@link MouseFilter#STOP}
     */
    protected MouseFilter defaultMouseFilter() {
        return MouseFilter.IGNORE;
    }

    /**
     * Shows a text near the pointer (or below the focused node) after the theme's delay.
     *
     * @param text the tooltip
     * @return this node
     */
    public N tooltip(String text) {
        this.tooltip = text;
        return self();
    }

    /**
     * Shows a node as the tooltip.
     *
     * @param content the tooltip content
     * @return this node
     */
    public N tooltip(Node<?> content) {
        this.tooltip = content;
        return self();
    }

    /**
     * Sets the mouse cursor over the node.
     *
     * @param value the cursor
     * @return this node
     */
    public N cursor(Cursor value) {
        this.cursor = value;
        return self();
    }

    /**
     * Returns the cursor over the node.
     *
     * @return the cursor, or {@code null} for the default
     */
    public @Nullable Cursor cursor() {
        return cursor;
    }

    /**
     * Overrides where focus goes from this node in a direction; by default the nearest focusable node is chosen from
     * the layout.
     *
     * @param direction the direction
     * @param target the node to focus
     * @return this node
     */
    public N focusNeighbor(Direction direction, Node<?> target) {
        EnumMap<Direction, Node<?>> map = neighbors;
        if (map == null) {
            map = new EnumMap<>(Direction.class);
            neighbors = map;
        }
        map.put(direction, target);
        return self();
    }

    /**
     * Makes the node draggable; while dragging, a copy follows the pointer and drop targets that accept the payload
     * light up. With a gamepad, accept picks the node up and accept on a target drops it.
     *
     * @param payload what is dragged
     * @return this node
     */
    public N draggable(Object payload) {
        this.dragPayload = payload;
        return self();
    }

    /**
     * Makes the node a drop target.
     *
     * @param accept which payloads it accepts
     * @param action receives the dropped payload
     * @return this node
     */
    public N dropTarget(Predicate<Object> accept, Consumer<Object> action) {
        this.dropAccept = accept;
        this.onDrop = action;
        return self();
    }

    /**
     * Sets the context menu.
     *
     * @param menu the menu
     * @return this node
     */
    public N contextMenu(Menu menu) {
        this.contextMenu = menu;
        return self();
    }

    /**
     * Moves the keyboard and gamepad focus to this node.
     *
     * @return this node
     */
    public N requestFocus() {
        if (UiAccess.hasBackend()) {
            UiAccess.backend().focus(this);
        }
        return self();
    }

    /**
     * Returns whether the node has the focus.
     *
     * @return {@code true} if focused
     */
    public boolean hasFocus() {
        return focused;
    }

    /**
     * Returns whether the pointer is over the node.
     *
     * @return {@code true} if hovered
     */
    public boolean isHovered() {
        return hovered;
    }

    /**
     * Returns whether the node is being pressed.
     *
     * @return {@code true} while pressed
     */
    public boolean isPressed() {
        return pressed;
    }

    // ================================================================== events

    /**
     * Handles an event of this node.
     *
     * @param type the event class
     * @param handler the handler
     * @param <E> the event type
     * @return this node
     */
    public <E extends NodeEvent> N on(Class<E> type, Consumer<? super E> handler) {
        List<Handler<?>> list = handlers;
        if (list == null) {
            list = new ArrayList<>(1);
            handlers = list;
        }
        list.add(new Handler<>(type, handler));
        return self();
    }

    /**
     * Runs an action when the node is clicked, tapped or accepted with the keyboard or gamepad.
     *
     * @param action the action
     * @return this node
     */
    public N onClick(Runnable action) {
        return on(NodeEvent.Click.class, e -> action.run());
    }

    /**
     * Runs an action when the pointer enters or leaves.
     *
     * @param action receives {@code true} on enter
     * @return this node
     */
    public N onHover(Consumer<Boolean> action) {
        return on(NodeEvent.Hover.class, e -> action.accept(e.entered()));
    }

    /**
     * Runs an action when the focus arrives or leaves.
     *
     * @param action receives {@code true} when focused
     * @return this node
     */
    public N onFocus(Consumer<Boolean> action) {
        return on(NodeEvent.Focus.class, e -> action.accept(e.gained()));
    }

    /**
     * Returns whether a handler for an event type is registered.
     *
     * @param type the event class
     * @return {@code true} if one is
     */
    protected final boolean hasHandler(Class<? extends NodeEvent> type) {
        List<Handler<?>> list = handlers;
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                if (type.isAssignableFrom(list.get(i).type)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Sends an event to this node's handlers.
     *
     * @param event the event
     */
    protected final void fire(NodeEvent event) {
        List<Handler<?>> list = handlers;
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            list.get(i).offer(event);
        }
    }

    /** Clicks the node: plays the click sound and fires {@link NodeEvent.Click}. Subclasses add their own action. */
    protected void click() {
        if (UiAccess.hasBackend()) {
            UiAccess.backend().play(style().clickSound());
        }
        fire(new NodeEvent.Click(this));
    }

    /**
     * Fires a change event with a new value.
     *
     * @param value the value
     */
    protected final void fireChange(Object value) {
        fire(new NodeEvent.Change(this, value));
    }

    /**
     * Fires a submit event.
     *
     * @param text the submitted text
     */
    protected final void fireSubmit(String text) {
        fire(new NodeEvent.Submit(this, text));
    }

    /**
     * Returns whether clicks do something, so that pressing shows and accept clicks.
     *
     * @return {@code true} if there are click handlers
     */
    protected boolean isClickable() {
        return hasHandler(NodeEvent.Click.class);
    }

    // ================================================================== bindings

    /**
     * Keeps something in sync with an observable while the node is on screen; the current value is applied at once.
     *
     * @param source the observable
     * @param apply receives each value
     * @param <T> the value type
     */
    protected final <T> void bind(Observable<T> source, Consumer<? super T> apply) {
        List<Binding<?>> list = bindings;
        if (list == null) {
            list = new ArrayList<>(1);
            bindings = list;
        }
        Binding<T> binding = new Binding<>(source, apply);
        list.add(binding);
        apply.accept(source.get());
        if (mounted) {
            binding.start();
        }
    }

    // ================================================================== style

    /**
     * Returns the style of the current state, resolved from the theme, variant and overrides.
     *
     * @return the style; do not change it
     */
    protected final Style style() {
        Theme current = effectiveTheme();
        Style style = resolved;
        if (style == null || resolvedTheme != current) {
            Style base = current.resolve(styleType(), variant, shownState);
            Style own = override;
            style = own == null ? base : base.copy().apply(own);
            resolved = style;
            resolvedTheme = current;
        }
        return style;
    }

    /**
     * Returns the theme of this subtree.
     *
     * @return the nearest theme set on this node or an ancestor, or the global theme
     */
    protected final Theme effectiveTheme() {
        for (Node<?> n = this; n != null; n = n.parent) {
            Theme own = n.theme;
            if (own != null) {
                return own;
            }
        }
        return UiAccess.hasBackend() ? UiAccess.backend().theme() : Theme.DEFAULT;
    }

    /**
     * Returns the state the theme styles, from the flags in priority order.
     *
     * @return the state
     */
    protected WidgetState state() {
        if (!isEnabled()) {
            return WidgetState.DISABLED;
        }
        if (pressed) {
            return WidgetState.PRESSED;
        }
        if (isChecked()) {
            return WidgetState.CHECKED;
        }
        if (focused && (!UiAccess.hasBackend() || UiAccess.backend().isFocusVisible())) {
            return WidgetState.FOCUSED;
        }
        if (hovered) {
            return WidgetState.HOVER;
        }
        return WidgetState.NORMAL;
    }

    /**
     * Returns whether the node shows as checked or selected.
     *
     * @return {@code false} unless a subclass says otherwise
     */
    protected boolean isChecked() {
        return false;
    }

    /** Recomputes the state; call after a flag that {@link #state()} reads changed. */
    protected final void refreshState() {
        WidgetState next = state();
        if (next != shownState) {
            StyleBox before = currentBox();
            shownState = next;
            resolved = null;
            float seconds = style().transition();
            if (seconds > 0f && mounted) {
                fromBox = before;
                blend = 0f;
            } else {
                blend = 1f;
            }
            Style style = style();
            if (!Objects.equals(style.padding(), paddingUsed) || style.fontSize() != fontUsed) {
                invalidate();
            }
        }
    }

    private @Nullable Insets paddingUsed;
    private float fontUsed = -1f;

    private StyleBox currentBox() {
        StyleBox target = style().background();
        return blend >= 1f ? target : fromBox.lerp(target, blend);
    }

    private void restyle() {
        resolved = null;
        shownState = state();
        invalidate();
    }

    private void restyleTree() {
        restyle();
        for (int i = 0; i < children.size(); i++) {
            children.get(i).restyleTree();
        }
    }

    // ================================================================== layout

    /** Marks the node and its ancestors for layout in the next frame. */
    protected final void invalidate() {
        for (Node<?> n = this; n != null; n = n.parent) {
            if (n.dirty && n != this) {
                break;
            }
            n.dirty = true;
        }
        if (mounted && UiAccess.hasBackend()) {
            UiAccess.backend().requestLayout();
        }
    }

    /**
     * Measures the minimum size of the content, without padding; children are measured already.
     *
     * @return the content size
     */
    protected Size measure() {
        return Size.ZERO;
    }

    /**
     * Places the children inside the node's rectangle; called when the rectangle or the content changed. Containers
     * override it and call {@link #place} for each child.
     */
    protected void arrange() {}

    /**
     * Returns whether this node's padding comes from its style; containers without backgrounds return {@code false}.
     *
     * @return {@code true} for widgets and panels
     */
    protected boolean usesPadding() {
        return true;
    }

    /**
     * Returns the padding used by the layout.
     *
     * @return the style padding, or zero
     */
    protected final Insets padding() {
        return usesPadding() ? style().padding() : Insets.ZERO;
    }

    void measureTree() {
        if (!dirty) {
            return;
        }
        for (int i = 0; i < children.size(); i++) {
            Node<?> child = children.get(i);
            if (child.visible) {
                child.measureTree();
            }
        }
        Style style = style();
        Insets pad = padding();
        paddingUsed = style.padding();
        fontUsed = style.fontSize();
        Size content = measure();
        float w = content.width() + pad.horizontal();
        float h = content.height() + pad.vertical();
        w = Math.max(w, Math.max(minWidth, style.minWidth()));
        h = Math.max(h, Math.max(minHeight, style.minHeight()));
        if (!Float.isNaN(fixedWidth)) {
            w = fixedWidth;
        }
        if (!Float.isNaN(fixedHeight)) {
            h = fixedHeight;
        }
        measuredWidth = Math.min(w, maxWidth);
        measuredHeight = Math.min(h, maxHeight);
    }

    /**
     * Describes the layout flags, for the UI inspector and debugging.
     *
     * @return alignment, expand ratios and fixed or limited sizes
     */
    public String layoutInfo() {
        StringBuilder text = new StringBuilder();
        text.append("align ").append(alignX == null ? "-" : alignX).append('/').append(alignY == null ? "-" : alignY);
        if (expandMain > 0f || expandX > 0f || expandY > 0f) {
            text.append("  expand ").append(expandMain);
            if (expandX > 0f) {
                text.append(" x").append(expandX);
            }
            if (expandY > 0f) {
                text.append(" y").append(expandY);
            }
        }
        if (!Float.isNaN(fixedWidth) || !Float.isNaN(fixedHeight)) {
            text.append("  fixed ").append(fixedWidth).append(" x ").append(fixedHeight);
        }
        if (maxWidth < Float.POSITIVE_INFINITY || maxHeight < Float.POSITIVE_INFINITY) {
            text.append("  max ").append(maxWidth).append(" x ").append(maxHeight);
        }
        return text.toString();
    }

    /**
     * Returns the measured minimum width, padding included.
     *
     * @return UI points
     */
    public final float minWidth() {
        return measuredWidth;
    }

    /**
     * Returns the measured minimum height, padding included.
     *
     * @return UI points
     */
    public final float minHeight() {
        return measuredHeight;
    }

    /**
     * Returns the maximum width.
     *
     * @return UI points, infinite by default
     */
    public final float maxWidth() {
        return Float.isNaN(fixedWidth) ? maxWidth : fixedWidth;
    }

    /**
     * Returns the maximum height.
     *
     * @return UI points, infinite by default
     */
    public final float maxHeight() {
        return Float.isNaN(fixedHeight) ? maxHeight : fixedHeight;
    }

    /**
     * Gives a child its rectangle; for containers inside {@link #arrange()}. Positions are rounded to UI pixels.
     *
     * @param child the child
     * @param cx left
     * @param cy top
     * @param cw width
     * @param ch height
     */
    protected final void place(Node<?> child, float cx, float cy, float cw, float ch) {
        child.setRect(cx, cy, cw, ch);
    }

    /**
     * Gives a child a rectangle by its anchor inside an area, or the whole area without an anchor; for stacks.
     *
     * @param child the child
     * @param ax area left
     * @param ay area top
     * @param aw area width
     * @param ah area height
     */
    protected final void placeAnchored(Node<?> child, float ax, float ay, float aw, float ah) {
        Anchor a = child.anchor;
        if (a == null) {
            child.setRect(ax, ay, aw, ah);
            return;
        }
        float cw = Anchor.place(a.minX(), a.maxX(), ax, aw, child.measuredWidth, child.offsetX, false);
        float ch = Anchor.place(a.minY(), a.maxY(), ay, ah, child.measuredHeight, child.offsetY, false);
        float cx = Anchor.place(a.minX(), a.maxX(), ax, aw, cw, child.offsetX, true);
        float cy = Anchor.place(a.minY(), a.maxY(), ay, ah, ch, child.offsetY, true);
        child.setRect(cx, cy, cw, ch);
    }

    /**
     * Places a child inside a cell along one axis by its alignment: fill, or its minimum size at start, centre, end.
     *
     * @param child the child
     * @param horizontal whether the axis is X
     * @param start cell start
     * @param length cell length
     * @param fallback the alignment when the child has none
     * @param wantStart {@code true} for the start, {@code false} for the length
     * @return the start or the length
     */
    protected static float alignIn(
            Node<?> child, boolean horizontal, float start, float length, Align fallback, boolean wantStart) {
        Align align = horizontal ? child.alignX : child.alignY;
        if (align == null) {
            align = fallback;
        }
        float min = horizontal ? child.measuredWidth : child.measuredHeight;
        float max = horizontal ? child.maxWidth() : child.maxHeight();
        float size = align == Align.FILL ? Math.min(length, max) : Math.min(min, length);
        size = Math.max(size, Math.min(min, length));
        if (!wantStart) {
            return size;
        }
        float factor = align == Align.FILL ? 0f : align.factor();
        return start + (length - size) * factor;
    }

    private void setRect(float nx, float ny, float nw, float nh) {
        // Whole UI points keep text and borders sharp.
        float rx = Math.round(nx);
        float ry = Math.round(ny);
        float rw = Math.round(nx + nw) - rx;
        float rh = Math.round(ny + nh) - ry;
        boolean changed = rx != x || ry != y || rw != width || rh != height;
        boolean needed = changed || dirty;
        x = rx;
        y = ry;
        width = rw;
        height = rh;
        // Cleared first, so that a child invalidating itself while arranged (wrapping text) marks this node again.
        dirty = false;
        if (needed) {
            bounds = null;
            arrange();
            afterArrange();
        }
    }

    /** Called after the node was placed; wrapping text re-measures itself here if the width changed. */
    void afterArrange() {}

    /**
     * Returns the node's rectangle in UI points.
     *
     * @return the bounds from the last layout
     */
    public final Rect bounds() {
        Rect cached = bounds;
        if (cached == null) {
            cached = new Rect(x, y, width, height);
            bounds = cached;
        }
        return cached;
    }

    /**
     * Returns the left edge.
     *
     * @return UI points
     */
    public final float x() {
        return x;
    }

    /**
     * Returns the top edge.
     *
     * @return UI points
     */
    public final float y() {
        return y;
    }

    /**
     * Returns the laid-out width.
     *
     * @return UI points
     */
    public final float width() {
        return width;
    }

    /**
     * Returns the laid-out height.
     *
     * @return UI points
     */
    public final float height() {
        return height;
    }

    /**
     * Returns whether a point lies inside the node.
     *
     * @param px UI x
     * @param py UI y
     * @return {@code true} if inside
     */
    public boolean contains(float px, float py) {
        return px >= x && py >= y && px < x + width && py < y + height;
    }

    /**
     * Returns whether children are clipped to this node's rectangle (scroll containers).
     *
     * @return {@code false} unless a subclass clips
     */
    public boolean clipsChildren() {
        return false;
    }

    /**
     * Lays out text with this node's engine; for widgets that show text.
     *
     * @param text the text
     * @param style the text style
     * @param box the box
     * @return the layout
     */
    protected static TextLayout layoutText(Text text, TextStyle style, TextBox box) {
        return UiAccess.backend().layout(text, style, box);
    }

    /**
     * Returns a number that changes when translated text may read differently, such as after {@code
     * translations().setLocale(...)}; widgets that cache a text layout compare it to know when to lay out again.
     *
     * <pre>{@code
     * if (layout == null || layoutRevision != textRevision()) {
     *     layout = layoutText(text, style().textStyle(), TextBox.NONE);
     *     layoutRevision = textRevision();
     * }
     * }</pre>
     *
     * @return the revision
     */
    protected static int textRevision() {
        return UiAccess.backend().textRevision();
    }

    // ================================================================== drawing and animation

    /**
     * Draws the node itself, below its children. The default draws the style background.
     *
     * @param draw where to draw, in UI points
     */
    protected void draw(Draw draw) {
        drawBackground(draw);
    }

    /**
     * Draws the style background over the node's rectangle, blending during state transitions.
     *
     * @param draw where to draw
     */
    protected final void drawBackground(Draw draw) {
        currentBox().draw(draw, x, y, width, height);
    }

    /**
     * Draws over the children, for example scroll bars.
     *
     * @param draw where to draw
     */
    protected void drawOver(Draw draw) {}

    /**
     * Draws the children; override to skip some (a virtual list draws only visible rows).
     *
     * @param draw where to draw
     * @param inherited the opacity of this node
     */
    protected void drawChildren(Draw draw, float inherited) {
        for (int i = 0; i < children.size(); i++) {
            children.get(i).drawTree(draw, inherited);
        }
    }

    void drawTree(Draw draw, float inherited) {
        if (!visible) {
            return;
        }
        float a = inherited * alpha;
        if (a <= 0.001f) {
            return;
        }
        boolean transformed = scale != 1f || rotation != 0f;
        draw.push();
        if (transformed) {
            float px = x + width * pivotX;
            float py = y + height * pivotY;
            draw.translate(px, py);
            if (rotation != 0f) {
                draw.rotate(rotation);
            }
            if (scale != 1f) {
                draw.scale(scale, scale);
            }
            draw.translate(-px, -py);
        }
        draw.alpha(a);
        draw(draw);
        if (clipsChildren()) {
            Draw previous = drawing;
            float previousAlpha = drawingAlpha;
            drawing = draw;
            drawingAlpha = a;
            draw.clip(bounds(), drawChildrenBody);
            drawing = previous;
            drawingAlpha = previousAlpha;
        } else {
            drawChildren(draw, a);
        }
        draw.alpha(a);
        drawOver(draw);
        draw.pop();
    }

    /**
     * Advances animations; called every frame with real time while the node is on screen.
     *
     * @param seconds real seconds since the last frame
     */
    protected void update(float seconds) {}

    private void updateTree(float seconds) {
        if (blend < 1f) {
            float length = style().transition();
            blend = length <= 0f ? 1f : Math.min(1f, blend + seconds / length);
        }
        update(seconds);
        for (int i = 0; i < children.size(); i++) {
            children.get(i).updateTree(seconds);
        }
    }

    // ================================================================== input hooks

    /**
     * Handles a pointer press inside the node. The default presses clickable nodes.
     *
     * @param px UI x
     * @param py UI y
     * @param button the button
     * @return {@code true} to capture the pointer until release
     */
    protected boolean pointerDown(float px, float py, MouseButton button) {
        if (button == MouseButton.LEFT && isClickable()) {
            pressed = true;
            refreshState();
            return true;
        }
        return false;
    }

    /**
     * Handles pointer motion while this node captures the pointer.
     *
     * @param px UI x
     * @param py UI y
     */
    protected void pointerDrag(float px, float py) {}

    /**
     * Handles the release of a captured pointer. The default clicks when released inside.
     *
     * @param px UI x
     * @param py UI y
     * @param inside whether the pointer is over the node
     */
    protected void pointerUp(float px, float py, boolean inside) {
        if (pressed) {
            pressed = false;
            refreshState();
            if (inside && isEnabled()) {
                click();
            }
        }
    }

    /**
     * Handles a wheel turn over the node.
     *
     * @param dx horizontal amount
     * @param dy vertical amount, positive downwards
     * @return {@code true} if consumed
     */
    protected boolean scrolled(float dx, float dy) {
        return false;
    }

    /**
     * Handles a UI action while focused. The default clicks on accept.
     *
     * @param action the action
     * @return {@code true} if consumed; otherwise directions move the focus
     */
    protected boolean navigate(UiAction action) {
        if (action == UiAction.ACCEPT && isClickable()) {
            click();
            return true;
        }
        return false;
    }

    /**
     * Handles a key press while focused, for text input.
     *
     * @param key the key
     * @param modifiers modifier bits (1 shift, 2 control, 4 alt, 8 super)
     * @param repeat whether it repeats
     * @return {@code true} if consumed
     */
    protected boolean keyPressed(KeyboardKey key, int modifiers, boolean repeat) {
        return false;
    }

    /**
     * Handles a typed character while focused.
     *
     * @param codePoint the character
     * @return {@code true} if consumed
     */
    protected boolean charTyped(int codePoint) {
        return false;
    }

    /**
     * Called when the focus arrives or leaves.
     *
     * @param gained whether focused now
     */
    protected void focusChanged(boolean gained) {}

    /** Called when the node joins the live UI. */
    protected void mounted() {}

    /** Called when the node leaves the live UI. */
    protected void unmounted() {}

    private void mountTree() {
        mounted = true;
        everMounted = true;
        List<Binding<?>> list = bindings;
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                list.get(i).start();
            }
        }
        mounted();
        for (int i = 0; i < children.size(); i++) {
            children.get(i).mountTree();
        }
        restyle();
    }

    private void unmountTree() {
        for (int i = 0; i < children.size(); i++) {
            children.get(i).unmountTree();
        }
        List<Binding<?>> list = bindings;
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                list.get(i).stop();
            }
        }
        if (focused && UiAccess.hasBackend()) {
            UiAccess.backend().focus(null);
        }
        mounted = false;
        hovered = false;
        pressed = false;
        focused = false;
        shownState = WidgetState.NORMAL;
        resolved = null;
        unmounted();
    }

    /**
     * Returns whether the node was on screen once and is not any more; animations targeting it stop.
     *
     * @return {@code true} after it left the live UI
     */
    public boolean isDisposed() {
        return everMounted && !mounted;
    }

    @Override
    public String toString() {
        String name = getClass().getSimpleName();
        return (name.isEmpty() ? styleType() : name) + (id != null ? "#" + id : "") + "[" + x + ", " + y + ", " + width
                + "x" + height + "]";
    }

    // ================================================================== internals

    /** A handler of one event type. */
    private record Handler<E extends NodeEvent>(Class<E> type, Consumer<? super E> action) {
        void offer(NodeEvent event) {
            if (type.isInstance(event)) {
                action.accept(type.cast(event));
            }
        }
    }

    /** A binding that subscribes while the node is mounted. */
    private static final class Binding<T> {
        private final Observable<T> source;
        private final Consumer<? super T> apply;
        private @Nullable Subscription subscription;

        Binding(Observable<T> source, Consumer<? super T> apply) {
            this.source = source;
            this.apply = apply;
        }

        void start() {
            if (subscription == null) {
                subscription = source.subscribe(apply);
                apply.accept(source.get());
            }
        }

        void stop() {
            Subscription current = subscription;
            if (current != null) {
                current.cancel();
                subscription = null;
            }
        }
    }

    /** Engine access to the protected parts of nodes. */
    private static final class NodeHooks implements UiAccess.Hooks {
        @Override
        public boolean layout(Node<?> root, float ax, float ay, float aw, float ah) {
            root.measureTree();
            root.placeAnchoredRoot(ax, ay, aw, ah);
            return root.dirty;
        }

        @Override
        public boolean isDirty(Node<?> root) {
            return root.dirty;
        }

        @Override
        public void restyle(Node<?> root) {
            root.restyleTree();
        }

        @Override
        public void draw(Node<?> node, Draw draw, float alpha) {
            node.drawTree(draw, alpha);
        }

        @Override
        public void update(Node<?> node, float seconds) {
            node.updateTree(seconds);
        }

        @Override
        public void mount(Node<?> node) {
            if (!node.mounted) {
                node.mountTree();
            }
        }

        @Override
        public void unmount(Node<?> node) {
            if (node.mounted) {
                node.unmountTree();
            }
        }

        @Override
        public boolean pointerDown(Node<?> node, float px, float py, MouseButton button) {
            return node.isEnabled() && node.pointerDown(px, py, button);
        }

        @Override
        public void pointerDrag(Node<?> node, float px, float py) {
            node.pointerDrag(px, py);
        }

        @Override
        public void pointerUp(Node<?> node, float px, float py, boolean inside) {
            node.pointerUp(px, py, inside);
        }

        @Override
        public boolean scroll(Node<?> node, float dx, float dy) {
            return node.isEnabled() && node.scrolled(dx, dy);
        }

        @Override
        public boolean navigate(Node<?> node, UiAction action) {
            return node.isEnabled() && node.navigate(action);
        }

        @Override
        public boolean key(Node<?> node, KeyboardKey key, int modifiers, boolean repeat) {
            return node.isEnabled() && node.keyPressed(key, modifiers, repeat);
        }

        @Override
        public boolean text(Node<?> node, int codePoint) {
            return node.isEnabled() && node.charTyped(codePoint);
        }

        @Override
        public void hover(Node<?> node, boolean on) {
            if (node.hovered == on) {
                return;
            }
            node.hovered = on;
            node.refreshState();
            if (on && UiAccess.hasBackend() && node.isClickable() && node.isEnabled()) {
                UiAccess.backend().play(node.style().hoverSound());
            }
            node.fire(new NodeEvent.Hover(node, on));
        }

        @Override
        public void focus(Node<?> node, boolean on) {
            if (node.focused == on) {
                return;
            }
            node.focused = on;
            node.refreshState();
            node.focusChanged(on);
            node.fire(new NodeEvent.Focus(node, on));
        }

        @Override
        public @Nullable Object dragPayload(Node<?> node) {
            return node.dragPayload;
        }

        @Override
        public boolean acceptsDrop(Node<?> node, Object payload) {
            Predicate<Object> accept = node.dropAccept;
            return accept != null && node.isEnabled() && accept.test(payload);
        }

        @Override
        public void drop(Node<?> node, Object payload) {
            Consumer<Object> action = node.onDrop;
            if (action != null) {
                action.accept(payload);
            }
        }

        @Override
        public @Nullable Menu contextMenu(Node<?> node) {
            return node.contextMenu;
        }

        @Override
        public @Nullable Node<?> focusNeighbor(Node<?> node, Direction direction) {
            EnumMap<Direction, Node<?>> map = node.neighbors;
            return map == null ? null : map.get(direction);
        }

        @Override
        public @Nullable Object tooltip(Node<?> node) {
            return node.tooltip;
        }
    }

    private void placeAnchoredRoot(float ax, float ay, float aw, float ah) {
        Anchor a = anchor;
        if (a == null) {
            setRect(ax, ay, aw, ah);
            return;
        }
        float cw = Anchor.place(a.minX(), a.maxX(), ax, aw, measuredWidth, offsetX, false);
        float ch = Anchor.place(a.minY(), a.maxY(), ay, ah, measuredHeight, offsetY, false);
        setRect(
                Anchor.place(a.minX(), a.maxX(), ax, aw, cw, offsetX, true),
                Anchor.place(a.minY(), a.maxY(), ay, ah, ch, offsetY, true),
                cw,
                ch);
    }
}
