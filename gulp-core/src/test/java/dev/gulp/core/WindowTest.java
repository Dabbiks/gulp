package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.render.GameWindow;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WindowTest {

    private HeadlessRunner runner;

    @AfterEach
    void tearDown() {
        runner.stop();
    }

    @Test
    void startSettingsAndRuntimeChangesReachThePlatformWindow() {
        TestGame game = new TestGame();
        game.configure = s -> s.title("Start").borderless(true).monitor(1).vsync(true);
        runner = HeadlessRunner.start(game);
        runner.step(1);
        GameWindow window = game.display().window();
        assertThat(window.title()).isEqualTo("Start");
        assertThat(window.isBorderless()).isTrue();
        assertThat(runner.backend().window().isBorderless()).isTrue();
        assertThat(window.monitors()).hasSize(2);
        assertThat(window.monitor()).isEqualTo(1);
        assertThat(window.isVsync()).isTrue();
        assertThat(window.width()).isPositive();
        assertThat(window.height()).isPositive();

        window.setTitle("Running");
        window.setFullscreen(true);
        window.setVsync(false);
        window.setBorderless(false);
        window.setMonitor(0);
        window.setSize(800, 600);
        assertThat(window.title()).isEqualTo("Running");
        assertThat(window.isFullscreen()).isTrue();
        assertThat(runner.backend().window().isVsync()).isFalse();
        assertThat(window.isVsync()).isFalse();
        assertThat(window.isBorderless()).isFalse();
        assertThat(window.monitor()).isZero();
        assertThat(window.isFocused()).isTrue();
        assertThatThrownBy(() -> window.setMonitor(5)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> window.setSize(0, 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
