package dev.gulp.core.input;

import dev.gulp.api.data.Preferences;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.input.ActionPressEvent;
import dev.gulp.api.input.ActionReleaseEvent;
import dev.gulp.api.input.ActionSet;
import dev.gulp.api.input.AxisDirection;
import dev.gulp.api.input.Binding;
import dev.gulp.api.input.CharTypedEvent;
import dev.gulp.api.input.Clipboard;
import dev.gulp.api.input.ControllerFamily;
import dev.gulp.api.input.Cursor;
import dev.gulp.api.input.CursorMode;
import dev.gulp.api.input.Gamepad;
import dev.gulp.api.input.GamepadAxis;
import dev.gulp.api.input.GamepadButton;
import dev.gulp.api.input.GamepadConnectEvent;
import dev.gulp.api.input.GamepadDisconnectEvent;
import dev.gulp.api.input.Input;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.input.InputBindings;
import dev.gulp.api.input.InputDevice;
import dev.gulp.api.input.KeyPressEvent;
import dev.gulp.api.input.KeyReleaseEvent;
import dev.gulp.api.input.KeyRepeatEvent;
import dev.gulp.api.input.KeyboardKey;
import dev.gulp.api.input.Keys;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.input.MouseButtonPressEvent;
import dev.gulp.api.input.MouseButtonReleaseEvent;
import dev.gulp.api.input.MouseMoveEvent;
import dev.gulp.api.input.MouseScrollEvent;
import dev.gulp.api.input.SystemCursor;
import dev.gulp.api.input.Touch;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.render.Camera;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.core.event.EventBus;
import dev.gulp.core.scheduler.PromiseImpl;
import dev.gulp.platform.DecodedImage;
import dev.gulp.platform.InputListener;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformInput;
import dev.gulp.platform.PlatformWindow;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * {@link Input}: receives raw platform events, keeps device state, and computes action states once per tick.
 *
 * <p>A key or button that goes down is latched until the next tick, so a press and release between two ticks still
 * reaches actions. Gamepads are polled once per frame; button presses latch the same way. Bindings changed by the
 * player are stored in preferences under {@code input.bindings.<action key>} as comma-separated binding ids.
 */
public final class InputImpl implements Input, InputListener {

    /** Number of gamepad slots. */
    public static final int GAMEPADS = 4;

    /** Preference key prefix of saved bindings. */
    public static final String PREFIX = "input.bindings.";

    /** Dead zone of {@link Gamepad#axis}. */
    public static final float GAMEPAD_DEAD_ZONE = 0.15f;

    private static final int KEYS = 256;
    private static final int BUTTONS = 8;
    private static final int PAD_BUTTONS = 17;
    private static final int PAD_AXES = 6;
    private static final float PRESS_POINT = 0.5f;

    private final PlatformInput platform;
    private final PlatformWindow window;
    private final EventBus events;
    private final Preferences preferences;
    private final Function<TextureRegion, Pixmap> regionReader;
    private final Supplier<PromiseImpl<String>> promises;

    private final boolean[] keyDown = new boolean[KEYS];
    private final boolean[] keyLatched = new boolean[KEYS];
    private final boolean[] mouseDown = new boolean[BUTTONS];
    private final boolean[] mouseLatched = new boolean[BUTTONS];
    private final GamepadImpl[] pads = new GamepadImpl[GAMEPADS];
    private final Map<InputAction, ActionState> states = new HashMap<>();
    private final List<ActionState> stateList = new ArrayList<>();
    private final Set<ActionSet> disabled = new HashSet<>();
    private final List<Binding> suppressed = new ArrayList<>();
    private final BindingsImpl bindings = new BindingsImpl();
    private final ClipboardImpl clipboard = new ClipboardImpl();
    private final List<TouchImpl> touches = new ArrayList<>();
    private final List<Touch> touchView = Collections.unmodifiableList(touches);
    private final ArrayDeque<TouchImpl> touchPool = new ArrayDeque<>();
    private final Gestures gestures;

