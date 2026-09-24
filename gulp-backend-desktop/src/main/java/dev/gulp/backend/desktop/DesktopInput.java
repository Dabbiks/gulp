package dev.gulp.backend.desktop;

import static org.lwjgl.glfw.GLFW.*;

import dev.gulp.core.MainQueue;
import dev.gulp.platform.InputListener;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformInput;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFWGamepadState;

/**
 * Raw keyboard and mouse input from GLFW callbacks, which run inside {@code glfwPollEvents} on the main thread before
 * each frame. Key codes are USB HID usage ids, the same on every backend. Gamepads use GLFW's gamepad API with its
 * built-in SDL mapping database, polled once per frame into the standard mapping.
 */
final class DesktopInput implements PlatformInput {

    /** GLFW key -> USB HID usage id; 0 for keys without one. */
    private static final int[] HID = new int[GLFW_KEY_LAST + 1];

    static {
        for (int i = 0; i < 26; i++) {
            HID[GLFW_KEY_A + i] = 4 + i;
        }
        for (int i = 1; i <= 9; i++) {
            HID[GLFW_KEY_0 + i] = 29 + i;
            HID[GLFW_KEY_KP_0 + i] = 88 + i;
        }
        HID[GLFW_KEY_0] = 39;
        HID[GLFW_KEY_KP_0] = 98;
        for (int i = 0; i < 12; i++) {
            HID[GLFW_KEY_F1 + i] = 58 + i;
        }
        int[][] pairs = {
            {GLFW_KEY_ENTER, 40},
            {GLFW_KEY_ESCAPE, 41},
            {GLFW_KEY_BACKSPACE, 42},
            {GLFW_KEY_TAB, 43},
            {GLFW_KEY_SPACE, 44},
            {GLFW_KEY_MINUS, 45},
            {GLFW_KEY_EQUAL, 46},
            {GLFW_KEY_LEFT_BRACKET, 47},
            {GLFW_KEY_RIGHT_BRACKET, 48},
            {GLFW_KEY_BACKSLASH, 49},
            {GLFW_KEY_SEMICOLON, 51},
            {GLFW_KEY_APOSTROPHE, 52},
            {GLFW_KEY_GRAVE_ACCENT, 53},
            {GLFW_KEY_COMMA, 54},
            {GLFW_KEY_PERIOD, 55},
            {GLFW_KEY_SLASH, 56},
            {GLFW_KEY_CAPS_LOCK, 57},
            {GLFW_KEY_PRINT_SCREEN, 70},
            {GLFW_KEY_SCROLL_LOCK, 71},
            {GLFW_KEY_PAUSE, 72},
            {GLFW_KEY_INSERT, 73},
            {GLFW_KEY_HOME, 74},
            {GLFW_KEY_PAGE_UP, 75},
            {GLFW_KEY_DELETE, 76},
            {GLFW_KEY_END, 77},
            {GLFW_KEY_PAGE_DOWN, 78},
            {GLFW_KEY_RIGHT, 79},
            {GLFW_KEY_LEFT, 80},
            {GLFW_KEY_DOWN, 81},
            {GLFW_KEY_UP, 82},
            {GLFW_KEY_NUM_LOCK, 83},
            {GLFW_KEY_KP_DIVIDE, 84},
            {GLFW_KEY_KP_MULTIPLY, 85},
            {GLFW_KEY_KP_SUBTRACT, 86},
            {GLFW_KEY_KP_ADD, 87},
            {GLFW_KEY_KP_ENTER, 88},
            {GLFW_KEY_KP_DECIMAL, 99},
            {GLFW_KEY_WORLD_2, 100},
            {GLFW_KEY_MENU, 101},
            {GLFW_KEY_LEFT_CONTROL, 224},
            {GLFW_KEY_LEFT_SHIFT, 225},
            {GLFW_KEY_LEFT_ALT, 226},
            {GLFW_KEY_LEFT_SUPER, 227},
            {GLFW_KEY_RIGHT_CONTROL, 228},
            {GLFW_KEY_RIGHT_SHIFT, 229},
            {GLFW_KEY_RIGHT_ALT, 230},
            {GLFW_KEY_RIGHT_SUPER, 231}
        };
        for (int[] pair : pairs) {
            HID[pair[0]] = pair[1];
        }
    }

    /** Gamepad slots; GLFW joystick ids 0..3. */
    private static final int SLOTS = 4;

    /** Standard mapping button -> GLFW gamepad button; -1 for triggers, read from their axes. */
    private static final int[] BUTTONS = {
        GLFW_GAMEPAD_BUTTON_A,
        GLFW_GAMEPAD_BUTTON_B,
        GLFW_GAMEPAD_BUTTON_X,
        GLFW_GAMEPAD_BUTTON_Y,
        GLFW_GAMEPAD_BUTTON_LEFT_BUMPER,
        GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER,
        -1,
        -1,
        GLFW_GAMEPAD_BUTTON_BACK,
        GLFW_GAMEPAD_BUTTON_START,
        GLFW_GAMEPAD_BUTTON_LEFT_THUMB,
        GLFW_GAMEPAD_BUTTON_RIGHT_THUMB,
        GLFW_GAMEPAD_BUTTON_DPAD_UP,
        GLFW_GAMEPAD_BUTTON_DPAD_DOWN,
        GLFW_GAMEPAD_BUTTON_DPAD_LEFT,
        GLFW_GAMEPAD_BUTTON_DPAD_RIGHT,
        GLFW_GAMEPAD_BUTTON_GUIDE
    };

