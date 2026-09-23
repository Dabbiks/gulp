package dev.gulp.core;

import static dev.gulp.core.Fixtures.LOG;
import static dev.gulp.core.Fixtures.started;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.Gulp;
import dev.gulp.api.event.EventPriority;
import dev.gulp.api.event.lifecycle.FocusGainedEvent;
import dev.gulp.api.event.lifecycle.FocusLostEvent;
import dev.gulp.api.event.lifecycle.GameStartEvent;
import dev.gulp.api.event.lifecycle.GameStopEvent;
import dev.gulp.api.event.lifecycle.PauseEvent;
import dev.gulp.api.event.lifecycle.ResumeEvent;
import dev.gulp.api.event.lifecycle.TickEndEvent;
import dev.gulp.api.event.lifecycle.TickStartEvent;
import dev.gulp.api.event.lifecycle.WindowResizeEvent;
import dev.gulp.api.graphics.Color;
import dev.gulp.backend.headless.HeadlessLoop;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EngineTest {

    private HeadlessRunner runner;

    @BeforeEach
    void clearLog() {
        LOG.clear();
    }

    @AfterEach
    void stop() {
        if (runner != null) {
            runner.stop();
        }
    }

    @Test
    void runsLifecycleAndBindsEngine() {
        TestGame game = new TestGame();
        game.onStart = () -> game.on(GameStartEvent.class, e -> LOG.add("GameStartEvent"));
        runner = HeadlessRunner.start(game);

        assertThat(LOG).isEmpty();
        assertThat(Gulp.isRunning()).isTrue();
        assertThat(runner.engine().isRunning()).isFalse();

        runner.step(1);
        assertThat(LOG).containsExactly("game.onLoad", "game.onStart", "GameStartEvent");
        assertThat(Gulp.engine()).isSameAs(runner.engine());
        assertThat(runner.engine().game()).isSameAs(game);
        assertThat(game.isEnabled()).isTrue();
        assertThat(runner.engine().isRunning()).isTrue();
        assertThat(runner.engine().registries().isFrozen()).isTrue();

        game.on(GameStopEvent.class, e -> LOG.add("GameStopEvent"));
        runner.stop();
        runner = null;
        assertThat(LOG).endsWith("GameStopEvent", "game.onStop");
        assertThat(Gulp.isRunning()).isFalse();
        assertThat(game.isEnabled()).isFalse();
        assertThatThrownBy(Gulp::engine).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void runsOneTickPerFrameAtMatchingRates() {
        runner = started(new TestGame());
        GulpEngine engine = runner.engine();

        assertThat(engine.tick()).isZero();
        runner.step(10);

        assertThat(engine.tick()).isEqualTo(10);
        assertThat(engine.realTick()).isEqualTo(10);
        assertThat(engine.targetTps()).isEqualTo(60);
        assertThat(engine.frameCount()).isEqualTo(11);
        // Two clears a frame: the letterbox, then the game area.
        assertThat(runner.backend().gl().clearCount()).isEqualTo(22);
    }

    @Test
    void fixedStepAccumulatesAndCapsCatchUp() {
        runner = started(new TestGame());
        GulpEngine engine = runner.engine();
        HeadlessLoop loop = runner.backend().loop();
        long tick = 1_000_000_000L / 60;

        loop.setFrameNanos(tick * 3 / 2);
        runner.step(1);
        assertThat(engine.tick()).isEqualTo(1);
        assertThat(engine.alpha()).isBetween(0.49f, 0.51f);
        runner.step(1);
        assertThat(engine.tick()).isEqualTo(3);

        loop.setFrameNanos(1_000_000_000L); // one second: clamped to 250 ms, then capped at 5 ticks
        runner.step(1);
        assertThat(engine.tick()).isEqualTo(3 + GulpEngine.MAX_CATCH_UP_TICKS);
        assertThat(engine.alpha()).isBetween(0f, 1f);
    }

    @Test
    void timeScaleChangesGameTicksButNotRealTicks() {
        runner = started(new TestGame());
        GulpEngine engine = runner.engine();

        engine.setTimeScale(2f);
        runner.step(3);
        assertThat(engine.tick()).isEqualTo(6);
        assertThat(engine.realTick()).isEqualTo(3);

        engine.setTimeScale(0.5f);
        runner.step(4);
        assertThat(engine.tick()).isEqualTo(8);
        assertThat(engine.timeScale()).isEqualTo(0.5f);

        assertThatThrownBy(() -> engine.setTimeScale(-1f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.setTimeScale(11f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.setTimeScale(Float.NaN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pauseStopsGameTicksAndFiresEvents() {
        TestGame game = new TestGame();
        runner = started(game);
        GulpEngine engine = runner.engine();
        game.on(PauseEvent.class, e -> LOG.add("pause"));
        game.on(ResumeEvent.class, e -> LOG.add("resume"));

        engine.pause();
        engine.pause();
        runner.step(5);
        assertThat(engine.isPaused()).isTrue();
        assertThat(engine.tick()).isZero();
        assertThat(engine.realTick()).isEqualTo(5);

        engine.resume();
        engine.resume();
        runner.step(2);
        assertThat(engine.tick()).isEqualTo(2);
        assertThat(LOG).containsSubsequence("pause", "resume");
        assertThat(LOG).filteredOn("pause"::equals).hasSize(1);
    }

    @Test
    void measuresTicksPerSecond() {
        runner = started(new TestGame());
        GulpEngine engine = runner.engine();

        assertThat(engine.tps()).isEqualTo(60f);
        runner.backend().loop().setFrameNanos(1_000_000_000L / 30);
        runner.step(61);
        assertThat(engine.tps())
                .as("fixed step: frame rate does not change TPS")
                .isBetween(59f, 61f);

        engine.setTimeScale(0.5f);
        runner.step(62); // the next full one-second window at half speed
        assertThat(engine.tps()).as("half-speed game time").isBetween(29f, 31f);
    }

    @Test
    void tickEventsCarryTheTickNumber() {
        TestGame game = new TestGame();
        runner = started(game);
        List<Long> ticks = new ArrayList<>();
        game.on(TickStartEvent.class, e -> ticks.add(e.tick()));
        game.on(TickEndEvent.class, e -> ticks.add(-e.tick()));

        runner.step(2);

        assertThat(ticks).containsExactly(1L, -1L, 2L, -2L);
    }

    @Test
    void windowChangesBecomeEvents() {
        TestGame game = new TestGame();
        runner = started(game);
        game.on(WindowResizeEvent.class, e -> LOG.add("resize " + e.width() + "x" + e.height()));
        game.on(FocusLostEvent.class, e -> LOG.add("focus lost"));
        game.on(FocusGainedEvent.class, e -> LOG.add("focus gained"));

        runner.backend().window().simulateResize(640, 360, 2f);
        runner.backend().window().simulateFocus(false);
        runner.backend().window().simulateFocus(true);
        runner.step(1);

        assertThat(LOG).containsSubsequence("resize 640x360", "focus lost", "focus gained");
        assertThat(runner.backend().gl().viewport()).containsExactly(0, 0, 1280, 720);
    }

    @Test
    void clearsWithTheConfiguredColor() {
        TestGame game = new TestGame();
        game.configure = s -> s.clearColor(new Color(0.25f, 0.5f, 0.75f, 1f));
        runner = started(game);

        assertThat(runner.backend().gl().lastClearColor()).containsExactly(0.25f, 0.5f, 0.75f, 1f);
    }

    @Test
    void stopAndWindowCloseEndTheLoop() {
        runner = started(new TestGame());
        runner.engine().stop();

        assertThat(runner.step(5)).isEqualTo(1);
        assertThat(runner.engine().isStopped()).isTrue();
        assertThat(LOG).contains("game.onStop");
        runner = null;

        LOG.clear();
        HeadlessRunner second = started(new TestGame());
        second.backend().window().simulateCloseRequest();
        second.step(3);
        assertThat(second.isRunning()).isFalse();
        assertThat(LOG).contains("game.onStop");
    }

    @Test
    void failedStartStopsTheGameAndReportsTheCause() {
        TestGame game = new TestGame();
        game.onStart = () -> {
            throw new IllegalStateException("boom");
        };
        runner = HeadlessRunner.start(game);

        assertThatThrownBy(() -> runner.step(1))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("boom");
        assertThat(runner.engine().isStopped()).isTrue();
        assertThat(runner.engine().failure()).hasMessage("boom");
        assertThat(LOG).contains("game.onStop");
        assertThat(Gulp.isRunning()).isFalse();
        runner = null;
    }

    @Test
    void rejectsInvalidGameIds() {
        assertThatThrownBy(() -> HeadlessRunner.start(new TestGame("Bad Id")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid game id");
        assertThatThrownBy(() -> HeadlessRunner.start(new TestGame("gulp"))).isInstanceOf(IllegalStateException.class);
        assertThat(Gulp.isRunning()).isFalse();
    }

    @Test
    void onlyOneEngineRunsAtATime() {
        runner = started(new TestGame());

        assertThatThrownBy(() -> HeadlessRunner.start(new TestGame()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already running");
        assertThatThrownBy(() -> runner.engine().start()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void engineApiIsMainThreadOnlyInDevelopment() throws InterruptedException {
        TestGame game = new TestGame();
        runner = started(game);
        AtomicReference<Throwable> thrown = new AtomicReference<>();

        Thread thread = new Thread(
                () -> {
                    try {
                        runner.engine().events().call(new Fixtures.PingEvent());
                    } catch (Throwable t) {
                        thrown.set(t);
                    }
                },
                "worker");
        thread.start();
        thread.join();

        assertThat(thrown.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Events.call")
                .hasMessageContaining("worker");
    }

    @Test
    void platformDescribesTheBackend() {
        runner = started(new TestGame());

        assertThat(runner.engine().platform().backend()).isEqualTo("headless");
        assertThat(runner.engine().platform().osName()).isEqualTo("headless");
        assertThat(runner.engine().platform().architecture()).isEqualTo("headless");
        assertThat(runner.engine().platform().isWeb()).isFalse();
        assertThat(runner.engine().platform().isDevelopment()).isTrue();
        assertThat(runner.engine().platform().systemLocale()).isEqualTo("en-US");
        assertThat(runner.engine().logger().name()).isEqualTo("gulp");
    }

    @Test
    void postedWorkRunsBeforeTheNextFrame() {
        runner = started(new TestGame());
        runner.engine().post(() -> LOG.add("posted"));
        runner.engine().post(() -> {
            throw new IllegalStateException("posted failure");
        });

        runner.step(1);

        assertThat(LOG).contains("posted");
        assertThat(runner.backend().log().messages(4)).contains("Unhandled exception in main-thread work");
    }

    @Test
    void handlerPriorityShortcutWorks() {
        TestGame game = new TestGame();
        runner = started(game);
        List<String> order = new ArrayList<>();
        game.on(Fixtures.PingEvent.class, EventPriority.HIGH, e -> order.add("high"));
        game.on(Fixtures.PingEvent.class, e -> order.add("normal"));

        runner.engine().events().call(new Fixtures.PingEvent());

        assertThat(order).containsExactly("normal", "high");
    }
}