    private float mouseX;
    private float mouseY;
    private float frameMoveX;
    private float frameMoveY;
    private boolean moved;
    private float tickMoveX;
    private float tickMoveY;
    private float lastMoveX;
    private float lastMoveY;
    private float scrollSumX;
    private float scrollSumY;
    private float lastScrollX;
    private float lastScrollY;
    private int primaryTouch = -1;
    private @Nullable Consumer<Binding> capture;
    private InputDevice lastDevice = InputDevice.KEYBOARD;
    private ControllerFamily family = ControllerFamily.KEYBOARD_MOUSE;
    private Cursor cursor = SystemCursor.ARROW;
    private CursorMode cursorMode = CursorMode.NORMAL;
    private boolean textInput;
    private boolean running;
    private float now;
    private @Nullable PointMapper pointMapper;

    /**
     * Creates the input and starts listening to the platform.
     *
     * @param platform raw input
     * @param window for cursors
     * @param events where input events go
     * @param preferences where changed bindings are stored
     * @param regionReader reads pixels of a texture region, for cursors made from sprites
     * @param promises creates promises completed on the main thread
     */
    public InputImpl(
            PlatformInput platform,
            PlatformWindow window,
            EventBus events,
            Preferences preferences,
            Function<TextureRegion, Pixmap> regionReader,
            Supplier<PromiseImpl<String>> promises) {
        this.platform = platform;
        this.window = window;
        this.events = events;
        this.preferences = preferences;
        this.regionReader = regionReader;
        this.promises = promises;
        this.gestures = new Gestures(events);
        for (int i = 0; i < GAMEPADS; i++) {
            pads[i] = new GamepadImpl(i);
        }
        platform.setListener(this);
    }

    /**
     * Starts or stops firing events; device state is tracked either way.
     *
     * @param on whether the game runs
     */
    public void setRunning(boolean on) {
        running = on;
        gestures.setEnabled(on);
    }

    /**
     * Creates states for registered actions, so settings screens and {@code conflicts} see them all.
     *
     * @param actions the registered actions
     */
    public void registerActions(Iterable<InputAction> actions) {
        for (InputAction action : actions) {
            state(action);
        }
    }

    /** Stops listening to the platform. */
    public void dispose() {
        platform.setListener(null);
        if (textInput) {
            stopTextInput();
        }
    }

    /** Releases every key and button, for example when the window loses focus and would miss the key-up events. */
    public void releaseAll() {
        Arrays.fill(keyDown, false);
        Arrays.fill(mouseDown, false);
    }

    // ------------------------------------------------------------------ per frame and per tick

    /**
     * Polls gamepads and fires frame-coalesced events. Called once per frame, after platform events were delivered.
     *
     * @param nanoTime the frame time
     */
    public void frame(long nanoTime) {
        now = (float) (nanoTime / 1e9);
        platform.pollGamepads();
        for (GamepadImpl pad : pads) {
            if (pad.connected) {
                pad.poll();
            }
        }
        if (moved) {
            moved = false;
            if (running && events.hasListeners(MouseMoveEvent.class)) {
                events.call(new MouseMoveEvent(mouseX, mouseY, frameMoveX, frameMoveY));
            }
            frameMoveX = 0f;
            frameMoveY = 0f;
        }
        gestures.frame(now, touchView);
    }

    /** Computes action states for the tick that starts. Called before every game tick, or real-time tick when paused. */
    public void tick() {
        lastMoveX = tickMoveX;
        lastMoveY = tickMoveY;
        tickMoveX = 0f;
        tickMoveY = 0f;
        lastScrollX = scrollSumX;
        lastScrollY = scrollSumY;
        scrollSumX = 0f;
        scrollSumY = 0f;
        for (int i = suppressed.size() - 1; i >= 0; i--) {
            if (rawValue(suppressed.get(i), false) == 0f) {
                suppressed.remove(i);
            }
        }
        for (int i = 0; i < stateList.size(); i++) {
            ActionState state = stateList.get(i);
            state.update(!disabled.contains(state.action.set()));
        }
        Arrays.fill(keyLatched, false);
        Arrays.fill(mouseLatched, false);
        for (GamepadImpl pad : pads) {
            Arrays.fill(pad.latched, false);
        }
        for (int i = 0; i < stateList.size(); i++) {
            ActionState state = stateList.get(i);
            if (!running) {
                continue;
            }
            if (state.justPressed && events.hasListeners(ActionPressEvent.class)) {
                events.call(new ActionPressEvent(state.action));
            } else if (state.justReleased && events.hasListeners(ActionReleaseEvent.class)) {
                events.call(new ActionReleaseEvent(state.action, state.releasedAfter));
            }
        }
    }

