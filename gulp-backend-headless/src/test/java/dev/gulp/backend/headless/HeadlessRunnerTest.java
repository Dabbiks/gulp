package dev.gulp.backend.headless;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.Gulp;
import dev.gulp.platform.FrameHandler;
import org.junit.jupiter.api.Test;

class HeadlessRunnerTest {

    @Test
    void stepsFramesWithSimulatedTime() {
        TestGame game = new TestGame();
        HeadlessRunner runner = HeadlessRunner.start(game);

        assertThat(game.calls).as("the game starts on the first frame").isEmpty();
        assertThat(runner.step(60)).isEqualTo(60);
        assertThat(game.calls).containsExactly("onStart");
        assertThat(runner.backend().loop().nanoTime()).isEqualTo(60 * HeadlessLoop.DEFAULT_FRAME_NANOS);
        assertThat(runner.engine().frameCount()).isEqualTo(60);
        assertThat(runner.settings().windowWidth()).isEqualTo(320);
        assertThat(runner.isRunning()).isTrue();

        runner.stop();
        runner.stop();

        assertThat(runner.isRunning()).isFalse();
        assertThat(game.calls).containsExactly("onStart", "onStop");
        assertThatThrownBy(() -> runner.step(1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void customFrameDuration() {
        HeadlessRunner runner = HeadlessRunner.start(new TestGame());
        runner.backend().loop().setFrameNanos(1_000);

        runner.step(5);

        assertThat(runner.backend().loop().frameNanos()).isEqualTo(1_000);
        assertThat(runner.backend().loop().nanoTime()).isEqualTo(5_000);
        assertThatThrownBy(() -> runner.backend().loop().setFrameNanos(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runner.step(-1)).isInstanceOf(IllegalArgumentException.class);
        runner.stop();
    }

    @Test
    void loopRejectsSecondRun() {
        HeadlessRunner runner = HeadlessRunner.start(new TestGame());

        assertThatThrownBy(() -> runner.backend().loop().run(new StopAfter(1)))
                .isInstanceOf(IllegalStateException.class);
        runner.stop();
    }

    @Test
    void stopInsideFrameEndsAfterThatFrame() {
        HeadlessBackend backend = backend();
        StopAfter handler = new StopAfter(Integer.MAX_VALUE) {
            @Override
            public boolean frame(long nanoTime) {
                super.frame(nanoTime);
                backend.loop().stop();
                return true;
            }
        };
        backend.loop().run(handler);

        assertThat(backend.loop().step(5)).isEqualTo(1);
        assertThat(handler.exited).isTrue();
    }

    @Test
    void runUntilStoppedHonoursHandlerAndLimit() {
        HeadlessBackend stopsItself = backend();
        StopAfter handler = new StopAfter(3);
        stopsItself.loop().run(handler);
        assertThat(stopsItself.loop().runUntilStopped(0)).isEqualTo(3);
        assertThat(handler.exited).isTrue();

        HeadlessBackend limited = backend();
        StopAfter endless = new StopAfter(Integer.MAX_VALUE);
        limited.loop().run(endless);
        assertThat(limited.loop().runUntilStopped(10)).isEqualTo(10);
        assertThat(endless.exited).isTrue();
        assertThat(limited.loop().isRunning()).isFalse();
    }

    @Test
    void stopWithoutRunIsIgnored() {
        HeadlessBackend backend = backend();

        backend.loop().stop();

        assertThat(backend.loop().isRunning()).isFalse();
    }

    @Test
    void gulpLaunchUsesHeadlessLauncher() {
        HeadlessGameLauncher launcher = new HeadlessGameLauncher();
        assertThat(launcher.name()).isEqualTo("headless");
        assertThat(launcher.priority()).isZero();

        String previous = System.getProperty(HeadlessGameLauncher.MAX_FRAMES_PROPERTY);
        System.setProperty(HeadlessGameLauncher.MAX_FRAMES_PROPERTY, "30");
        try {
            TestGame game = new TestGame();
            Gulp.launch(game);
            assertThat(game.calls).containsExactly("onStart", "onStop");
        } finally {
            if (previous == null) {
                System.clearProperty(HeadlessGameLauncher.MAX_FRAMES_PROPERTY);
            } else {
                System.setProperty(HeadlessGameLauncher.MAX_FRAMES_PROPERTY, previous);
            }
        }
    }

    static HeadlessBackend backend() {
        return new HeadlessBackend(new dev.gulp.platform.WindowConfig("t", 100, 50, true, false, true));
    }

    static class StopAfter implements FrameHandler {
        private final int frames;
        int ran;
        boolean exited;

        StopAfter(int frames) {
            this.frames = frames;
        }

        @Override
        public boolean frame(long nanoTime) {
            ran++;
            return ran < frames;
        }

        @Override
        public void exit() {
            exited = true;
        }
    }
}
