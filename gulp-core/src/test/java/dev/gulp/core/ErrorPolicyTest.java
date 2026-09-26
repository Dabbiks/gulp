package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.event.lifecycle.TickEndEvent;
import dev.gulp.api.graphics.Shader;
import dev.gulp.api.module.ModuleState;
import dev.gulp.api.registry.Key;
import dev.gulp.api.scheduler.Task;
import dev.gulp.api.text.Font;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;
import dev.gulp.backend.headless.HeadlessLog;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.BaseModule;
import dev.gulp.core.Fixtures.MiddleModule;
import dev.gulp.core.Fixtures.TestGame;
import dev.gulp.core.log.CrashReport;
import dev.gulp.core.log.CrashScreen;
import dev.gulp.core.log.RecentLog;
import dev.gulp.platform.PlatformLog;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Every row of the error policy table in section 20.3. */
class ErrorPolicyTest {

    /** Throws on every tick. */
    static final class Broken extends Component {
        int ticks;

        @Override
        protected void onTick() {
            ticks++;
            throw new IllegalStateException("broken on purpose");
        }
    }

    /** Fails twice, then works: failures must be in a row to disable it. */
    static final class Flaky extends Component {
        int ticks;

        @Override
        protected void onTick() {
            ticks++;
            if (ticks % 3 != 0) {
                throw new IllegalStateException("flaky");
            }
        }
    }

    static final EntityType BROKEN = EntityType.builder(Key.of("test", "broken"))
            .component(Broken::new)
            .component(Flaky::new)
            .build();

    private @Nullable HeadlessRunner runner;

    @AfterEach
    void tearDown() {
        Fixtures.LOG.clear();
        if (runner != null) {
            runner.stop();
        }
    }

    private List<String> errors() {
        return runner.backend().log().messages(PlatformLog.ERROR);
    }

    @Test
    void listenerExceptionsAreLoggedWithTheirOwnerAndOthersStillRun() {
        BaseModule module = new BaseModule();
        List<String> delivered = new ArrayList<>();
        module.onEnableHook = () -> module.on(TickEndEvent.class, e -> {
            throw new IllegalStateException("listener broke");
        });
        TestGame game = new TestGame().modules(module);
        runner = Fixtures.started(game);
        game.on(TickEndEvent.class, e -> delivered.add("tick"));
        runner.step(3);
        assertThat(delivered).hasSize(3);
        assertThat(errors()).anyMatch(m -> m.contains("TickEndEvent") && m.contains("(base)"));
    }

    @Test
    void tasksAndComponentsStopAfterThreeFailuresInARow() {
        TestGame game = new TestGame();
        runner = Fixtures.started(game);
        AtomicInteger runs = new AtomicInteger();
        Task task = game.every(1, () -> {
            runs.incrementAndGet();
            throw new IllegalStateException("task broke");
        });
        World world = game.worlds().create("errors", WorldSettings.DEFAULT);
        game.worlds().switchTo("errors");
        Entity entity = world.spawn(BROKEN, 0, 0);
        runner.step(10);

        assertThat(task.isCancelled()).isTrue();
        assertThat(runs).hasValue(3);
        Broken broken = entity.get(Broken.class);
        assertThat(broken.isEnabled()).isFalse();
        assertThat(broken.ticks).isEqualTo(3);
        Flaky flaky = entity.get(Flaky.class);
        assertThat(flaky.isEnabled()).as("two failures, then a good tick").isTrue();
        assertThat(flaky.ticks).isGreaterThan(3);
        assertThat(errors()).anyMatch(m -> m.contains("3 times in a row") && m.contains("cancelled"));
        assertThat(errors()).anyMatch(m -> m.contains("Broken failed 3 times in a row") && m.contains("disabled"));
        assertThat(game.worlds().active()).isSameAs(world);
    }

    @Test
    void aModuleFailingToEnableIsFailedAndItsDependentsStayOff() {
        BaseModule base = new BaseModule();
        MiddleModule middle = new MiddleModule();
        base.onEnableHook = () -> {
            throw new IllegalStateException("enable broke");
        };
        TestGame game = new TestGame().modules(base, middle);
        runner = Fixtures.started(game);
        runner.step(2);
        assertThat(game.modules().state("base")).isEqualTo(ModuleState.FAILED);
        assertThat(game.modules().isEnabled("middle")).isFalse();
        assertThat(runner.engine().isRunning()).isTrue();
    }