    private ActionState state(InputAction action) {
        ActionState state = states.get(action);
        if (state == null) {
            state = new ActionState(action);
            states.put(action, state);
            stateList.add(state);
        }
        return state;
    }

    /** Value of one binding from device state: 1 or 0 for buttons, the push of an axis without dead zone. */
    private float rawValue(Binding binding, boolean latched) {
        if (binding instanceof KeyboardKey key) {
            int code = key.code();
            return code > 0 && code < KEYS && (keyDown[code] || (latched && keyLatched[code])) ? 1f : 0f;
        }
        if (binding instanceof MouseButton button) {
            int index = button.index();
            return mouseDown[index] || (latched && mouseLatched[index]) ? 1f : 0f;
        }
        if (binding instanceof GamepadButton button) {
            int index = button.index();
            for (GamepadImpl pad : pads) {
                if (pad.connected && (pad.down[index] || (latched && pad.latched[index]))) {
                    return 1f;
                }
            }
            return 0f;
        }
        AxisDirection direction = (AxisDirection) binding;
        int index = direction.axis().index();
        float best = 0f;
        for (GamepadImpl pad : pads) {
            if (pad.connected) {
                float value = direction.positive() ? pad.axes[index] : -pad.axes[index];
                best = Math.max(best, value);
            }
        }
        return Math.min(best, 1f);
    }

    private static float applyDeadZone(float value, float deadZone) {
        return value <= deadZone ? 0f : Math.min(1f, (value - deadZone) / (1f - deadZone));
    }

    private void finishCapture(Binding binding) {
        Consumer<Binding> callback = capture;
        capture = null;
        if (!suppressed.contains(binding)) {
            suppressed.add(binding);
        }
        if (callback != null) {
            callback.accept(binding);
        }
    }

    private void usedKeyboardOrMouse(InputDevice device) {
        lastDevice = device;
        family = ControllerFamily.KEYBOARD_MOUSE;
    }

    // ------------------------------------------------------------------ InputListener

    @Override
    public void keyDown(int keyCode, int scanCode, int modifiers, boolean repeat) {
        usedKeyboardOrMouse(InputDevice.KEYBOARD);
        boolean known = keyCode > 0 && keyCode < KEYS;
        if (repeat) {
            if (running && events.hasListeners(KeyRepeatEvent.class)) {
                events.call(new KeyRepeatEvent(Keys.of(keyCode), scanCode, modifiers));
            }
            return;
        }
        if (known) {
            keyDown[keyCode] = true;
        }
        if (capture != null && known) {
            finishCapture(Keys.of(keyCode));
            return;
        }
        if (known) {
            keyLatched[keyCode] = true;
        }
        if (running && events.hasListeners(KeyPressEvent.class)) {
            events.call(new KeyPressEvent(Keys.of(keyCode), scanCode, modifiers));
        }
    }

    @Override
    public void keyUp(int keyCode, int scanCode, int modifiers) {
        if (keyCode > 0 && keyCode < KEYS) {
            keyDown[keyCode] = false;
        }
        if (running && events.hasListeners(KeyReleaseEvent.class)) {
            events.call(new KeyReleaseEvent(Keys.of(keyCode), scanCode, modifiers));
        }
    }

    @Override
    public void textTyped(int codePoint) {
        if (running && events.hasListeners(CharTypedEvent.class)) {
            events.call(new CharTypedEvent(codePoint));
        }
    }

    @Override
    public void mouseMoved(float x, float y, float deltaX, float deltaY) {
        mouseX = x;
        mouseY = y;
        frameMoveX += deltaX;
        frameMoveY += deltaY;
        tickMoveX += deltaX;
        tickMoveY += deltaY;
        moved = true;
    }

    @Override
    public void mouseButton(int button, boolean down, int modifiers) {
        MouseButton mouse = MouseButton.ofIndex(button);
        if (mouse == null) {
            return;
        }
        usedKeyboardOrMouse(InputDevice.MOUSE);
        mouseDown[button] = down;
        if (down) {
            if (capture != null) {
                finishCapture(mouse);
                return;
            }
            mouseLatched[button] = true;
            if (running && events.hasListeners(MouseButtonPressEvent.class)) {
                events.call(new MouseButtonPressEvent(mouse, mouseX, mouseY, modifiers));
            }
        } else if (running && events.hasListeners(MouseButtonReleaseEvent.class)) {
            events.call(new MouseButtonReleaseEvent(mouse, mouseX, mouseY, modifiers));
        }
    }

