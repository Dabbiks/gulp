package dev.gulp.core.ui;

import dev.gulp.api.Logger;
import dev.gulp.api.Owner;
import dev.gulp.api.audio.Sound;
import dev.gulp.api.data.Preferences;
import dev.gulp.api.entity.component.WorldUi;
import dev.gulp.api.event.Subscription;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.ActionSet;
import dev.gulp.api.input.Cursor;
import dev.gulp.api.input.Gamepad;
import dev.gulp.api.input.GamepadAxis;
import dev.gulp.api.input.GamepadButton;
import dev.gulp.api.input.Input;
import dev.gulp.api.input.KeyboardKey;
import dev.gulp.api.input.Keys;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.input.SystemCursor;
import dev.gulp.api.math.Rect;
import dev.gulp.api.render.Draw;
import dev.gulp.api.spi.UiAccess;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import dev.gulp.api.text.TextStyle;
import dev.gulp.api.ui.Anchor;
import dev.gulp.api.ui.Direction;
import dev.gulp.api.ui.Hud;
import dev.gulp.api.ui.Label;
import dev.gulp.api.ui.Menu;
import dev.gulp.api.ui.MenuPopup;
import dev.gulp.api.ui.MouseFilter;
import dev.gulp.api.ui.Node;
import dev.gulp.api.ui.Overlay;
import dev.gulp.api.ui.OverlayArea;
import dev.gulp.api.ui.Screen;
import dev.gulp.api.ui.ScreenCloseEvent;
import dev.gulp.api.ui.ScreenOpenEvent;
import dev.gulp.api.ui.ScreenTransition;
import dev.gulp.api.ui.Scroll;
import dev.gulp.api.ui.Style;
import dev.gulp.api.ui.Tabs;
import dev.gulp.api.ui.TextField;
import dev.gulp.api.ui.Theme;
import dev.gulp.api.ui.Ui;
import dev.gulp.api.ui.UiAction;
import dev.gulp.api.ui.WidgetState;
import dev.gulp.core.GulpEngine;
import dev.gulp.core.command.ConsoleImpl;
import dev.gulp.core.event.EventBus;
import dev.gulp.core.graphics.ScreenLayers;
import dev.gulp.core.input.InputImpl;
import dev.gulp.core.input.UiInput;
import dev.gulp.core.world.WorldsImpl;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@link Ui}: keeps the layers (world UI, HUD, screens, popups, tooltips, toasts, overlay, console, inspector), lays
 * them out and draws them each frame, and routes input to them before the game: console, popups, the focused node, the
 * node under the pointer. UI actions are read every frame, so menus react at once even in slow motion or pause.
 */
public final class UiImpl implements Ui, UiAccess.Backend, UiInput, ScreenLayers {

    private static final String SCALE_KEY = "gulp.ui.scale";
    private static final float REPEAT_DELAY = 0.4f;
    private static final float REPEAT_EVERY = 0.08f;
    private static final float DRAG_START = 6f;

    /** One open screen. */
    private static final class Entry {
        final Screen screen;
        final Node<?> root;
        float progress;
        boolean closing;

        @Nullable Node<?> lastFocus;

        Entry(Screen screen, Node<?> root) {
            this.screen = screen;
            this.root = root;
        }
    }

    /** One open popup. */
    private record Popup(Node<?> node, Node<?> owner) {}

    /** One HUD node. */
    private record HudNode(Node<?> node, Owner owner) {}

    /** One overlay drawer. */
    private static final class OverlayEntry implements Subscription {
        final Owner owner;
        final Overlay.Drawer drawer;
        boolean active = true;

        OverlayEntry(Owner owner, Overlay.Drawer drawer) {
            this.owner = owner;
            this.drawer = drawer;
        }

        @Override
        public void cancel() {
            active = false;
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }

    /** One notification. */
    private static final class Toast {
        final Node<?> node;
        float left;
        final float total;

        Toast(Node<?> node, float seconds) {
            this.node = node;
            this.left = seconds;
            this.total = seconds;
        }
    }

    private final GulpEngine engine;
    private int seenTextRevision;
    private final InputImpl input;
    private final EventBus events;
    private final WorldsImpl worlds;
    private final ConsoleImpl console;
    private final Logger logger;
    private final boolean development;
    private final UiAccess.Hooks hooks = UiAccess.hooks();
    private final UiAccess.ScreenHooks screenHooks = UiAccess.screens();

    private Theme theme = Theme.DEFAULT;
    private float scale = 1f;
    private boolean pixelPerfect;
    private float time;
    private float width = 1f;
    private float height = 1f;
    private boolean layoutRequested = true;

    private final List<Entry> stack = new ArrayList<>();
    private final List<Entry> closing = new ArrayList<>();
    private final List<Popup> popups = new ArrayList<>();
    private final List<HudNode> hudNodes = new ArrayList<>();
    private boolean hudVisible = true;
    private final List<WorldUi> worldUis = new ArrayList<>();
    private final List<OverlayEntry> overlays = new ArrayList<>();
    private final List<Toast> toasts = new ArrayList<>();

    private @Nullable Node<?> focused;
    private boolean focusVisible;
    private @Nullable Node<?> hovered;
    private @Nullable Node<?> captured;
    private boolean capturedConsumes;
    private float pointerX;
    private float pointerY;
    private float pressX;
    private float pressY;
    private boolean pointerDown;

    private @Nullable Node<?> dragSource;
    private @Nullable Object dragPayload;
    private boolean dragging;
    private boolean padCarry;

    private @Nullable Node<?> tooltipSource;
    private float tooltipTime;
    private @Nullable Node<?> tooltipView;
    private boolean tooltipFromFocus;

    /** The UI actions, copied once: {@code values()} makes a new array on every call. */
    private static final UiAction[] ACTIONS = UiAction.values();

    private final float[] actionStrength = new float[ACTIONS.length];
    private final float[] actionHeld = new float[ACTIONS.length];
    private final float[] actionRepeat = new float[ACTIONS.length];
    private boolean contextButtonDown;

    private boolean pausedByUi;
    private boolean gameplayBlocked;
    private @Nullable Cursor shownCursor;

    private boolean consoleOpen;
    private final TextField consoleField;
    private int historyIndex = -1;
    private final List<TextLayout> consoleLines = new ArrayList<>();
    private int consoleSeen = -1;

    private boolean inspectorOpen;
    private @Nullable Node<?> inspected;
    private @Nullable TextLayout inspectorText;
    private @Nullable Node<?> inspectorFor;

    private final float[] point = new float[2];
    private final Area area = new Area();

    private final Hud hud = new HudImpl();
    private final Overlay overlay = (owner, drawer) -> {
        OverlayEntry entry = new OverlayEntry(owner, drawer);
        overlays.add(entry);
        return entry;
    };

    /**
     * Creates the UI.
     *
     * @param engine the engine
     * @param input the input, which will send raw input here first
     * @param events the event bus
     * @param worlds the worlds, for world UI
     * @param console the console
     * @param logger the engine logger
     * @param development whether the inspector shortcut works
     */
    public UiImpl(
            GulpEngine engine,
            InputImpl input,
            EventBus events,
            WorldsImpl worlds,
            ConsoleImpl console,
            Logger logger,
            boolean development) {
        this.engine = engine;
        this.input = input;
        this.events = events;
        this.worlds = worlds;
        this.console = console;
        this.logger = logger;
        this.development = development;
        this.consoleField = new TextField().placeholder("/help").theme(theme);
        consoleField.onSubmit(line -> {
            console.submit(line.startsWith("/") ? line : "/" + line);
            consoleField.text("");
            historyIndex = -1;
        });
    }