    @Test
    void missingAssetsBecomePlaceholders() {
        TestGame game = new TestGame();
        runner = HeadlessRunner.start(game, b -> b.files().useClasspathAssets(true));
        for (int i = 0; i < 20 && !runner.engine().isRunning(); i++) {
            runner.step(1);
        }
        AtomicReference<Font> font = new AtomicReference<>();
        game.assets().load(AssetKey.font("test:fonts/nothing")).thenSync(font::set);
        AtomicReference<Object> region = new AtomicReference<>();
        game.assets().load(AssetKey.region("test:sprites/nothing")).thenSync(region::set);
        runner.step(3);
        assertThat(font.get()).isSameAs(game.graphics().defaultFont().regular());
        assertThat(region.get()).isNotNull();
        assertThat(runner.backend().log().messages(PlatformLog.WARN))
                .anyMatch(m -> m.contains("test:fonts/nothing") && m.contains("placeholder"));
    }

    @Test
    void aBrokenShaderFallsBackAndLogsTheLine() {
        TestGame game = new TestGame();
        runner = Fixtures.started(game);
        Shader shader = game.graphics().shader("""
                void main() {
                #error missing semicolon
                }
                """);
        assertThat(shader.isValid()).isFalse();
        assertThat(shader.log()).contains("0:").contains("#error");
        assertThat(errors()).anyMatch(m -> m.contains("using the default shader"));
    }

    @Test
    void apiCallsFromOtherThreadsFailWithTheMethodAndThread() throws InterruptedException {
        TestGame game = new TestGame();
        runner = Fixtures.started(game);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread worker = new Thread(
                () -> {
                    try {
                        game.engine().pause();
                    } catch (Throwable error) {
                        thrown.set(error);
                    }
                },
                "worker-7");
        worker.start();
        worker.join();
        assertThat(thrown.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Engine.pause")
                .hasMessageContaining("worker-7");
    }

    @Test
    void aFatalErrorWritesACrashReport() {
        BaseModule module = new BaseModule();
        TestGame game = new TestGame().modules(module);
        game.onStart = () -> {
            throw new IllegalStateException("start broke", new IllegalArgumentException("the cause"));
        };
        runner = HeadlessRunner.start(game);
        assertThatThrownBy(() -> runner.step(4)).hasMessageContaining("failed to start");
        HeadlessLog log = runner.backend().log();
        assertThat(log.crashReports()).hasSize(1);
        String report = log.crashReports().getFirst();
        assertThat(report)
                .contains(
                        "---- Gulp crash report ----",
                        "Description: The game failed to start",
                        "java.lang.IllegalStateException: start broke",
                        "Caused by: java.lang.IllegalArgumentException: the cause",
                        "Game: test",
                        "Gulp: " + GulpEngine.VERSION,
                        "Backend: headless",
                        "base: ",
                        "-- Last log lines --");
        runner.engine().crash("again", new RuntimeException("ignored"));
        assertThat(log.crashReports()).as("one report per run").hasSize(1);
        runner = null;
    }

    @Test
    void crashReportPartsWorkOnTheirOwn() {
        assertThat(CrashReport.fileName(0L)).isEqualTo("crash-1970-01-01_00-00-00.txt");
        List<String> lines = new ArrayList<>();
        PlatformLog sink = (level, logger, message, error) -> lines.add(message);
        RecentLog recent = new RecentLog(sink, 2);
        recent.write(PlatformLog.INFO, "a", "one", null);
        recent.write(PlatformLog.WARN, "a", "two", null);
        recent.write(PlatformLog.ERROR, "b", "three", new RuntimeException("x"));
        assertThat(lines).containsExactly("one", "two", "three");
        assertThat(recent.lines())
                .containsExactly("[WARN] [a] two", "[ERROR] [b] three (java.lang.RuntimeException: x)");
        assertThat(recent.sink()).isSameAs(sink);
        assertThat(sink.crash("x.txt", "report")).isEmpty();
        assertThat(lines).contains("report");
        String text = CrashReport.text(
                new CrashReport.Details("testing", 0L, "g", "1", "b", "os", "gpu", "25", List.of(), List.of()),
                new RuntimeException("boom"));
        assertThat(text).contains("(none)", "Time: 1970-01-01 00:00:00 UTC", "boom");
        assertThat(new CrashScreen("x".repeat(400), "").toString()).isNotEmpty();
    }
}