    @Override
    public void scrolled(float deltaX, float deltaY) {
        scrollSumX += deltaX;
        scrollSumY += deltaY;
        if (running && events.hasListeners(MouseScrollEvent.class)) {
            events.call(new MouseScrollEvent(deltaX, deltaY));
        }
    }

    @Override
    public void touch(int pointer, int phase, float x, float y) {
        TouchImpl touch = null;
        for (TouchImpl active : touches) {
            if (active.id == pointer) {
                touch = active;
                break;
            }
        }
        if (phase == 0) {
            if (touch == null) {
                touch = touchPool.isEmpty() ? new TouchImpl() : touchPool.removeFirst();
                touches.add(touch);
            }
            touch.id = pointer;
            touch.x = x;
            touch.y = y;
            touch.startX = x;
            touch.startY = y;
            touch.startTime = now;
            gestures.down(pointer, x, y, now, touches.size());
            if (primaryTouch == -1) {
                primaryTouch = pointer;
                mouseMoved(x, y, x - mouseX, y - mouseY);
                mouseButton(MouseButton.LEFT.index(), true, 0);
            }
            return;
        }
        if (touch == null) {
            return;
        }
        touch.x = x;
        touch.y = y;
        if (pointer == primaryTouch) {
            mouseMoved(x, y, x - mouseX, y - mouseY);
        }
        if (phase == 1) {
            gestures.move(pointer, x, y);
            return;
        }
        touches.remove(touch);
        touchPool.add(touch);
        if (phase == 2) {
            gestures.up(pointer, x, y, now);
        } else {
            gestures.up(pointer, x, y, Float.MAX_VALUE);
        }
        if (pointer == primaryTouch) {
            primaryTouch = -1;
            mouseButton(MouseButton.LEFT.index(), false, 0);
        }
    }

    @Override
    public void gamepadConnection(int index, boolean connected) {
        if (index < 0 || index >= GAMEPADS) {
            return;
        }
        GamepadImpl pad = pads[index];
        pad.connected = connected;
        if (connected) {
            String name = platform.gamepadName(index);
            pad.name = name != null ? name : "Gamepad " + (index + 1);
            pad.family = ControllerFamily.fromName(pad.name);
            if (running && events.hasListeners(GamepadConnectEvent.class)) {
                events.call(new GamepadConnectEvent(pad));
            }
        } else {
            Arrays.fill(pad.down, false);
            Arrays.fill(pad.axes, 0f);
            if (running && events.hasListeners(GamepadDisconnectEvent.class)) {
                events.call(new GamepadDisconnectEvent(pad));
            }
            pad.name = "";
        }
    }

    // ------------------------------------------------------------------ Input

    @Override
    public boolean pressed(InputAction action) {
        return state(action).active;
    }

    @Override
    public boolean justPressed(InputAction action) {
        return state(action).justPressed;
    }

    @Override
    public boolean justReleased(InputAction action) {
        return state(action).justReleased;
    }

    @Override
    public int heldTicks(InputAction action) {
        return state(action).held;
    }

    @Override
    public float strength(InputAction action) {
        return state(action).strength;
    }

    @Override
    public float axis(InputAction negative, InputAction positive) {
        return state(positive).strength - state(negative).strength;
    }

    @Override
    public Vec2 vector(InputAction left, InputAction right, InputAction up, InputAction down) {
        ActionState r = state(right);
        float x = r.raw - state(left).raw;
        float y = state(down).raw - state(up).raw;
        float length = (float) Math.sqrt(x * x + y * y);
        float deadZone = right.deadZone();
        if (length <= deadZone || length == 0f) {
            return Vec2.ZERO;
        }
        float scale = Math.min(1f, (length - deadZone) / (1f - deadZone)) / length;
        return new Vec2(x * scale, y * scale);
    }

    @Override
    public void enable(ActionSet set) {
        disabled.remove(set);
    }

    @Override
    public void disable(ActionSet set) {
        disabled.add(set);
    }

