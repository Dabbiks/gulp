package dev.gulp.core;

import static dev.gulp.core.Fixtures.started;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.scheduler.Promise;
import dev.gulp.api.scheduler.Scheduler;
import dev.gulp.api.scheduler.Sequence;
import dev.gulp.api.scheduler.Task;
import dev.gulp.api.scheduler.TaskRunnable;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import dev.gulp.platform.PlatformLog;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchedulerTest {

    private final TestGame game = new TestGame();
    private final List<Long> runs = new ArrayList<>();
    private HeadlessRunner runner;
    private GulpEngine engine;

    @BeforeEach
    void start() {
        runner = started(game);
        engine = runner.engine();
    }

    @AfterEach
    void stop() {
        runner.stop();
    }

    private Runnable record() {
        return () -> runs.add(engine.tick());
    }

    @Test
    void runLaterAndEveryUseTicks() {
        Task next = game.run(record());
        Task later = game.later(3, record());
        Task repeating = game.every(2, record());
        Task delayed = engine.scheduler().every(0, 4, () -> runs.add(-engine.tick()));

        runner.step(6);

        assertThat(runs).containsExactly(1L, 0L - 1, 2L, 3L, 4L, -5L, 6L);
        assertThat(next.isDone()).isTrue();
        assertThat(next.isCancelled()).isFalse();
        assertThat(later.isDone()).isTrue();
        assertThat(repeating.isDone()).isFalse();
        assertThat(repeating.isRepeating()).isTrue();
        assertThat(repeating.isRealtime()).isFalse();
        assertThat(repeating.owner()).isSameAs(game);
        assertThat(delayed.owner()).isSameAs(game);
    }

    @Test
    void durationsRoundUpToTicks() {
        game.later(Duration.ofMillis(100), record()); // 6 ticks at 60 TPS
        game.every(Duration.ofMillis(1), () -> runs.add(-engine.tick())); // rounds up to 1 tick
        engine.scheduler().every(Duration.ofMillis(50), Duration.ofSeconds(1), () -> runs.add(1000 + engine.tick()));
        engine.scheduler().later(Duration.ZERO, () -> runs.add(500L));

        runner.step(6);

        assertThat(runs).contains(6L, 500L, 1003L).contains(-1L, -6L);
        assertThatThrownBy(() -> game.later(Duration.ofSeconds(-1), () -> {}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> game.later(-1, () -> {})).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> game.every(0, () -> {})).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancelStopsTasksEvenFromInside() {
        AtomicInteger count = new AtomicInteger();
        Task[] self = new Task[1];
        self[0] = game.every(1, () -> {
            if (count.incrementAndGet() == 3) {
                self[0].cancel();
            }
        });
        Task other = game.later(2, record());
        other.cancel();

        runner.step(10);

        assertThat(count).hasValue(3);
        assertThat(self[0].isCancelled()).isTrue();
        assertThat(runs).isEmpty();
    }

    @Test
    void pausedGameStopsOrdinaryTasksButNotRealtimeOnes() {
        AtomicInteger ordinary = new AtomicInteger();
        AtomicInteger realtime = new AtomicInteger();
        game.every(1, ordinary::incrementAndGet);
        Scheduler real = engine.scheduler().realtime().owner(game);
        Task realTask = real.every(1, realtime::incrementAndGet);

        engine.pause();
        runner.step(5);
        assertThat(ordinary).hasValue(0);
        assertThat(realtime).hasValue(5);
        assertThat(realTask.isRealtime()).isTrue();

        engine.resume();
        engine.setTimeScale(2f);
        runner.step(2);
        assertThat(ordinary).hasValue(4);
        assertThat(realtime).hasValue(7);
    }

    @Test
    void taskFailingThreeTimesInARowIsCancelled() {
        AtomicInteger attempts = new AtomicInteger();
        Task task = game.every(1, () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("broken task");
        });
        AtomicInteger flaky = new AtomicInteger();
        game.every(1, () -> {
            if (flaky.incrementAndGet() % 2 == 0) {
                throw new IllegalStateException("every other time");
            }
        });

        runner.step(10);

        assertThat(attempts).hasValue(SchedulerImpl3.MAX);
        assertThat(task.isCancelled()).isTrue();
        assertThat(flaky).hasValue(10);
        assertThat(runner.backend().log().messages(PlatformLog.ERROR))
                .anyMatch(m -> m.contains("failed 3 times in a row"));
    }

    @Test
    void taskRunnableCanCancelItself() {
        List<Integer> countdown = new ArrayList<>();
        TaskRunnable timer = new TaskRunnable() {
            int seconds = 3;

            @Override
            public void run() {
                countdown.add(seconds--);
                if (seconds == 0) {
                    cancel();
                }
            }
        };
        assertThat(timer.isCancelled()).isFalse();
        assertThatThrownBy(timer::cancel).isInstanceOf(IllegalStateException.class);

        Task task = timer.runEvery(game, 2);
        assertThatThrownBy(() -> timer.runNext(game)).isInstanceOf(IllegalStateException.class);
        runner.step(10);

        assertThat(countdown).containsExactly(3, 2, 1);
        assertThat(timer.isCancelled()).isTrue();
        assertThat(task.isCancelled()).isTrue();

        List<String> once = new ArrayList<>();
        TaskRunnable single = new TaskRunnable() {
            @Override
            public void run() {
                once.add("ran");
            }
        };
        single.runLater(game, 2);
        runner.step(2);
        single.runNext(game);
        runner.step(1);
        assertThat(once).containsExactly("ran", "ran");
    }

    @Test
    void schedulingFromAnotherThreadIsAllowed() throws InterruptedException {
        Thread worker = new Thread(() -> game.run(record()));
        worker.start();
        worker.join();

        runner.step(1);

        assertThat(runs).containsExactly(1L);
    }

    @Test
    void sequencesRunStepsAcrossTicks() {
        List<String> steps = new ArrayList<>();
        boolean[] ready = {false};
        Task task = engine.scheduler()
                .sequence()
                .run(() -> steps.add("open@" + engine.tick()))
                .waitTicks(2)
                .run(() -> steps.add("wait-done@" + engine.tick()))
                .waitUntil(() -> ready[0])
                .run(() -> steps.add("ready@" + engine.tick()))
                .wait(Duration.ofMillis(34))
                .run(() -> steps.add("end@" + engine.tick()))
                .repeat(2)
                .start(game);

        runner.step(5);
        ready[0] = true;
        runner.step(20);

        assertThat(steps)
                .containsExactly(
                        "open@1", "wait-done@3", "ready@6", "end@9", "open@9", "wait-done@11", "ready@11", "end@14");
        assertThat(task.isCancelled()).isTrue();
    }

    @Test
    void sequencesRepeatForeverAndValidate() {
        AtomicInteger runsCount = new AtomicInteger();
        Sequence forever =
                engine.scheduler().sequence().run(runsCount::incrementAndGet).repeatForever();
        Task task = forever.start(game);

        runner.step(3);
        assertThat(runsCount.get()).isBetween(3, 6);
        task.cancel();

        assertThatThrownBy(() -> forever.run(() -> {})).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> forever.start(game)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> engine.scheduler().sequence().start(game)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> engine.scheduler().sequence().repeat(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.scheduler().sequence().waitTicks(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> engine.scheduler().sequence().wait(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void asyncResultsComeBackOnTheMainThread() {
        List<String> results = new ArrayList<>();
        Promise<Integer> promise = engine.scheduler().async(() -> 6 * 7);
        promise.thenSync(value -> results.add("value " + value));
        Promise<String> mapped = promise.map(value -> "mapped " + value);
        mapped.thenSync(results::add);

        assertThat(promise.isDone()).isFalse();
        runner.step(1);

        assertThat(results).containsExactly("value 42", "mapped 42");
        assertThat(promise.isDone()).isTrue();
        assertThat(promise.isFailed()).isFalse();

        promise.thenSync(value -> results.add("late " + value));
        promise.onFailure(error -> results.add("never"));
        runner.step(1);
        assertThat(results).endsWith("late 42");
    }

    @Test
    void asyncFailuresReachFailureCallbacks() {
        List<String> results = new ArrayList<>();
        Promise<Integer> failing = engine.scheduler().async(() -> {
            throw new IllegalStateException("no path");
        });
        failing.onFailure(error -> results.add("failed: " + error.getMessage()));
        failing.thenSync(value -> results.add("never"));
        Promise<Integer> mappedFailure = engine.scheduler().async(() -> 1).map(value -> {
            throw new IllegalArgumentException("bad map");
        });
        mappedFailure.onFailure(error -> results.add("map failed: " + error.getMessage()));
        engine.scheduler().async(() -> {
            throw new IllegalStateException("nobody listens");
        });

        runner.step(1);

        assertThat(results).containsExactlyInAnyOrder("failed: no path", "map failed: bad map");
        assertThat(failing.isFailed()).isTrue();
        assertThat(runner.backend().log().messages(PlatformLog.ERROR))
                .contains("Unhandled failure of an asynchronous operation");

        failing.onFailure(error -> results.add("late failure"));
        failing.thenSync(value -> results.add("never"));
        runner.step(1);
        assertThat(results).contains("late failure").doesNotContain("never");
    }

    @Test
    void callbackExceptionsAreLogged() {
        engine.scheduler().async(() -> 1).thenSync(value -> {
            throw new IllegalStateException("callback broke");
        });

        runner.step(1);

        assertThat(runner.backend().log().messages(PlatformLog.ERROR))
                .contains("Unhandled exception in a promise callback");
    }

    /** Keeps the constant reference readable in assertions. */
    private static final class SchedulerImpl3 {
        static final int MAX = dev.gulp.core.scheduler.SchedulerImpl.MAX_CONSECUTIVE_ERRORS;
    }
}