    /** Connects the UI to input and nodes; called when the game starts. */
    public void start() {
        UiAccess.install(this);
        input.setUi(this);
        Preferences preferences = engine.preferences();
        scale = clampScale(preferences.getFloat(SCALE_KEY, 1f));
    }

    /** Closes everything; called when the engine stops. */
    public void dispose() {
        for (int i = stack.size() - 1; i >= 0; i--) {
            Entry entry = stack.get(i);
            hooks.unmount(entry.root);
            screenHooks.close(entry.screen);
        }
        stack.clear();
        for (Entry entry : closing) {
            hooks.unmount(entry.root);
        }
        closing.clear();
        for (Popup popup : popups) {
            hooks.unmount(popup.node());
        }
        popups.clear();
        for (HudNode node : hudNodes) {
            hooks.unmount(node.node());
        }
        hudNodes.clear();
        worldUis.clear();
        overlays.clear();
        toasts.clear();
        input.setUi(null);
        if (pausedByUi) {
            pausedByUi = false;
        }
        UiAccess.install((UiAccess.Backend) null);
    }

    private static float clampScale(float value) {
        return Math.max(0.75f, Math.min(2f, value));
    }

    // ================================================================== Ui: screens

    @Override
    public void open(Screen screen) {
        engine.checkMainThread("Ui.open");
        if (!allowOpen(screen)) {
            return;
        }
        for (int i = stack.size() - 1; i >= 0; i--) {
            closeEntry(stack.get(i), false);
        }
        stack.clear();
        pushEntry(screen);
    }

    @Override
    public void push(Screen screen) {
        engine.checkMainThread("Ui.push");
        if (allowOpen(screen)) {
            pushEntry(screen);
        }
    }

    private boolean allowOpen(Screen screen) {
        if (screen.isOpen()) {
            throw new IllegalStateException("Screen " + screen.id() + " is already open");
        }
        if (events.hasListeners(ScreenOpenEvent.class)) {
            return !events.call(new ScreenOpenEvent(screen)).isCancelled();
        }
        return true;
    }

    private void pushEntry(Screen screen) {
        closePopups();
        Entry top = top();
        if (top != null) {
            top.lastFocus = focused;
        }
        Node<?> root = screenHooks.open(screen);
        Entry entry = new Entry(screen, root);
        entry.progress = screen.enter().seconds() <= 0f ? 1f : 0f;
        stack.add(entry);
        hooks.mount(root);
        layout(root);
        screenHooks.opened(screen);
        Node<?> first = screenHooks.defaultFocus(screen);
        if (first == null) {
            first = firstFocusable(root);
        }
        setFocus(first);
        updateGameState();
    }

    @Override
    public @Nullable Screen pop() {
        engine.checkMainThread("Ui.pop");
        Entry top = top();
        if (top == null) {
            return null;
        }
        stack.remove(stack.size() - 1);
        closeEntry(top, true);
        return top.screen;
    }

