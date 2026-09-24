package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.input.ActionPressEvent;
import dev.gulp.api.input.ActionReleaseEvent;
import dev.gulp.api.input.ActionSet;
import dev.gulp.api.input.Binding;
import dev.gulp.api.input.CharTypedEvent;
import dev.gulp.api.input.ControllerFamily;
import dev.gulp.api.input.Cursor;
import dev.gulp.api.input.CursorMode;
import dev.gulp.api.input.DoubleTapEvent;
import dev.gulp.api.input.GamepadAxis;
import dev.gulp.api.input.GamepadButton;
import dev.gulp.api.input.GamepadConnectEvent;
import dev.gulp.api.input.GamepadDisconnectEvent;
import dev.gulp.api.input.Input;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.input.InputDevice;
import dev.gulp.api.input.KeyPressEvent;
import dev.gulp.api.input.KeyReleaseEvent;
import dev.gulp.api.input.KeyRepeatEvent;
import dev.gulp.api.input.Keys;
import dev.gulp.api.input.LongPressEvent;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.input.MouseButtonPressEvent;
import dev.gulp.api.input.MouseButtonReleaseEvent;
import dev.gulp.api.input.MouseMoveEvent;
import dev.gulp.api.input.MouseScrollEvent;
import dev.gulp.api.input.PanEvent;
import dev.gulp.api.input.PinchEvent;
import dev.gulp.api.input.SwipeEvent;
import dev.gulp.api.input.SystemCursor;
import dev.gulp.api.input.TapEvent;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Registries;
import dev.gulp.backend.headless.HeadlessBackend;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import dev.gulp.core.data.PreferencesImpl;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class InputTest {

    private static final InputAction JUMP = InputAction.builder(Key.of("test", "jump"))
            .bind(Keys.SPACE, GamepadButton.SOUTH)
            .build();
    private static final InputAction LEFT = InputAction.builder(Key.of("test", "left"))
            .bind(Keys.A, GamepadAxis.LEFT_X.negative())
            .build();
    private static final InputAction RIGHT = InputAction.builder(Key.of("test", "right"))
            .bind(Keys.D, GamepadAxis.LEFT_X.positive())
            .build();
    private static final InputAction UP = InputAction.builder(Key.of("test", "up"))
            .bind(Keys.W, GamepadAxis.LEFT_Y.negative())
            .build();
    private static final InputAction DOWN = InputAction.builder(Key.of("test", "down"))
            .bind(Keys.S, GamepadAxis.LEFT_Y.positive())
            .build();
    private static final InputAction CONFIRM = InputAction.builder(Key.of("test", "confirm"))
            .bind(Keys.ENTER, MouseButton.LEFT)
            .set(ActionSet.MENU)
            .build();

    private @Nullable HeadlessRunner runner;
    private final List<String> log = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (runner != null) {
            runner.stop();
        }
    }

    private TestGame start(Consumer<HeadlessBackend> prepare) {
        TestGame game = new TestGame();
        game.onLoad = () -> {
            for (InputAction action : List.of(JUMP, LEFT, RIGHT, UP, DOWN, CONFIRM)) {
                game.registries().register(Registries.INPUT_ACTION, action);
            }
        };
        runner = Fixtures.started(game, prepare);
        assertThat(runner.engine().isRunning()).isTrue();
        return game;
    }

    private TestGame start() {
        return start(b -> {});
    }

    private HeadlessBackend backend() {
        return runner.backend();
    }

    private void key(int code, boolean down) {
        backend().input().inject(l -> {
            if (down) {
                l.keyDown(code, code + 1000, 0, false);
            } else {
                l.keyUp(code, code + 1000, 0);
            }
        });
    }

    @Test
    void aTapBetweenTicksIsNotLost() {
        TestGame game = start();
        Input input = game.input();
        key(Keys.SPACE.code(), true);
        key(Keys.SPACE.code(), false);
        runner.step(1);
        assertThat(input.justPressed(JUMP)).isTrue();
        assertThat(input.pressed(JUMP)).isTrue();
        assertThat(input.heldTicks(JUMP)).isEqualTo(1);
        assertThat(input.isDown(Keys.SPACE)).isFalse();
        runner.step(1);
        assertThat(input.justReleased(JUMP)).isTrue();
        assertThat(input.pressed(JUMP)).isFalse();
        runner.step(1);
        assertThat(input.justReleased(JUMP)).isFalse();
    }

    @Test
    void holdingCountsTicksAndFiresEvents() {
        TestGame game = start();
        game.on(ActionPressEvent.class, e -> log.add("press " + e.action().key().path()));
        game.on(
                ActionReleaseEvent.class,
                e -> log.add("release " + e.action().key().path() + " " + e.heldTicks()));
        game.on(KeyPressEvent.class, e -> log.add("key " + e.key().name() + " " + e.scanCode()));
        game.on(KeyReleaseEvent.class, e -> log.add("up " + e.key().name()));
        key(Keys.SPACE.code(), true);
        runner.step(3);
        assertThat(game.input().heldTicks(JUMP)).isEqualTo(3);
        assertThat(game.input().strength(JUMP)).isEqualTo(1f);
        assertThat(game.input().isDown(Keys.SPACE)).isTrue();
        key(Keys.SPACE.code(), false);
        runner.step(1);
        assertThat(log).containsExactly("key space 1044", "press jump", "up space", "release jump 3");
        assertThat(game.input().lastDevice()).isEqualTo(InputDevice.KEYBOARD);
    }

    @Test
    void analogBindingsUseDeadZonesAndVectorsAreRound() {
        TestGame game = start();
        Input input = game.input();
        game.on(GamepadConnectEvent.class, e -> log.add("connect " + e.gamepad().name()));
        game.on(
                GamepadDisconnectEvent.class,
                e -> log.add("disconnect " + e.gamepad().index()));
        float[] axes = new float[6];
        axes[0] = -0.1f;
        backend().input().setGamepad(0, "Xbox Wireless Controller", axes, new boolean[17]);
        runner.step(1);
        assertThat(input.axis(LEFT, RIGHT)).isZero();
        assertThat(input.gamepads()).hasSize(1);
        assertThat(input.gamepad(0).family()).isEqualTo(ControllerFamily.XBOX);
        assertThat(input.gamepad(0).rawAxis(GamepadAxis.LEFT_X)).isEqualTo(-0.1f);
        assertThat(input.gamepad(0).axis(GamepadAxis.LEFT_X)).isZero();

        axes[0] = -0.6f;
        backend().input().setGamepad(0, "Xbox Wireless Controller", axes, new boolean[17]);
        runner.step(1);
        assertThat(input.strength(LEFT)).isCloseTo(0.5f, within(1e-4f));
        assertThat(input.axis(LEFT, RIGHT)).isCloseTo(-0.5f, within(1e-4f));
        assertThat(input.pressed(LEFT)).isTrue();
        assertThat(input.gamepad(0).axis(GamepadAxis.LEFT_X)).isCloseTo(-0.529f, within(1e-3f));
        assertThat(input.controllerFamily()).isEqualTo(ControllerFamily.XBOX);
        assertThat(input.lastDevice()).isEqualTo(InputDevice.GAMEPAD);

        boolean[] buttons = new boolean[17];
        buttons[GamepadButton.SOUTH.index()] = true;
        backend().input().setGamepad(0, "Xbox Wireless Controller", new float[6], buttons);
        runner.step(1);
        assertThat(input.justPressed(JUMP)).isTrue();
        assertThat(input.gamepad(0).isDown(GamepadButton.SOUTH)).isTrue();
        assertThat(input.gamepad(0).supportsRumble()).isTrue();
        input.gamepad(0).rumble(0.5f, 1f, 0.2f);
        assertThat(backend().input().rumbleCount()).isEqualTo(1);
        input.gamepad(0).setDeadZone(0.3f);
        assertThat(input.gamepad(0).deadZone()).isEqualTo(0.3f);
        assertThatThrownBy(() -> input.gamepad(0).setDeadZone(1f)).isInstanceOf(IllegalArgumentException.class);
        assertThat(input.gamepad(0).toString()).contains("Xbox");

        key(Keys.D.code(), true);
        key(Keys.S.code(), true);
        runner.step(1);
        Vec2 vector = input.vector(LEFT, RIGHT, UP, DOWN);
        assertThat(vector.length()).isCloseTo(1f, within(1e-4f));
        assertThat(vector.x()).isCloseTo(0.7071f, within(1e-3f));
        assertThat(input.controllerFamily()).isEqualTo(ControllerFamily.KEYBOARD_MOUSE);
        key(Keys.D.code(), false);
        key(Keys.S.code(), false);
        runner.step(1);
        assertThat(input.vector(LEFT, RIGHT, UP, DOWN)).isEqualTo(Vec2.ZERO);

        backend().input().removeGamepad(0);
        runner.step(1);
        assertThat(input.gamepads()).isEmpty();
        assertThat(input.gamepad(0).isConnected()).isFalse();
        assertThat(input.gamepad(0).isDown(GamepadButton.SOUTH)).isFalse();
        assertThat(input.gamepad(0).supportsRumble()).isFalse();
        assertThat(input.maxGamepads()).isEqualTo(4);
        assertThatThrownBy(() -> input.gamepad(9)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThat(log).containsExactly("connect Xbox Wireless Controller", "disconnect 0");
    }

    @Test
    void disabledSetsReadAsReleased() {
        TestGame game = start();
        Input input = game.input();
        key(Keys.SPACE.code(), true);
        key(Keys.ENTER.code(), true);
        runner.step(1);
        assertThat(input.pressed(JUMP)).isTrue();
        input.disable(ActionSet.GAMEPLAY);
        runner.step(1);
        assertThat(input.isEnabled(ActionSet.GAMEPLAY)).isFalse();
        assertThat(input.justReleased(JUMP)).isTrue();
        assertThat(input.pressed(CONFIRM)).isTrue();
        input.enable(ActionSet.GAMEPLAY);
        runner.step(1);
        assertThat(input.justPressed(JUMP)).isTrue();

        game.engine().pause();
        key(Keys.SPACE.code(), false);
        runner.step(1);
        assertThat(input.justReleased(JUMP)).isTrue();
        game.engine().resume();
    }

    @Test
    void rebindingCapturesAndSurvivesRestart() {
        TestGame game = start();
        Input input = game.input();
        AtomicReference<Binding> captured = new AtomicReference<>();
        input.bindings().captureNextInput(captured::set);
        assertThat(input.bindings().isCapturing()).isTrue();
        key(Keys.K.code(), true);
        runner.step(1);
        assertThat(captured.get()).isEqualTo(Keys.K);
        assertThat(input.bindings().isCapturing()).isFalse();

        input.bindings().rebind(JUMP, 0, Keys.K);
        runner.step(1);
        assertThat(input.pressed(JUMP)).as("the captured key waits for release").isFalse();
        key(Keys.K.code(), false);
        runner.step(1);
        key(Keys.K.code(), true);
        runner.step(1);
        assertThat(input.justPressed(JUMP)).isTrue();

        input.bindings().rebind(JUMP, 2, MouseButton.RIGHT);
        input.bindings().unbind(JUMP, 1);
        assertThat(input.bindings().of(JUMP)).containsExactly(Keys.K, null, MouseButton.RIGHT);
        assertThatThrownBy(() -> input.bindings().rebind(JUMP, 5, Keys.L)).isInstanceOf(IllegalArgumentException.class);
        assertThat(input.bindings().conflicts(JUMP, Keys.A)).containsExactly(LEFT);
        assertThat(input.bindings().conflicts(JUMP, Keys.ENTER)).isEmpty();

        input.bindings().captureNextInput(b -> log.add("never"));
        input.bindings().cancelCapture();
        backend().input().inject(l -> l.mouseButton(1, true, 0));
        runner.step(1);
        assertThat(input.pressed(JUMP)).isTrue();
        assertThat(log).isEmpty();
        runner.step(40);

        HeadlessBackend first = backend();
        runner.stop();
        byte[] saved = first.files().userData(PreferencesImpl.FILE);
        assertThat(saved).isNotNull();
        assertThat(new String(saved, java.nio.charset.StandardCharsets.UTF_8)).contains("key:k,,mouse:right");

        TestGame second = start(b -> b.files().writeUserData(PreferencesImpl.FILE, ByteBuffer.wrap(saved), noop()));
        assertThat(second.input().bindings().of(JUMP)).containsExactly(Keys.K, null, MouseButton.RIGHT);
        second.input().bindings().reset(JUMP);
        assertThat(second.input().bindings().of(JUMP)).containsExactly(Keys.SPACE, GamepadButton.SOUTH);
        second.input().bindings().rebind(LEFT, 0, Keys.Q);
        second.input().bindings().resetAll();
        assertThat(second.input().bindings().of(LEFT)).containsExactly(Keys.A, GamepadAxis.LEFT_X.negative());
        assertThat(second.preferences().keys()).noneMatch(k -> k.startsWith("input."));
    }

    @Test
    void captureTakesGamepadButtonsAndSticks() {
        TestGame game = start();
        List<Binding> captured = new ArrayList<>();
        backend().input().setGamepad(1, "DualSense Wireless Controller", new float[6], new boolean[17]);
        runner.step(1);
        game.input().bindings().captureNextInput(captured::add);
        boolean[] buttons = new boolean[17];
        buttons[GamepadButton.NORTH.index()] = true;
        backend().input().setGamepad(1, "DualSense Wireless Controller", new float[6], buttons);
        runner.step(1);
        game.input().bindings().captureNextInput(captured::add);
        float[] axes = new float[6];
        axes[GamepadAxis.RIGHT_Y.index()] = -0.9f;
        backend().input().setGamepad(1, "DualSense Wireless Controller", axes, buttons);
        runner.step(1);
        assertThat(captured).containsExactly(GamepadButton.NORTH, GamepadAxis.RIGHT_Y.negative());
        assertThat(game.input().controllerFamily()).isEqualTo(ControllerFamily.PLAYSTATION);
        assertThat(GamepadButton.SOUTH.glyph().label()).isEqualTo("Cross");
    }

    @Test
    void mouseEventsAreCoalescedPerFrameAndCountedPerTick() {
        TestGame game = start();
        Input input = game.input();
        game.on(MouseMoveEvent.class, e -> log.add("move " + e.x() + " " + e.deltaX()));
        game.on(MouseScrollEvent.class, e -> log.add("scroll " + e.deltaY()));
        game.on(MouseButtonPressEvent.class, e -> log.add("down " + e.button() + " " + e.x() + " " + e.modifiers()));
        game.on(MouseButtonReleaseEvent.class, e -> log.add("up " + e.button() + " " + e.y()));
        backend().input().inject(l -> l.mouseMoved(10, 5, 10, 5));
        backend().input().inject(l -> l.mouseMoved(15, 5, 5, 0));
        backend().input().inject(l -> l.scrolled(0, 2));
        backend().input().inject(l -> l.mouseButton(0, true, 1));
        backend().input().inject(l -> l.mouseButton(9, true, 0));
        runner.step(1);
        assertThat(input.mouseX()).isEqualTo(15f);
        assertThat(input.mouseY()).isEqualTo(5f);
        assertThat(input.mouseDeltaX()).isEqualTo(15f);
        assertThat(input.mouseDeltaY()).isEqualTo(5f);
        assertThat(input.scrollY()).isEqualTo(2f);
        assertThat(input.scrollX()).isZero();
        assertThat(input.isDown(MouseButton.LEFT)).isTrue();
        assertThat(input.pressed(CONFIRM)).isTrue();
        assertThat(input.mouseWorld(game.display().camera())).isNotNull();
        backend().input().inject(l -> l.mouseButton(0, false, 0));
        runner.step(1);
        assertThat(input.mouseDeltaX()).isZero();
        assertThat(input.isDown(MouseButton.LEFT)).isFalse();
        assertThat(log).containsExactly("scroll 2.0", "down LEFT 15.0 1", "move 15.0 15.0", "up LEFT 5.0");
        assertThat(input.lastDevice()).isEqualTo(InputDevice.MOUSE);
    }

    @Test
    void touchesDriveTheMouseAndGestures() {
        TestGame game = start();
        Input input = game.input();
        for (Class<? extends dev.gulp.api.event.Event> type : List.of(
                TapEvent.class,
                DoubleTapEvent.class,
                LongPressEvent.class,
                PanEvent.class,
                PinchEvent.class,
                SwipeEvent.class)) {
            game.on(type, e -> log.add(e.eventName()));
        }
        touch(1, 0, 50, 50);
        runner.step(1);
        assertThat(input.touches()).hasSize(1);
        assertThat(input.touches().get(0).startX()).isEqualTo(50f);
        assertThat(input.touches().get(0).seconds()).isGreaterThanOrEqualTo(0f);
        assertThat(input.isDown(MouseButton.LEFT)).isTrue();
        touch(1, 2, 50, 50);
        runner.step(1);
        assertThat(input.isDown(MouseButton.LEFT)).isFalse();
        touch(1, 0, 52, 50);
        runner.step(1);
        touch(1, 2, 52, 50);
        runner.step(1);
        assertThat(log).containsExactly("TapEvent", "TapEvent", "DoubleTapEvent");

        log.clear();
        touch(2, 0, 10, 10);
        runner.step(40);
        touch(2, 3, 10, 10);
        runner.step(1);
        assertThat(log).containsExactly("LongPressEvent");

        log.clear();
        touch(3, 0, 10, 10);
        runner.step(1);
        touch(3, 1, 150, 10);
        runner.step(1);
        touch(3, 2, 250, 10);
        runner.step(1);
        assertThat(log).containsExactly("PanEvent", "SwipeEvent");

        log.clear();
        touch(4, 0, 100, 100);
        touch(5, 0, 200, 100);
        runner.step(1);
        touch(5, 1, 300, 100);
        runner.step(1);
        assertThat(input.touches()).hasSize(2);
        touch(4, 2, 100, 100);
        touch(5, 2, 300, 100);
        touch(6, 1, 0, 0);
        runner.step(1);
        assertThat(log).containsExactly("PinchEvent");
        assertThat(input.touches()).isEmpty();
    }

    private void touch(int pointer, int phase, float x, float y) {
        backend().input().inject(l -> l.touch(pointer, phase, x, y));
    }

    @Test
    void cursorClipboardTextAndKeyboardText() {
        TestGame game = start();
        Input input = game.input();
        input.setCursor(SystemCursor.HAND);
        assertThat(backend().window().cursorShape()).isEqualTo(1);
        assertThat(input.cursor()).isEqualTo(SystemCursor.HAND);
        input.setCursor(Cursor.custom(new Pixmap(4, 4), 1, 1));
        assertThat(backend().window().customCursor()).isNotNull();
        input.setCursor(Cursor.custom(game.graphics().texture(new Pixmap(2, 2)).region(), 0, 0));
        assertThat(backend().window().customCursor().width()).isEqualTo(2);
        assertThatThrownBy(() -> input.setCursor(new Cursor.Custom(null, null, 0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        input.setCursorMode(CursorMode.CAPTURED);
        assertThat(input.cursorMode()).isEqualTo(CursorMode.CAPTURED);
        assertThat(backend().window().cursorMode()).isEqualTo(dev.gulp.platform.CursorMode.CAPTURED);

        input.clipboard().set("seed-42");
        List<String> read = new ArrayList<>();
        input.clipboard().get().thenSync(read::add);
        runner.step(1);
        assertThat(read).containsExactly("seed-42");

        input.startTextInput(new Rect(10, 10, 100, 20));
        assertThat(input.isTextInputActive()).isTrue();
        assertThat(backend().input().isTextInputActive()).isTrue();
        game.on(CharTypedEvent.class, e -> log.add(e.text() + e.codePoint()));
        game.on(
                KeyRepeatEvent.class,
                e -> log.add("repeat " + e.isShiftDown() + e.isControlDown() + e.isAltDown() + e.isSuperDown()
                        + e.modifiers()));
        backend().input().inject(l -> l.textTyped('ż'));
        backend().input().inject(l -> l.keyDown(Keys.BACKSPACE.code(), 14, 3, true));
        runner.step(1);
        input.stopTextInput();
        assertThat(input.isTextInputActive()).isFalse();
        assertThat(log).containsExactly("ż380", "repeat truetruefalsefalse3");
        assertThat(input.touches()).isEmpty();
    }

    private static <T> dev.gulp.platform.PlatformCallback<T> noop() {
        return new dev.gulp.platform.PlatformCallback<>() {
            @Override
            public void success(T value) {}

            @Override
            public void failure(Throwable error) {}
        };
    }
}