    @Override
    public boolean isEnabled(ActionSet set) {
        return !disabled.contains(set);
    }

    @Override
    public InputBindings bindings() {
        return bindings;
    }

    @Override
    public boolean isDown(KeyboardKey key) {
        int code = key.code();
        return code > 0 && code < KEYS && keyDown[code];
    }

    @Override
    public boolean isDown(MouseButton button) {
        return mouseDown[button.index()];
    }

    @Override
    public float mouseX() {
        return mouseX;
    }

    @Override
    public float mouseY() {
        return mouseY;
    }

    @Override
    public Vec2 mouseWorld(Camera camera) {
        PointMapper mapper = pointMapper;
        if (mapper == null) {
            return camera.screenToWorld(new Vec2(mouseX, mouseY));
        }
        Vec2 logical = mapper.toLogical(mouseX, mouseY);
        Rect area = camera.viewport();
        return camera.screenToWorld(
                new Vec2(logical.x() - area.x() * mapper.width(), logical.y() - area.y() * mapper.height()));
    }

    /**
     * Converts window points to the logical game area, so that {@link #mouseWorld} matches what is drawn.
     *
     * @param mapper the display
     */
    public void setPointMapper(PointMapper mapper) {
        this.pointMapper = mapper;
    }

    /** Maps window points to logical game coordinates. */
    public interface PointMapper {
        /**
         * Converts a window point.
         *
         * @param windowX window x
         * @param windowY window y
         * @return logical coordinates
         */
        Vec2 toLogical(float windowX, float windowY);

        /**
         * Returns the logical width.
         *
         * @return logical points
         */
        float width();

        /**
         * Returns the logical height.
         *
         * @return logical points
         */
        float height();
    }

    @Override
    public float mouseDeltaX() {
        return lastMoveX;
    }

    @Override
    public float mouseDeltaY() {
        return lastMoveY;
    }

    @Override
    public float scrollX() {
        return lastScrollX;
    }

    @Override
    public float scrollY() {
        return lastScrollY;
    }

    @Override
    public Cursor cursor() {
        return cursor;
    }

    @Override
    public void setCursor(Cursor newCursor) {
        cursor = newCursor;
        if (newCursor instanceof SystemCursor system) {
            window.setSystemCursor(system.ordinal());
            return;
        }
        Cursor.Custom custom = (Cursor.Custom) newCursor;
        Pixmap image = custom.pixmap();
        TextureRegion region = custom.region();
        if (image == null && region != null) {
            image = regionReader.apply(region);
        }
        if (image == null) {
            throw new IllegalArgumentException("Custom cursor without an image");
        }
        byte[] rgba = image.toRgba();
        ByteBuffer pixels = ByteBuffer.allocateDirect(rgba.length).order(ByteOrder.nativeOrder());
        pixels.put(rgba).flip();
        window.setCustomCursor(new DecodedImage(image.width(), image.height(), pixels), custom.hotX(), custom.hotY());
    }

    @Override
    public CursorMode cursorMode() {
        return cursorMode;
    }

    @Override
    public void setCursorMode(CursorMode mode) {
        cursorMode = mode;
        window.setCursorMode(dev.gulp.platform.CursorMode.values()[mode.ordinal()]);
    }

    @Override
    public List<Touch> touches() {
        return touchView;
    }

    @Override
    public Gamepad gamepad(int index) {
        if (index < 0 || index >= GAMEPADS) {
            throw new IndexOutOfBoundsException("Gamepad slot " + index + " is not in [0, " + GAMEPADS + ")");
        }
        return pads[index];
    }

    @Override
    public List<Gamepad> gamepads() {
        List<Gamepad> connected = new ArrayList<>();
        for (GamepadImpl pad : pads) {
            if (pad.connected) {
                connected.add(pad);
            }
        }
        return connected;
    }

    @Override
    public int maxGamepads() {
        return GAMEPADS;
    }

    @Override
    public InputDevice lastDevice() {
        return lastDevice;
    }

    @Override
    public ControllerFamily controllerFamily() {
        return family;
    }

    @Override
    public Clipboard clipboard() {
        return clipboard;
    }

    @Override
    public void startTextInput(Rect area) {
        textInput = true;
        platform.setTextInput(true, area.x(), area.y(), area.width(), area.height());
    }