    private final long window;
    private final MainQueue mainQueue;
    private final GLFWGamepadState[] states = new GLFWGamepadState[SLOTS];
    private final boolean[] connected = new boolean[SLOTS];
    private @Nullable InputListener listener;
    private double lastX = Double.NaN;
    private double lastY;

    DesktopInput(long window, MainQueue mainQueue) {
        this.window = window;
        this.mainQueue = mainQueue;
        for (int i = 0; i < SLOTS; i++) {
            states[i] = GLFWGamepadState.create();
        }
        glfwSetKeyCallback(window, (handle, key, scancode, action, mods) -> {
            InputListener current = listener;
            if (current == null) {
                return;
            }
            int code = hid(key);
            int modifiers = mods & 0xf;
            if (action == GLFW_RELEASE) {
                current.keyUp(code, scancode, modifiers);
            } else {
                current.keyDown(code, scancode, modifiers, action == GLFW_REPEAT);
            }
        });
        glfwSetCharCallback(window, (handle, codePoint) -> {
            InputListener current = listener;
            if (current != null) {
                current.textTyped(codePoint);
            }
        });
        glfwSetCursorPosCallback(window, (handle, x, y) -> {
            double dx = Double.isNaN(lastX) ? 0 : x - lastX;
            double dy = Double.isNaN(lastX) ? 0 : y - lastY;
            lastX = x;
            lastY = y;
            InputListener current = listener;
            if (current != null) {
                current.mouseMoved((float) x, (float) y, (float) dx, (float) dy);
            }
        });
        glfwSetMouseButtonCallback(window, (handle, button, action, mods) -> {
            InputListener current = listener;
            if (current != null) {
                current.mouseButton(button, action == GLFW_PRESS, mods & 0xf);
            }
        });
        glfwSetScrollCallback(window, (handle, dx, dy) -> {
            InputListener current = listener;
            if (current != null) {
                // GLFW reports wheel notches with up positive; Gulp's Y axis points down.
                current.scrolled((float) dx, (float) -dy);
            }
        });
    }

    /**
     * Maps a GLFW key to its USB HID usage id.
     *
     * @param glfwKey the GLFW key
     * @return the id, or 0 for unknown keys
     */
    static int hid(int glfwKey) {
        return glfwKey >= 0 && glfwKey < HID.length ? HID[glfwKey] : 0;
    }

    @Override
    public void setListener(@Nullable InputListener listener) {
        this.listener = listener;
    }

    @Override
    public void pollGamepads() {
        for (int slot = 0; slot < SLOTS; slot++) {
            boolean now = glfwJoystickIsGamepad(slot) && glfwGetGamepadState(slot, states[slot]);
            if (now != connected[slot]) {
                connected[slot] = now;
                InputListener current = listener;
                if (current != null) {
                    current.gamepadConnection(slot, now);
                }
            }
        }
    }

    @Override
    public boolean isGamepadConnected(int index) {
        return index >= 0 && index < SLOTS && connected[index];
    }

    @Override
    public @Nullable String gamepadName(int index) {
        return isGamepadConnected(index) ? glfwGetGamepadName(index) : null;
    }

    @Override
    public float gamepadAxis(int index, int axis) {
        if (!isGamepadConnected(index) || axis < 0 || axis >= 6) {
            return 0f;
        }
        float value = states[index].axes(axis);
        // GLFW triggers rest at -1; the standard mapping uses 0..1.
        return axis >= GLFW_GAMEPAD_AXIS_LEFT_TRIGGER ? (value + 1f) / 2f : value;
    }

    @Override
    public boolean gamepadButton(int index, int button) {
        if (!isGamepadConnected(index) || button < 0 || button >= BUTTONS.length) {
            return false;
        }
        int glfwButton = BUTTONS[button];
        if (glfwButton < 0) {
            return gamepadAxis(index, button == 6 ? 4 : 5) > 0.5f;
        }
        return states[index].buttons(glfwButton) == GLFW_PRESS;
    }

    @Override
    public boolean rumble(int index, float weak, float strong, int durationMillis) {
        // GLFW has no rumble API.
        return false;
    }

    @Override
    public boolean supportsRumble(int index) {
        return false;
    }

    @Override
    public void setTextInput(boolean active, float x, float y, float width, float height) {
        glfwSetInputMode(window, GLFW_IME, active ? GLFW_TRUE : GLFW_FALSE);
        if (active) {
            glfwSetPreeditCursorRectangle(window, (int) x, (int) y, (int) width, (int) height);
        }
    }

    @Override
    public void readClipboard(PlatformCallback<String> callback) {
        String text = glfwGetClipboardString(window);
        mainQueue.post(() -> callback.success(text == null ? "" : text));
    }

    @Override
    public void writeClipboard(String text) {
        glfwSetClipboardString(window, text);
    }
}
