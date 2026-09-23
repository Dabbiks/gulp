package dev.gulp.backend.headless;

import dev.gulp.platform.InputListener;
import dev.gulp.platform.PlatformCallback;
import dev.gulp.platform.PlatformInput;
import java.util.ArrayDeque;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Input fed by tests. Injected events are queued and delivered to the listener before the next frame, like real
 * platform events; gamepads are simulated slots.
 *
 * <pre>{@code
 * backend.input().inject(l -> l.keyDown(KEY_SPACE, 57, 0, false));
 * backend.input().setGamepad(0, "Test pad", new float[] {-1f, 0f}, new boolean[] {true});
 * backend.loop().step(1);
 * }</pre>
 */
public final class HeadlessInput implements PlatformInput {

    /** Number of simulated gamepad slots. */
    public static final int GAMEPAD_SLOTS = 4;

    private final ArrayDeque<Consumer<InputListener>> queued = new ArrayDeque<>();
    private final @Nullable String[] gamepadNames = new String[GAMEPAD_SLOTS];
    private final float[][] gamepadAxes = new float[GAMEPAD_SLOTS][];
    private final boolean[][] gamepadButtons = new boolean[GAMEPAD_SLOTS][];
    private final boolean[] reportedConnected = new boolean[GAMEPAD_SLOTS];
    private @Nullable InputListener listener;
    private String clipboard = "";
    private int rumbleCount;

    HeadlessInput() {}

    /**
     * Queues an event for delivery before the next frame.
     *
     * @param event calls one method of the listener
     */
    public void inject(Consumer<InputListener> event) {
        queued.add(event);
    }

    void deliverQueued() {
        while (!queued.isEmpty()) {
            Consumer<InputListener> event = queued.removeFirst();
            if (listener != null) {
                event.accept(listener);
            }
        }
    }

    /**
     * Connects a simulated gamepad or replaces its state.
     *
     * @param index the slot
     * @param name gamepad name
     * @param axes axis values in the standard mapping
     * @param buttons button states in the standard mapping
     */
    public void setGamepad(int index, String name, float[] axes, boolean[] buttons) {
        gamepadNames[index] = name;
        gamepadAxes[index] = axes.clone();
        gamepadButtons[index] = buttons.clone();
    }

    /**
     * Disconnects a simulated gamepad.
     *
     * @param index the slot
     */
    public void removeGamepad(int index) {
        gamepadNames[index] = null;
        gamepadAxes[index] = null;
        gamepadButtons[index] = null;
    }

    /**
     * Returns how many times rumble was requested.
     *
     * @return the rumble request count
     */
    public int rumbleCount() {
        return rumbleCount;
    }

    @Override
    public void setListener(@Nullable InputListener listener) {
        this.listener = listener;
    }

    @Override
    public void pollGamepads() {
        for (int i = 0; i < GAMEPAD_SLOTS; i++) {
            boolean connected = isGamepadConnected(i);
            if (connected != reportedConnected[i]) {
                reportedConnected[i] = connected;
                if (listener != null) {
                    listener.gamepadConnection(i, connected);
                }
            }
        }
    }

    @Override
    public boolean isGamepadConnected(int index) {
        return index >= 0 && index < GAMEPAD_SLOTS && gamepadNames[index] != null;
    }

    @Override
    public @Nullable String gamepadName(int index) {
        return isGamepadConnected(index) ? gamepadNames[index] : null;
    }

    @Override
    public float gamepadAxis(int index, int axis) {
        if (!isGamepadConnected(index)) {
            return 0f;
        }
        float[] axes = gamepadAxes[index];
        return axes != null && axis >= 0 && axis < axes.length ? axes[axis] : 0f;
    }

    @Override
    public boolean gamepadButton(int index, int button) {
        if (!isGamepadConnected(index)) {
            return false;
        }
        boolean[] buttons = gamepadButtons[index];
        return buttons != null && button >= 0 && button < buttons.length && buttons[button];
    }

    @Override
    public boolean rumble(int index, float weak, float strong, int durationMillis) {
        if (!isGamepadConnected(index)) {
            return false;
        }
        rumbleCount++;
        return true;
    }

    @Override
    public void readClipboard(PlatformCallback<String> callback) {
        callback.success(clipboard);
    }

    @Override
    public void writeClipboard(String text) {
        clipboard = text;
    }
}