    @Override
    public void stopTextInput() {
        textInput = false;
        platform.setTextInput(false, 0f, 0f, 0f, 0f);
    }

    @Override
    public boolean isTextInputActive() {
        return textInput;
    }

    // ------------------------------------------------------------------ parts

    /** Per-tick state and current bindings of one action. */
    private final class ActionState {
        final InputAction action;

        @Nullable Binding[] current;

        boolean active;
        boolean justPressed;
        boolean justReleased;
        int held;
        int releasedAfter;
        float strength;
        float raw;

        ActionState(InputAction action) {
            this.action = action;
            this.current = load(action);
        }

        void update(boolean enabled) {
            float best = 0f;
            float bestRaw = 0f;
            if (enabled) {
                for (Binding binding : current) {
                    if (binding == null || suppressed.contains(binding)) {
                        continue;
                    }
                    float value = rawValue(binding, true);
                    bestRaw = Math.max(bestRaw, value);
                    float shaped = binding instanceof AxisDirection ? applyDeadZone(value, action.deadZone()) : value;
                    best = Math.max(best, shaped);
                }
            }
            strength = best;
            raw = bestRaw;
            boolean now = best >= PRESS_POINT;
            justPressed = now && !active;
            justReleased = !now && active;
            if (now) {
                held++;
            } else {
                if (justReleased) {
                    releasedAfter = held;
                }
                held = 0;
            }
            active = now;
        }
    }

    private @Nullable Binding[] load(InputAction action) {
        String key = PREFIX + action.key();
        if (!preferences.has(key)) {
            return action.defaultBindings().toArray(new Binding[0]);
        }
        String saved = preferences.getString(key, "");
        String[] ids = saved.isEmpty() ? new String[0] : saved.split(",", -1);
        @Nullable Binding[] result = new Binding[ids.length];
        for (int i = 0; i < ids.length; i++) {
            if (!ids[i].isEmpty()) {
                try {
                    result[i] = Binding.parse(ids[i]);
                } catch (IllegalArgumentException ignored) {
                    result[i] = null;
                }
            }
        }
        return result;
    }

    private void save(ActionState state) {
        String key = PREFIX + state.action.key();
        if (Arrays.asList(state.current).equals(state.action.defaultBindings())) {
            preferences.remove(key);
            return;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < state.current.length; i++) {
            if (i > 0) {
                text.append(',');
            }
            Binding binding = state.current[i];
            if (binding != null) {
                text.append(binding.id());
            }
        }
        preferences.set(key, text.toString());
    }

    /** {@link InputBindings} over the action states. */
    private final class BindingsImpl implements InputBindings {
        @Override
        public List<@Nullable Binding> of(InputAction action) {
            return new ArrayList<>(Arrays.asList(state(action).current));
        }

        @Override
        public void rebind(InputAction action, int slot, Binding binding) {
            ActionState state = state(action);
            if (slot < 0 || slot > state.current.length) {
                throw new IllegalArgumentException(
                        "Slot " + slot + " of " + action.key() + " is out of range; it has " + state.current.length);
            }
            if (slot == state.current.length) {
                state.current = Arrays.copyOf(state.current, slot + 1);
            }
            state.current[slot] = binding;
            save(state);
        }

        @Override
        public void unbind(InputAction action, int slot) {
            ActionState state = state(action);
            if (slot >= 0 && slot < state.current.length) {
                state.current[slot] = null;
                save(state);
            }
        }

        @Override
        public void reset(InputAction action) {
            ActionState state = state(action);
            state.current = action.defaultBindings().toArray(new Binding[0]);
            preferences.remove(PREFIX + action.key());
        }

        @Override
        public void resetAll() {
            for (ActionState state : stateList) {
                state.current = state.action.defaultBindings().toArray(new Binding[0]);
            }
            for (String key : preferences.keys()) {
                if (key.startsWith(PREFIX)) {
                    preferences.remove(key);
                }
            }
        }

        @Override
        public List<InputAction> conflicts(InputAction action, Binding binding) {
            List<InputAction> result = new ArrayList<>();
            for (ActionState state : stateList) {
                if (!state.action.equals(action)
                        && state.action.set().equals(action.set())
                        && Arrays.asList(state.current).contains(binding)) {
                    result.add(state.action);
                }
            }
            return result;
        }

