package dev.gulp.core;

import dev.gulp.api.input.GamepadButton;
import dev.gulp.api.input.KeyboardKey;
import dev.gulp.api.ui.Node;
import dev.gulp.api.ui.Screen;
import dev.gulp.api.ui.Ui;
import dev.gulp.backend.headless.HeadlessBackend;
import dev.gulp.core.Fixtures.TestGame;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shared setup of the UI tests: a running headless game with text, and helpers that press keys, buttons and pads. */
abstract class UiFixture extends JuiceFixture {

    TestGame game;
    Ui ui;

    TestGame startUi() {
        return startUi(settings -> {}, backend -> {});
    }

    TestGame startUi(Consumer<dev.gulp.api.GameSettings> settings, Consumer<HeadlessBackend> prepare) {
        TestGame created = new TestGame();
        created.configure = s -> {
            s.windowSize(1280, 720);
            settings.accept(s);
        };
        runner = dev.gulp.backend.headless.HeadlessRunner.start(created, b -> {
            b.files().useClasspathAssets(true);
            prepare.accept(b);
        });
        for (int i = 0; i < 20 && !runner.engine().isRunning(); i++) {
            runner.step(1);
        }
        game = created;
        ui = created.engine().ui();
        return created;
    }

    /** A screen made from a supplier of its tree. */
    static final class TestScreen extends Screen {
        private final Supplier<Node<?>> content;
        int opened;
        int closed;
        int backs;

        TestScreen(Supplier<Node<?>> content) {
            this.content = content;
            enter(dev.gulp.api.ui.ScreenTransition.NONE).exit(dev.gulp.api.ui.ScreenTransition.NONE);
        }

        @Override
        protected Node<?> build() {
            return content.get();
        }

        @Override
        protected void onOpen() {
            opened++;
        }

        @Override
        protected void onClose() {
            closed++;
        }

        @Override
        protected void onBack() {
            backs++;
            super.onBack();
        }
    }

    TestScreen open(Node<?> root) {
        TestScreen screen = new TestScreen(() -> root);
        ui.open(screen);
        step(1);
        return screen;
    }

    HeadlessBackend backend() {
        return runner.backend();
    }

    void move(float x, float y) {
        backend().input().inject(l -> l.mouseMoved(x, y, 0f, 0f));
        step(1);
    }

    void press(float x, float y, int button) {
        backend().input().inject(l -> l.mouseMoved(x, y, 0f, 0f));
        backend().input().inject(l -> l.mouseButton(button, true, 0));
        step(1);
    }

    void release(float x, float y, int button) {
        backend().input().inject(l -> l.mouseMoved(x, y, 0f, 0f));
        backend().input().inject(l -> l.mouseButton(button, false, 0));
        step(1);
    }

    void click(float x, float y) {
        press(x, y, 0);
        release(x, y, 0);
    }

    void click(Node<?> node) {
        click(node.x() + node.width() / 2f, node.y() + node.height() / 2f);
    }

    void rightClick(Node<?> node) {
        float x = node.x() + node.width() / 2f;
        float y = node.y() + node.height() / 2f;
        press(x, y, 1);
        release(x, y, 1);
    }

    void key(KeyboardKey key) {
        key(key, 0);
    }

    void key(KeyboardKey key, int modifiers) {
        backend().input().inject(l -> l.keyDown(key.code(), 0, modifiers, false));
        step(1);
        backend().input().inject(l -> l.keyUp(key.code(), 0, modifiers));
        step(1);
    }

    void type(String text) {
        for (int i = 0; i < text.length(); i++) {
            int cp = text.charAt(i);
            backend().input().inject(l -> l.textTyped(cp));
        }
        step(1);
    }

    void wheel(float x, float y, float dy) {
        backend().input().inject(l -> l.mouseMoved(x, y, 0f, 0f));
        backend().input().inject(l -> l.scrolled(0f, dy));
        step(1);
    }

    void pad(GamepadButton button) {
        boolean[] buttons = new boolean[17];
        buttons[button.index()] = true;
        backend().input().setGamepad(0, "Xbox Wireless Controller", new float[6], buttons);
        step(1);
        backend().input().setGamepad(0, "Xbox Wireless Controller", new float[6], new boolean[17]);
        step(1);
    }
}