    @Override
    public void close(Screen screen) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            Entry entry = stack.get(i);
            if (entry.screen == screen) {
                stack.remove(i);
                closeEntry(entry, i == stack.size());
                return;
            }
        }
    }

    private void closeEntry(Entry entry, boolean restoreFocus) {
        closePopups();
        try {
            screenHooks.close(entry.screen);
        } catch (RuntimeException error) {
            logger.error("Screen.onClose failed in " + entry.screen.id(), error);
        }
        engine.cleanup(entry.screen);
        if (entry.screen.exit().seconds() > 0f && entry.progress > 0f) {
            entry.closing = true;
            closing.add(entry);
        } else {
            hooks.unmount(entry.root);
        }
        if (focused != null && isInside(focused, entry.root)) {
            setFocus(null);
        }
        if (events.hasListeners(ScreenCloseEvent.class)) {
            events.call(new ScreenCloseEvent(entry.screen));
        }
        if (restoreFocus) {
            Entry below = top();
            if (below != null) {
                Node<?> previous = below.lastFocus;
                setFocus(previous != null && previous.isMounted() ? previous : firstFocusable(below.root));
            }
        }
        updateGameState();
    }

    @Override
    public @Nullable Screen current() {
        Entry top = top();
        return top == null ? null : top.screen;
    }

    @Override
    public List<Screen> screens() {
        List<Screen> result = new ArrayList<>();
        for (Entry entry : stack) {
            result.add(entry.screen);
        }
        return result;
    }

    private @Nullable Entry top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    private void updateGameState() {
        boolean pause = false;
        boolean block = false;
        for (Entry entry : stack) {
            pause |= entry.screen.pausesGame();
            block |= entry.screen.blocksGameplayInput();
        }
        if (pause && !pausedByUi && !engine.isPaused()) {
            engine.pause();
            pausedByUi = true;
        } else if (!pause && pausedByUi) {
            pausedByUi = false;
            if (engine.isPaused()) {
                engine.resume();
            }
        }
        if (block && !gameplayBlocked) {
            gameplayBlocked = true;
            input.disable(ActionSet.GAMEPLAY);
        } else if (!block && gameplayBlocked) {
            gameplayBlocked = false;
            input.enable(ActionSet.GAMEPLAY);
        }
    }

    // ================================================================== Ui: layers and settings

    @Override
    public Hud hud() {
        return hud;
    }

    @Override
    public Overlay overlay() {
        return overlay;
    }

    @Override
    public void toast(String message) {
        toast(message, "", 3f);
    }

    @Override
    public void toast(String message, String variant, float seconds) {
        Node<?> node = new ToastPanel(new Label(message).wrap(true).maxSize(320f, Float.POSITIVE_INFINITY))
                .variant(variant)
                .anchor(Anchor.TOP_RIGHT);
        node.alpha(0f);
        toasts.add(new Toast(node, Math.max(0.5f, seconds)));
        hooks.mount(node);
        while (toasts.size() > 5) {
            hooks.unmount(toasts.remove(0).node);
        }
    }

    /** The panel of a toast, styled {@code toast}. */
    private static final class ToastPanel extends dev.gulp.api.ui.Container<ToastPanel> {
        ToastPanel(Node<?> child) {
            super(child);
        }

        @Override
        protected String styleType() {
            return "toast";
        }

        @Override
        protected boolean usesPadding() {
            return true;
        }

        @Override
        protected MouseFilter defaultMouseFilter() {
            return MouseFilter.IGNORE;
        }

        @Override
        protected dev.gulp.api.ui.Size measure() {
            Node<?> child = children().get(0);
            return new dev.gulp.api.ui.Size(child.minWidth(), child.minHeight());
        }

        @Override
        protected void arrange() {
            dev.gulp.api.ui.Insets pad = padding();
            place(
                    children().get(0),
                    x() + pad.left(),
                    y() + pad.top(),
                    width() - pad.horizontal(),
                    height() - pad.vertical());
        }
    }

    @Override
    public float scale() {
        return scale;
    }

    @Override
    public void setScale(float value) {
        float next = clampScale(value);
        if (next != scale) {
            scale = next;
            engine.preferences().set(SCALE_KEY, next);
            requestLayout();
        }
    }

    @Override
    public boolean isPixelPerfect() {
        return pixelPerfect;
    }

    @Override
    public void pixelPerfect(boolean value) {
        this.pixelPerfect = value;
    }

    @Override
    public Theme theme() {
        return theme;
    }

    @Override
    public void setTheme(Theme value) {
        if (value == theme) {
            return;
        }
        theme = value;
        consoleField.theme(value);
        forEachRoot(hooks::restyle);
        requestLayout();
    }

    @Override
    public @Nullable Node<?> focused() {
        return focused;
    }

    @Override
    public void focus(@Nullable Node<?> node) {
        setFocus(node);
    }

    @Override
    public @Nullable Node<?> hovered() {
        return hovered;
    }

    @Override
    public void inspector(boolean open) {
        inspectorOpen = open;
    }

    @Override
    public boolean isInspectorOpen() {
        return inspectorOpen;
    }

    @Override
    public void console(boolean open) {
        boolean next = open && console.isAvailable();
        if (next == consoleOpen) {
            return;
        }
        consoleOpen = next;
        if (next) {
            hooks.mount(consoleField);
            hooks.focus(consoleField, true);
        } else {
            hooks.focus(consoleField, false);
            hooks.unmount(consoleField);
        }
    }

    @Override
    public boolean isConsoleOpen() {
        return consoleOpen;
    }

    // ================================================================== Backend

    @Override
    public TextLayout layout(Text text, TextStyle style, TextBox box) {
        return engine.graphics().layout(text, style, box);
    }

    @Override
    public int textRevision() {
        return engine.translations().revision();
    }

    @Override
    public float time() {
        return time;
    }

    @Override
    public void requestLayout() {
        layoutRequested = true;
    }

    @Override
    public boolean isFocusVisible() {
        return focusVisible;
    }

    @Override
    public void openPopup(Node<?> popup, Node<?> owner, float px, float py, float minWidth) {
        popup.minSize(minWidth, 0f);
        popups.add(new Popup(popup, owner));
        hooks.mount(popup);
        hooks.layout(popup, 0f, 0f, width, height);
        float x = Math.max(0f, Math.min(px, width - popup.minWidth()));
        float y = py + popup.minHeight() > height ? Math.max(0f, py - popup.minHeight() - 4f) : py;
        popup.anchor(Anchor.TOP_LEFT).offset(x, y);
        hooks.layout(popup, 0f, 0f, width, height);
        hideTooltip();
    }

    @Override
    public void closePopup(Node<?> popup) {
        for (int i = popups.size() - 1; i >= 0; i--) {
            Popup entry = popups.get(i);
            if (entry.node() == popup) {
                popups.remove(i);
                hooks.unmount(popup);
                if (focused == null || !focused.isMounted() || isInside(focused, popup)) {
                    setFocus(entry.owner().isMounted() ? entry.owner() : null);
                }
                return;
            }
        }
    }

    private void closePopups() {
        for (int i = popups.size() - 1; i >= 0; i--) {
            closePopup(popups.get(i).node());
        }
    }

    @Override
    public void play(@Nullable Sound sound) {
        if (sound != null) {
            engine.audio().play(sound);
        }
    }

    @Override
    public Input input() {
        return input;
    }

    @Override
    public void startTextInput(Rect area) {
        input.startTextInput(new Rect(area.x() * scale, area.y() * scale, area.width() * scale, area.height() * scale));
    }

    @Override
    public void stopTextInput() {
        input.stopTextInput();
    }

    @Override
    public void worldUi(WorldUi component, boolean attached) {
        if (attached) {
            if (!worldUis.contains(component)) {
                worldUis.add(component);
                component.node().anchor(Anchor.BOTTOM);
                hooks.mount(component.node());
            }
        } else if (worldUis.remove(component)) {
            hooks.unmount(component.node());
        }
    }

    // ================================================================== focus

    private void setFocus(@Nullable Node<?> node) {
        Node<?> target = node != null && node.isMounted() ? node : null;
        if (target == focused) {
            return;
        }
        Node<?> previous = focused;
        focused = target;
        if (previous != null) {
            hooks.focus(previous, false);
        }
        if (target != null) {
            hooks.focus(target, true);
            for (Node<?> p = target.parent(); p != null; p = p.parent()) {
                if (p instanceof Scroll scroll) {
                    scroll.scrollTo(target);
                }
            }
            if (focusVisible) {
                play(theme.resolve("button", "", WidgetState.NORMAL).hoverSound());
            }
        }
        hideTooltip();
    }

    private static boolean isInside(Node<?> node, Node<?> root) {
        for (Node<?> n = node; n != null; n = n.parent()) {
            if (n == root) {
                return true;
            }
        }
        return false;
    }

    private @Nullable Node<?> focusScope() {
        if (!popups.isEmpty()) {
            return popups.get(popups.size() - 1).node();
        }
        Entry top = top();
        return top != null ? top.root : null;
    }

    private static @Nullable Node<?> firstFocusable(Node<?> root) {
        if (!root.isVisible()) {
            return null;
        }
        if (root.isFocusable()) {
            return root;
        }
        List<Node<?>> children = root.children();
        for (int i = 0; i < children.size(); i++) {
            Node<?> found = firstFocusable(children.get(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void collectFocusable(Node<?> node, List<Node<?>> into) {
        if (!node.isVisible() || node.width() <= 0f) {
            return;
        }
        if (node.isFocusable()) {
            into.add(node);
        }
        List<Node<?>> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            collectFocusable(children.get(i), into);
        }
    }

    private List<Node<?>> focusCandidates() {
        List<Node<?>> candidates = new ArrayList<>();
        Node<?> scope = focusScope();
        if (scope != null) {
            collectFocusable(scope, candidates);
        } else if (hudVisible) {
            for (HudNode node : hudNodes) {
                collectFocusable(node.node(), candidates);
            }
        }
        return candidates;
    }

    private void moveFocus(Direction direction) {
        Node<?> current = focused;
        if (current == null) {
            List<Node<?>> candidates = focusCandidates();
            if (!candidates.isEmpty()) {
                setFocus(candidates.get(0));
            }
            return;
        }
        Node<?> manual = hooks.focusNeighbor(current, direction);
        if (manual != null && manual.isFocusable() && manual.isMounted()) {
            setFocus(manual);
            return;
        }
        float cx = current.x() + current.width() / 2f;
        float cy = current.y() + current.height() / 2f;
        Node<?> best = null;
        float bestScore = Float.MAX_VALUE;
        for (Node<?> candidate : focusCandidates()) {
            if (candidate == current) {
                continue;
            }
            float dx = candidate.x() + candidate.width() / 2f - cx;
            float dy = candidate.y() + candidate.height() / 2f - cy;
            float along = dx * direction.dx() + dy * direction.dy();
            // Must be beyond the current node's edge in that direction (with a little overlap allowed).
            float edge = (direction.dx() != 0 ? current.width() : current.height()) / 2f;
            float extent = (direction.dx() != 0 ? candidate.width() : candidate.height()) / 2f;
            if (along <= Math.max(1f, edge + extent - 4f) * 0.5f) {
                continue;
            }
            float across = Math.abs(direction.dx() != 0 ? dy : dx);
            float overlap = direction.dx() != 0
                    ? Math.min(current.y() + current.height(), candidate.y() + candidate.height())
                            - Math.max(current.y(), candidate.y())
                    : Math.min(current.x() + current.width(), candidate.x() + candidate.width())
                            - Math.max(current.x(), candidate.x());
            float score = along + across * (overlap > 0f ? 0.5f : 3f);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best != null) {
            setFocus(best);
        }
    }

    private void focusInOrder(boolean forward) {
        List<Node<?>> candidates = focusCandidates();
        if (candidates.isEmpty()) {
            return;
        }
        int index = focused == null ? -1 : candidates.indexOf(focused);
        int next = forward ? index + 1 : (index < 0 ? candidates.size() - 1 : index - 1);
        next = (next + candidates.size()) % candidates.size();
        focusVisible = true;
        setFocus(candidates.get(next));
    }

    private boolean isUiActive() {
        return !stack.isEmpty() || !popups.isEmpty() || (focused != null && focused.isMounted()) || padCarry;
    }

    // ================================================================== frame

    /**
     * Advances the UI: actions, hover, animations, transitions, notifications and layout. Called every frame.
     *
     * @param seconds real seconds since the last frame
     */
    public void frame(float seconds) {
        time += seconds;
        int revision = textRevision();
        if (revision != seenTextRevision) {
            // Translated text reads differently now: every widget measures again.
            seenTextRevision = revision;
            forEachRoot(hooks::restyle);
            requestLayout();
        }
        width = Math.max(1f, engine.display().width() / scale);
        height = Math.max(1f, engine.display().height() / scale);
        dropDisabledOwners();
        if (focused != null && !focused.isMounted()) {
            setFocus(null);
        }
        advanceScreens(seconds);
        readActions(seconds);
        readGamepadExtras(seconds);
        updateHover();
        updateTooltip(seconds);
        updateToasts(seconds);
        updateRoots(seconds);
        if (consoleOpen) {
            hooks.update(consoleField, seconds);
        }
        layoutAll();
        input.clearFrameLatches();
    }

    private void dropDisabledOwners() {
        for (int i = hudNodes.size() - 1; i >= 0; i--) {
            HudNode node = hudNodes.get(i);
            if (!node.owner().isEnabled()) {
                hudNodes.remove(i);
                hooks.unmount(node.node());
            }
        }
        for (int i = overlays.size() - 1; i >= 0; i--) {
            OverlayEntry entry = overlays.get(i);
            if (!entry.active || !entry.owner.isEnabled()) {
                overlays.remove(i);
            }
        }
    }

    private void advanceScreens(float seconds) {
        for (int i = 0; i < stack.size(); i++) {
            Entry entry = stack.get(i);
            if (entry.progress < 1f) {
                float length = entry.screen.enter().seconds();
                entry.progress = length <= 0f ? 1f : Math.min(1f, entry.progress + seconds / length);
            }
        }
        for (int i = closing.size() - 1; i >= 0; i--) {
            Entry entry = closing.get(i);
            float length = entry.screen.exit().seconds();
            entry.progress = length <= 0f ? 0f : entry.progress - seconds / length;
            if (entry.progress <= 0f) {
                closing.remove(i);
                hooks.unmount(entry.root);
            }
        }
    }

    private void readActions(float seconds) {
        UiAction[] actions = ACTIONS;
        boolean blocked = consoleOpen || input.bindings().isCapturing();
        for (int i = 0; i < actions.length; i++) {
            UiAction action = actions[i];
            float strength = input.frameStrength(action.action());
            boolean down = strength >= 0.5f;
            boolean wasDown = actionStrength[i] >= 0.5f;
            actionStrength[i] = strength;
            boolean fire = down && !wasDown;
            if (down && action.direction() != null) {
                actionHeld[i] += seconds;
                if (actionHeld[i] > REPEAT_DELAY) {
                    actionRepeat[i] += seconds;
                    if (actionRepeat[i] >= REPEAT_EVERY) {
                        actionRepeat[i] = 0f;
                        fire = true;
                    }
                }
            } else if (!down) {
                actionHeld[i] = 0f;
                actionRepeat[i] = 0f;
            }
            if (fire && !blocked && isUiActive()) {
                focusVisible = true;
                handleAction(action);
                input.suppressHeld(action.action());
            }
        }
    }

    private void handleAction(UiAction action) {
        Node<?> current = focused;
        if (padCarry) {
            if (action == UiAction.ACCEPT) {
                if (current != null && dragPayload != null && hooks.acceptsDrop(current, dragPayload)) {
                    hooks.drop(current, dragPayload);
                }
                endDrag();
                return;
            }
            if (action == UiAction.CANCEL) {
                endDrag();
                return;
            }
        }
        if (current != null && current.isMounted() && hooks.navigate(current, action)) {
            return;
        }
        if (action == UiAction.ACCEPT && current != null && hooks.dragPayload(current) != null) {
            dragSource = current;
            dragPayload = hooks.dragPayload(current);
            padCarry = true;
            return;
        }
        Direction direction = action.direction();
        if (direction != null) {
            moveFocus(direction);
            return;
        }
        switch (action) {
            case NEXT_TAB, PREV_TAB -> {
                Tabs tabs = nearestTabs();
                if (tabs != null) {
                    if (action == UiAction.NEXT_TAB) {
                        tabs.next();
                    } else {
                        tabs.previous();
                    }
                }
            }
            case CANCEL -> back();
            default -> {}
        }
    }

    private @Nullable Tabs nearestTabs() {
        for (Node<?> n = focused; n != null; n = n.parent()) {
            if (n instanceof Tabs tabs) {
                return tabs;
            }
        }
        Node<?> scope = focusScope();
        if (scope != null) {
            List<Tabs> all = scope.findAll(Tabs.class);
            return all.isEmpty() ? null : all.get(0);
        }
        return null;
    }

    private void back() {
        if (!popups.isEmpty()) {
            closePopup(popups.get(popups.size() - 1).node());
            return;
        }
        Entry top = top();
        if (top != null) {
            screenHooks.back(top.screen);
        } else if (focused != null) {
            setFocus(null);
        }
    }

    private void readGamepadExtras(float seconds) {
        boolean down = false;
        float scrollY = 0f;
        for (int slot = 0; slot < InputImpl.GAMEPADS; slot++) {
            Gamepad pad = input.gamepad(slot);
            if (pad.isConnected()) {
                down |= pad.isDown(GamepadButton.WEST);
                float axis = pad.axis(GamepadAxis.RIGHT_Y);
                if (Math.abs(axis) > Math.abs(scrollY)) {
                    scrollY = axis;
                }
            }
        }
        if (down && !contextButtonDown && focused != null && isUiActive()) {
            openContextMenu(focused, focused.x(), focused.y() + focused.height());
        }
        contextButtonDown = down;
        if (scrollY != 0f && isUiActive()) {
            Node<?> start = hovered != null ? hovered : focused;
            for (Node<?> n = start; n != null; n = n.parent()) {
                if (n instanceof Scroll scroll && scroll.canScroll(false)) {
                    scroll.scrollBy(0f, scrollY * 600f * seconds);
                    break;
                }
            }
        }
    }

    private boolean openContextMenu(Node<?> from, float x, float y) {
        for (Node<?> n = from; n != null; n = n.parent()) {
            Menu menu = hooks.contextMenu(n);
            if (menu != null) {
                new MenuPopup(menu).open(n, x, y);
                return true;
            }
        }
        return false;
    }

    private void updateHover() {
        Node<?> next = pointerDown && captured != null ? captured : hit(pointerX, pointerY);
        if (next != hovered) {
            Node<?> previous = hovered;
            hovered = next;
            if (previous != null && previous.isMounted()) {
                hooks.hover(previous, false);
            }
            if (next != null) {
                hooks.hover(next, true);
            }
        }
        Cursor wanted = SystemCursor.ARROW;
        for (Node<?> n = hovered; n != null; n = n.parent()) {
            Cursor own = n.cursor();
            if (own != null && n.isEnabled()) {
                wanted = own;
                break;
            }
            if (n.mouseFilter() == MouseFilter.STOP && !(n instanceof Scroll)) {
                break;
            }
        }
        if (hovered == null) {
            wanted = null;
        }
        if (wanted != shownCursor) {
            if (wanted != null || shownCursor != null) {
                input.setCursor(wanted != null ? wanted : SystemCursor.ARROW);
            }
            shownCursor = wanted;
        }
    }

    private void updateTooltip(float seconds) {
        Node<?> source = null;
        boolean fromFocus = false;
        if (!pointerDown && !dragging) {
            for (Node<?> n = hovered; n != null; n = n.parent()) {
                if (hooks.tooltip(n) != null) {
                    source = n;
                    break;
                }
            }
            if (source == null && focusVisible && focused != null && hooks.tooltip(focused) != null) {
                source = focused;
                fromFocus = true;
            }
        }
        if (source != tooltipSource) {
            hideTooltip();
            tooltipSource = source;
            tooltipFromFocus = fromFocus;
            tooltipTime = 0f;
        }
        if (source != null && tooltipView == null) {
            tooltipTime += seconds;
            if (tooltipTime >= theme.tooltipDelay()) {
                showTooltip(source);
            }
        }
    }

    private void showTooltip(Node<?> source) {
        Object content = hooks.tooltip(source);
        Style tip = theme.resolve("tooltip", "", WidgetState.NORMAL);
        Node<?> body = content instanceof Node<?> node
                ? node
                : new Label(String.valueOf(content))
                        .wrap(true)
                        .maxSize(280f, Float.POSITIVE_INFINITY)
                        .style(s -> s.fontSize(tip.fontSize()).textColor(tip.textColor()));
        Node<?> view = new TooltipPanel(body);
        tooltipView = view;
        hooks.mount(view);
        hooks.layout(view, 0f, 0f, width, height);
        float x = tooltipFromFocus ? source.x() : pointerX + 12f;
        float y = tooltipFromFocus ? source.y() + source.height() + 4f : pointerY + 18f;
        x = Math.max(0f, Math.min(x, width - view.minWidth()));
        if (y + view.minHeight() > height) {
            y = Math.max(0f, (tooltipFromFocus ? source.y() : pointerY) - view.minHeight() - 4f);
        }
        view.anchor(Anchor.TOP_LEFT).offset(x, y);
    }

    private void hideTooltip() {
        Node<?> view = tooltipView;
        if (view != null) {
            hooks.unmount(view);
            tooltipView = null;
        }
        tooltipSource = null;
        tooltipTime = 0f;
    }

    /** The panel of a tooltip, styled {@code tooltip}. */
    private static final class TooltipPanel extends dev.gulp.api.ui.Container<TooltipPanel> {
        TooltipPanel(Node<?> child) {
            super(child);
        }

        @Override
        protected String styleType() {
            return "tooltip";
        }

        @Override
        protected boolean usesPadding() {
            return true;
        }

        @Override
        protected dev.gulp.api.ui.Size measure() {
            Node<?> child = children().get(0);
            return new dev.gulp.api.ui.Size(child.minWidth(), child.minHeight());
        }

        @Override
        protected void arrange() {
            dev.gulp.api.ui.Insets pad = padding();
            place(
                    children().get(0),
                    x() + pad.left(),
                    y() + pad.top(),
                    width() - pad.horizontal(),
                    height() - pad.vertical());
        }
    }

    private void updateToasts(float seconds) {
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast toast = toasts.get(i);
            toast.left -= seconds;
            float shown = toast.total - toast.left;
            float alpha = Math.min(1f, Math.min(shown / 0.2f, toast.left / 0.3f));
            toast.node.alpha(Math.max(0f, alpha));
            if (toast.left <= 0f) {
                toasts.remove(i);
                hooks.unmount(toast.node);
            }
        }
    }

    private void updateRoots(float seconds) {
        for (int i = 0; i < worldUis.size(); i++) {
            hooks.update(worldUis.get(i).node(), seconds);
        }
        for (int i = 0; i < hudNodes.size(); i++) {
            hooks.update(hudNodes.get(i).node(), seconds);
        }
        for (int i = 0; i < stack.size(); i++) {
            hooks.update(stack.get(i).root, seconds);
        }
        for (int i = 0; i < closing.size(); i++) {
            hooks.update(closing.get(i).root, seconds);
        }
        for (int i = 0; i < popups.size(); i++) {
            hooks.update(popups.get(i).node(), seconds);
        }
    }

    private interface RootVisitor {
        void visit(Node<?> root);
    }

    private void forEachRoot(RootVisitor visitor) {
        for (int i = 0; i < worldUis.size(); i++) {
            visitor.visit(worldUis.get(i).node());
        }
        for (int i = 0; i < hudNodes.size(); i++) {
            visitor.visit(hudNodes.get(i).node());
        }
        for (int i = 0; i < stack.size(); i++) {
            visitor.visit(stack.get(i).root);
        }
        for (int i = 0; i < closing.size(); i++) {
            visitor.visit(closing.get(i).root);
        }
        for (int i = 0; i < popups.size(); i++) {
            visitor.visit(popups.get(i).node());
        }
        for (int i = 0; i < toasts.size(); i++) {
            visitor.visit(toasts.get(i).node);
        }
        Node<?> tip = tooltipView;
        if (tip != null) {
            visitor.visit(tip);
        }
    }

    private void layout(Node<?> root) {
        for (int pass = 0; pass < 4; pass++) {
            if (!hooks.layout(root, 0f, 0f, width, height)) {
                return;
            }
        }
    }

    private void layoutAll() {
        float lastWidth = area.width;
        float lastHeight = area.height;
        area.width = width;
        area.height = height;
        boolean resized = lastWidth != width || lastHeight != height;
        if (!layoutRequested && !resized) {
            return;
        }
        layoutRequested = false;
        for (int i = 0; i < hudNodes.size(); i++) {
            layout(hudNodes.get(i).node());
        }
        for (int i = 0; i < stack.size(); i++) {
            layout(stack.get(i).root);
        }
        for (int i = 0; i < closing.size(); i++) {
            layout(closing.get(i).root);
        }
        for (int i = 0; i < popups.size(); i++) {
            layout(popups.get(i).node());
        }
        float y = 16f;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Node<?> node = toasts.get(i).node;
            node.offset(16f, y);
            layout(node);
            y += node.height() + 8f;
        }
        Node<?> tip = tooltipView;
        if (tip != null) {
            layout(tip);
        }
        if (consoleOpen) {
            layout(consoleField);
        }
        if (!toasts.isEmpty() || !worldUis.isEmpty()) {
            layoutRequested = true;
        }
    }

    // ================================================================== hit testing

    private @Nullable Node<?> hit(float px, float py) {
        for (int i = popups.size() - 1; i >= 0; i--) {
            Node<?> found = hitNode(popups.get(i).node(), px, py);
            if (found != null) {
                return found;
            }
        }
        Entry top = top();
        if (top != null && !top.closing) {
            Node<?> found = hitNode(top.root, px, py);
            if (found != null || top.screen.blocksGameplayInput()) {
                return found;
            }
        }
        if (hudVisible) {
            for (int i = hudNodes.size() - 1; i >= 0; i--) {
                Node<?> found = hitNode(hudNodes.get(i).node(), px, py);
                if (found != null) {
                    return found;
                }
            }
        }
        for (int i = worldUis.size() - 1; i >= 0; i--) {
            WorldUi component = worldUis.get(i);
            Node<?> found = component.node().width() > 0f ? hitNode(component.node(), px, py) : null;
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static @Nullable Node<?> hitNode(Node<?> node, float px, float py) {
        if (!node.isVisible()) {
            return null;
        }
        boolean inside = node.contains(px, py);
        if (node.clipsChildren() && !inside) {
            return null;
        }
        List<Node<?>> children = node.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            Node<?> found = hitNode(children.get(i), px, py);
            if (found != null) {
                return found;
            }
        }
        return inside && node.mouseFilter() != MouseFilter.IGNORE ? node : null;
    }

    private static @Nullable Node<?> deepest(Node<?> node, float px, float py) {
        if (!node.isVisible() || !node.contains(px, py)) {
            return null;
        }
        List<Node<?>> children = node.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            Node<?> found = deepest(children.get(i), px, py);
            if (found != null) {
                return found;
            }
        }
        return node;
    }

    private boolean blocksWorld() {
        Entry top = top();
        return (top != null && top.screen.blocksGameplayInput()) || !popups.isEmpty();
    }

    // ================================================================== raw input (UiInput)

    @Override
    public boolean keyPressed(KeyboardKey key, int modifiers, boolean repeat) {
        if (key == Keys.GRAVE && !repeat && console.isAvailable()) {
            console(!consoleOpen);
            return true;
        }
        if (consoleOpen) {
            consoleKey(key, modifiers, repeat);
            return true;
        }
        if (key == Keys.F12 && development && !repeat) {
            inspectorOpen = !inspectorOpen;
            return true;
        }
        if (key == Keys.TAB && isUiActive()) {
            focusInOrder((modifiers & 1) == 0);
            return true;
        }
        Node<?> current = focused;
        if (current != null && current.isMounted() && hooks.key(current, key, modifiers, repeat)) {
            return true;
        }
        // Keys of UI actions belong to the UI while it is active, so the game does not also react to them.
        return isUiActive() && isUiKey(key);
    }

    private boolean isUiKey(KeyboardKey key) {
        for (UiAction action : ACTIONS) {
            for (dev.gulp.api.input.Binding binding : input.bindings().of(action.action())) {
                if (key.equals(binding)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void consoleKey(KeyboardKey key, int modifiers, boolean repeat) {
        if (key == Keys.ESCAPE) {
            console(false);
            return;
        }
        List<String> history = console.history();
        if ((key == Keys.UP || key == Keys.DOWN) && !history.isEmpty()) {
            if (key == Keys.UP) {
                historyIndex = historyIndex < 0 ? history.size() - 1 : Math.max(0, historyIndex - 1);
            } else {
                historyIndex = historyIndex < 0 ? -1 : historyIndex + 1;
                if (historyIndex >= history.size()) {
                    historyIndex = -1;
                }
            }
            consoleField.text(historyIndex < 0 ? "" : history.get(historyIndex));
            return;
        }
        if (key == Keys.TAB) {
            String typed = consoleField.text();
            List<String> options = console.complete(typed.startsWith("/") ? typed : "/" + typed);
            if (options.size() == 1) {
                consoleField.text(options.get(0) + " ");
            } else if (options.size() > 1) {
                console.print(String.join("  ", options));
            }
            return;
        }
        hooks.key(consoleField, key, modifiers, repeat);
    }

    @Override
    public boolean charTyped(int codePoint) {
        if (consoleOpen) {
            if (codePoint != '`' && codePoint != '~') {
                hooks.text(consoleField, codePoint);
            }
            return true;
        }
        Node<?> current = focused;
        return current != null && current.isMounted() && hooks.text(current, codePoint);
    }

    @Override
    public boolean mouseButton(MouseButton button, boolean down, float x, float y) {
        pointerX = x / scale;
        pointerY = y / scale;
        focusVisible = false;
        if (down) {
            return pointerPressed(button);
        }
        return pointerReleased(button);
    }

    private boolean pointerPressed(MouseButton button) {
        pointerDown = true;
        pressX = pointerX;
        pressY = pointerY;
        hideTooltip();
        if (!popups.isEmpty()) {
            Node<?> inPopup = null;
            for (int i = popups.size() - 1; i >= 0 && inPopup == null; i--) {
                inPopup = hitNode(popups.get(i).node(), pointerX, pointerY);
            }
            if (inPopup == null) {
                closePopups();
                return true;
            }
        }
        Node<?> target = hit(pointerX, pointerY);
        if (target == null) {
            if (focused != null && !blocksWorld()) {
                setFocus(null);
            }
            return blocksWorld();
        }
        if (button == MouseButton.RIGHT && openContextMenu(target, pointerX, pointerY)) {
            return true;
        }
        Node<?> focusTarget = null;
        for (Node<?> n = target; n != null; n = n.parent()) {
            if (n.isFocusable()) {
                focusTarget = n;
                break;
            }
        }
        if (focusTarget != null && focusTarget != focused) {
            setFocus(focusTarget);
        }
        for (Node<?> n = target; n != null; n = n.parent()) {
            Object payload = hooks.dragPayload(n);
            if (payload != null && button == MouseButton.LEFT && n.isEnabled()) {
                dragSource = n;
                dragPayload = payload;
                captured = n;
                capturedConsumes = true;
                hooks.pointerDown(n, pointerX, pointerY, button);
                return true;
            }
            if (hooks.pointerDown(n, pointerX, pointerY, button)) {
                captured = n;
                capturedConsumes = true;
                return true;
            }
            if (n.mouseFilter() == MouseFilter.STOP) {
                break;
            }
        }
        return target.mouseFilter() == MouseFilter.STOP || blocksWorld();
    }

    private boolean pointerReleased(MouseButton button) {
        pointerDown = false;
        Node<?> owner = captured;
        captured = null;
        boolean consumed = capturedConsumes;
        capturedConsumes = false;
        if (dragging) {
            Object payload = dragPayload;
            Node<?> target = hit(pointerX, pointerY);
            for (Node<?> n = target; n != null && payload != null; n = n.parent()) {
                if (hooks.acceptsDrop(n, payload)) {
                    hooks.drop(n, payload);
                    break;
                }
            }
            if (owner != null) {
                hooks.pointerUp(owner, pointerX, pointerY, false);
            }
            endDrag();
            return true;
        }
        if (!padCarry) {
            dragSource = null;
            dragPayload = null;
        }
        if (owner != null) {
            hooks.pointerUp(owner, pointerX, pointerY, owner.isMounted() && owner.contains(pointerX, pointerY));
            return consumed;
        }
        return hit(pointerX, pointerY) != null || blocksWorld();
    }

    private void endDrag() {
        dragging = false;
        padCarry = false;
        dragSource = null;
        dragPayload = null;
    }

    @Override
    public boolean scrolled(float dx, float dy) {
        Node<?> target = hit(pointerX, pointerY);
        for (Node<?> n = target; n != null; n = n.parent()) {
            if (hooks.scroll(n, dx, dy)) {
                return true;
            }
        }
        return target != null || blocksWorld();
    }

    @Override
    public void pointerMoved(float x, float y) {
        pointerX = x / scale;
        pointerY = y / scale;
        Node<?> owner = captured;
        if (owner == null) {
            return;
        }
        float moved = Math.abs(pointerX - pressX) + Math.abs(pointerY - pressY);
        if (dragSource != null && !dragging && moved > DRAG_START) {
            dragging = true;
            hooks.pointerUp(owner, pointerX, pointerY, false);
            return;
        }
        if (dragging) {
            return;
        }
        if (!(owner instanceof Scroll) && moved > DRAG_START * 1.5f) {
            // A press that turns into a drag over a scrollable area scrolls it instead of pressing the child.
            boolean vertical = Math.abs(pointerY - pressY) > Math.abs(pointerX - pressX);
            for (Node<?> n = owner.parent(); n != null; n = n.parent()) {
                if (n instanceof Scroll scroll && scroll.canScroll(!vertical)) {
                    hooks.pointerUp(owner, pointerX, pointerY, false);
                    scroll.beginDrag(pressX, pressY);
                    captured = scroll;
                    hooks.pointerDrag(scroll, pointerX, pointerY);
                    return;
                }
            }
        }
        hooks.pointerDrag(owner, pointerX, pointerY);
    }

    // ================================================================== drawing

    @Override
    public void drawUi(Draw draw) {
        draw.push();
        draw.scale(scale, scale);
        float alpha = engine.alpha();
        for (int i = 0; i < worldUis.size(); i++) {
            drawWorldUi(draw, worldUis.get(i), alpha);
        }
        if (hudVisible) {
            for (int i = 0; i < hudNodes.size(); i++) {
                hooks.draw(hudNodes.get(i).node(), draw, 1f);
            }
        }
        draw.pop();
    }

    private void drawScreens(Draw draw) {
        for (int i = 0; i < stack.size(); i++) {
            drawScreen(draw, stack.get(i));
        }
        for (int i = 0; i < closing.size(); i++) {
            drawScreen(draw, closing.get(i));
        }
        for (int i = 0; i < popups.size(); i++) {
            hooks.draw(popups.get(i).node(), draw, 1f);
        }
        drawFocusFrame(draw);
        drawDrag(draw);
        Node<?> tip = tooltipView;
        if (tip != null) {
            hooks.draw(tip, draw, 1f);
        }
        for (int i = 0; i < toasts.size(); i++) {
            hooks.draw(toasts.get(i).node, draw, 1f);
        }
    }

    private void drawWorldUi(Draw draw, WorldUi component, float alpha) {
        Node<?> node = component.node();
        if (!worlds.screenPoint(
                component.entity(), component.offset().x(), component.offset().y(), alpha, point)) {
            node.visible(false);
            return;
        }
        float sx = point[0] / scale;
        float sy = point[1] / scale;
        boolean onScreen = sx > -200f && sy > -200f && sx < width + 200f && sy < height + 200f;
        node.visible(onScreen);
        if (!onScreen) {
            return;
        }
        hooks.layout(node, sx, sy, 0f, 0f);
        if (component.scalesWithZoom()) {
            float zoom = worlds.activeZoom();
            draw.push();
            draw.translate(sx, sy);
            draw.scale(zoom, zoom);
            draw.translate(-sx, -sy);
            hooks.draw(node, draw, 1f);
            draw.pop();
        } else {
            hooks.draw(node, draw, 1f);
        }
    }

    private void drawScreen(Draw draw, Entry entry) {
        float progress = entry.closing ? Math.max(0f, entry.progress) : entry.progress;
        ScreenTransition transition = entry.closing ? entry.screen.exit() : entry.screen.enter();
        float alpha = transition.alphaAt(progress);
        if (entry.screen.dimBackground()) {
            Color previous = draw.color();
            draw.alpha(alpha);
            theme.resolve("screen", "", WidgetState.NORMAL).background().draw(draw, 0f, 0f, width, height);
            draw.alpha(1f);
            draw.color(previous);
        }
        float s = transition.scaleAt(progress);
        float dy = transition.offsetYAt(progress, height);
        boolean moved = s != 1f || dy != 0f;
        if (moved) {
            draw.push();
            draw.translate(width / 2f, height / 2f + dy);
            draw.scale(s, s);
            draw.translate(-width / 2f, -height / 2f);
        }
        hooks.draw(entry.root, draw, alpha);
        if (moved) {
            draw.pop();
        }
    }

    private void drawFocusFrame(Draw draw) {
        Node<?> node = focused;
        if (!focusVisible || node == null || !node.isMounted() || !node.isVisible() || isTransitioning(node)) {
            return;
        }
        Style base = theme.resolve("", "", WidgetState.NORMAL);
        float w = base.focusWidth();
        Color previous = draw.color();
        draw.color(padCarry ? base.accent().lerp(Color.WHITE, 0.4f) : base.focusColor());
        draw.alpha(1f);
        draw.rectOutline(
                node.x() - w - 1f, node.y() - w - 1f, node.width() + 2f * w + 2f, node.height() + 2f * w + 2f, w);
        draw.color(previous);
    }

    private boolean isTransitioning(Node<?> node) {
        for (Entry entry : stack) {
            if (entry.progress < 1f && isInside(node, entry.root)) {
                return true;
            }
        }
        return false;
    }

    private void drawDrag(Draw draw) {
        Node<?> source = dragSource;
        if (source == null || (!dragging && !padCarry) || !source.isMounted()) {
            return;
        }
        float tx = dragging ? pointerX - source.width() / 2f : source.x() + 6f;
        float ty = dragging ? pointerY - source.height() / 2f : source.y() - 6f;
        Node<?> focusNode = focused;
        if (padCarry && focusNode != null) {
            tx = focusNode.x() + 6f;
            ty = focusNode.y() - 6f;
        }
        Object payload = dragPayload;
        Node<?> target = dragging ? hit(pointerX, pointerY) : focusNode;
        if (payload != null && target != null) {
            for (Node<?> n = target; n != null; n = n.parent()) {
                if (hooks.acceptsDrop(n, payload)) {
                    Color previous = draw.color();
                    draw.color(theme.resolve("", "", WidgetState.NORMAL).accent());
                    draw.rectOutline(n.x() - 2f, n.y() - 2f, n.width() + 4f, n.height() + 4f, 2f);
                    draw.color(previous);
                    break;
                }
            }
        }
        draw.push();
        draw.translate(tx - source.x(), ty - source.y());
        hooks.draw(source, draw, 0.75f);
        draw.pop();
    }

    @Override
    public void drawOverlay(Draw draw) {
        draw.push();
        draw.scale(scale, scale);
        for (int i = 0; i < overlays.size(); i++) {
            OverlayEntry entry = overlays.get(i);
            if (entry.active) {
                try {
                    entry.drawer.draw(draw, area);
                } catch (RuntimeException error) {
                    entry.active = false;
                    logger.error("Overlay drawer of " + entry.owner.id() + " failed and was removed", error);
                }
            }
        }
        drawScreens(draw);
        if (inspectorOpen) {
            drawInspector(draw);
        }
        engine.debug().drawOverlay(draw, width, height);
        if (consoleOpen) {
            drawConsole(draw);
        }
        draw.pop();
    }

    private void drawConsole(Draw draw) {
        Style style = theme.resolve("console", "", WidgetState.NORMAL);
        float panelHeight = Math.min(height * 0.45f, 320f);
        style.background().draw(draw, 0f, 0f, width, panelHeight);
        List<String> output = console.output();
        if (output.size() != consoleSeen || consoleLines.isEmpty() != output.isEmpty()) {
            consoleSeen = output.size();
            consoleLines.clear();
            TextStyle text = style.textStyle();
            int from = Math.max(0, output.size() - 40);
            for (int i = from; i < output.size(); i++) {
                consoleLines.add(layout(Text.of(output.get(i)), text, TextBox.NONE));
            }
        }
        float pad = style.padding().left();
        float fieldHeight = consoleField.minHeight() > 0f ? consoleField.minHeight() : 30f;
        float y = panelHeight - fieldHeight - pad;
        for (int i = consoleLines.size() - 1; i >= 0 && y > 0f; i--) {
            TextLayout line = consoleLines.get(i);
            y -= line.height();
            draw.text(line, pad, y);
        }
        hooks.layout(consoleField, pad, panelHeight - fieldHeight - pad / 2f, width - 2f * pad, fieldHeight);
        hooks.draw(consoleField, draw, 1f);
    }

    private void drawInspector(Draw draw) {
        Style style = theme.resolve("inspector", "", WidgetState.NORMAL);
        Color previous = draw.color();
        draw.color(style.accent().withAlpha(0.35f));
        forEachRoot(root -> outline(draw, root));
        Node<?> target = null;
        for (int i = popups.size() - 1; i >= 0 && target == null; i--) {
            target = deepest(popups.get(i).node(), pointerX, pointerY);
        }
        Entry top = top();
        if (target == null && top != null) {
            target = deepest(top.root, pointerX, pointerY);
        }
        for (int i = hudNodes.size() - 1; i >= 0 && target == null; i--) {
            target = deepest(hudNodes.get(i).node(), pointerX, pointerY);
        }
        inspected = target;
        if (target != null) {
            draw.color(style.accent());
            draw.rectOutline(target.x(), target.y(), target.width(), target.height(), 2f);
            if (inspectorFor != target || inspectorText == null) {
                inspectorFor = target;
                inspectorText = layout(Text.of(describe(target)), style.textStyle(), TextBox.width(320f));
            }
            TextLayout text = inspectorText;
            if (text != null) {
                float pad = style.padding().left();
                float bw = text.width() + 2f * pad;
                float bh = text.height() + 2f * pad;
                float bx = Math.min(pointerX + 16f, width - bw);
                float by = pointerY + 16f + bh > height ? Math.max(0f, pointerY - bh - 8f) : pointerY + 16f;
                style.background().draw(draw, bx, by, bw, bh);
                draw.text(text, bx + pad, by + pad);
            }
        }
        draw.color(previous);
    }

    private static void outline(Draw draw, Node<?> node) {
        if (!node.isVisible() || node.width() <= 0f) {
            return;
        }
        draw.rectOutline(node.x(), node.y(), node.width(), node.height(), 1f);
        List<Node<?>> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            outline(draw, children.get(i));
        }
    }

    private static String describe(Node<?> node) {
        StringBuilder text = new StringBuilder();
        text.append(
                node.getClass().getSimpleName().isEmpty()
                        ? "Node"
                        : node.getClass().getSimpleName());
        if (node.id() != null) {
            text.append(" #").append(node.id());
        }
        if (!node.variant().isEmpty()) {
            text.append(" .").append(node.variant());
        }
        text.append("\npos ").append(round(node.x())).append(", ").append(round(node.y()));
        text.append("  size ").append(round(node.width())).append(" x ").append(round(node.height()));
        text.append("\nmin ").append(round(node.minWidth())).append(" x ").append(round(node.minHeight()));
        text.append("\n").append(node.layoutInfo());
        Anchor anchor = node.anchor();
        if (anchor != null) {
            text.append("\nanchor ")
                    .append(anchor.minX())
                    .append(',')
                    .append(anchor.minY())
                    .append(' ')
                    .append(anchor.maxX())
                    .append(',')
                    .append(anchor.maxY())
                    .append("  offset ")
                    .append(round(node.offsetX()))
                    .append(", ")
                    .append(round(node.offsetY()));
        }
        text.append("\nmouse ").append(node.mouseFilter());
        if (node.isFocusable()) {
            text.append("  focusable");
        }
        if (!node.isEnabled()) {
            text.append("  disabled");
        }
        return text.toString();
    }

    private static String round(float value) {
        return Integer.toString(Math.round(value));
    }

    /**
     * Returns the node under the pointer in the inspector.
     *
     * @return the node, or {@code null}
     */
    public @Nullable Node<?> inspected() {
        return inspected;
    }

    // ================================================================== HUD and overlay area

    /** The HUD layer. */
    private final class HudImpl implements Hud {
        @Override
        public <N extends Node<?>> N add(Owner owner, N node) {
            engine.checkMainThread("Hud.add");
            if (!engine.acceptsRegistrations(owner)) {
                throw new IllegalStateException("Owner " + owner.id() + " is not enabled");
            }
            hudNodes.add(new HudNode(node, owner));
            hooks.mount(node);
            layout(node);
            return node;
        }

        @Override
        public boolean remove(Node<?> node) {
            for (int i = 0; i < hudNodes.size(); i++) {
                if (hudNodes.get(i).node() == node) {
                    hudNodes.remove(i);
                    hooks.unmount(node);
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<Node<?>> nodes() {
            List<Node<?>> nodes = new ArrayList<>();
            for (HudNode node : hudNodes) {
                nodes.add(node.node());
            }
            return nodes;
        }

        @Override
        public void setVisible(boolean visible) {
            hudVisible = visible;
        }

        @Override
        public boolean isVisible() {
            return hudVisible;
        }
    }

    /** The screen area given to overlay drawers. */
    private static final class Area implements OverlayArea {
        float width;
        float height;

        @Override
        public float width() {
            return width;
        }

        @Override
        public float height() {
            return height;
        }
    }

    @Override
    public boolean drawsInBaseBuffer() {
        return pixelPerfect;
    }
}