        @Override
        public void captureNextInput(Consumer<Binding> callback) {
            capture = callback;
        }

        @Override
        public void cancelCapture() {
            capture = null;
        }

        @Override
        public boolean isCapturing() {
            return capture != null;
        }
    }

    /** One gamepad slot, refreshed from the platform every frame. */
    private final class GamepadImpl implements Gamepad {
        final int index;
        final boolean[] down = new boolean[PAD_BUTTONS];
        final boolean[] latched = new boolean[PAD_BUTTONS];
        final float[] axes = new float[PAD_AXES];
        boolean connected;
        String name = "";
        ControllerFamily family = ControllerFamily.GENERIC;
        float deadZone = GAMEPAD_DEAD_ZONE;

        GamepadImpl(int index) {
            this.index = index;
        }

        void poll() {
            GamepadButton[] buttons = GamepadButton.values();
            for (int b = 0; b < PAD_BUTTONS; b++) {
                boolean isDown = platform.gamepadButton(index, b);
                if (isDown && !down[b]) {
                    latched[b] = true;
                    used();
                    if (capture != null) {
                        down[b] = true;
                        latched[b] = false;
                        finishCapture(buttons[b]);
                        continue;
                    }
                }
                down[b] = isDown;
            }
            GamepadAxis[] all = GamepadAxis.values();
            for (int a = 0; a < PAD_AXES; a++) {
                float value = platform.gamepadAxis(index, a);
                float before = axes[a];
                axes[a] = value;
                boolean crossed = Math.abs(value) >= PRESS_POINT && Math.abs(before) < PRESS_POINT;
                if (crossed) {
                    used();
                    if (capture != null && (value > 0f || !all[a].isTrigger())) {
                        finishCapture(value > 0f ? all[a].positive() : all[a].negative());
                    }
                }
            }
        }

        private void used() {
            lastDevice = InputDevice.GAMEPAD;
            InputImpl.this.family = family;
        }

        @Override
        public int index() {
            return index;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ControllerFamily family() {
            return family;
        }

        @Override
        public boolean isDown(GamepadButton button) {
            return connected && down[button.index()];
        }

        @Override
        public float axis(GamepadAxis axis) {
            float value = rawAxis(axis);
            float shaped = applyDeadZone(Math.abs(value), deadZone);
            return value < 0f ? -shaped : shaped;
        }

        @Override
        public float rawAxis(GamepadAxis axis) {
            return connected ? axes[axis.index()] : 0f;
        }

        @Override
        public float deadZone() {
            return deadZone;
        }

        @Override
        public void setDeadZone(float zone) {
            if (!(zone >= 0f && zone < 1f)) {
                throw new IllegalArgumentException("Dead zone must be in [0, 1), got " + zone);
            }
            deadZone = zone;
        }

        @Override
        public boolean supportsRumble() {
            return connected && platform.supportsRumble(index);
        }

        @Override
        public void rumble(float weak, float strong, float seconds) {
            if (connected) {
                platform.rumble(index, weak, strong, Math.round(seconds * 1000f));
            }
        }

        @Override
        public String toString() {
            return "Gamepad[" + index + (connected ? ", " + name : ", disconnected") + "]";
        }
    }

    /** A finger; pooled. */
    private final class TouchImpl implements Touch {
        int id;
        float x;
        float y;
        float startX;
        float startY;
        float startTime;

        @Override
        public int id() {
            return id;
        }

        @Override
        public float x() {
            return x;
        }

        @Override
        public float y() {
            return y;
        }

        @Override
        public float startX() {
            return startX;
        }

        @Override
        public float startY() {
            return startY;
        }

        @Override
        public float seconds() {
            return now - startTime;
        }
    }

    /** Clipboard through the platform; reads complete on the main thread. */
    private final class ClipboardImpl implements Clipboard {
        @Override
        public Promise<String> get() {
            PromiseImpl<String> promise = promises.get();
            platform.readClipboard(new PlatformCallback<>() {
                @Override
                public void success(String value) {
                    promise.complete(value);
                }

                @Override
                public void failure(Throwable error) {
                    promise.complete("");
                }
            });
            return promise;
        }

        @Override
        public void set(String text) {
            platform.writeClipboard(text);
        }
    }
}
