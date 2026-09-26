package dev.gulp.examples.bench;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gulp.api.debug.ProfileReport;
import dev.gulp.api.world.World;
import dev.gulp.backend.headless.HeadlessRunner;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The CPU side of the budgets of section 20.5, headless: each scene at its desktop count must fit a 60 Hz frame, and
 * the typical scene its tick and render budgets. {@code -Pgulp.bench.slack=2} doubles every budget on slow machines.
 * Run with {@code ./gradlew :examples:bench:bench}.
 */
@Tag("bench")
class BenchTest {

    private static final double FRAME_MS = 1000.0 / 60.0;
    private static final double SLACK = Double.parseDouble(System.getProperty("gulp.bench.slack", "1"));

    /** A game that only hosts the scenes; the test builds them. */
    static final class Host extends dev.gulp.api.Game {
        @Override
        public String id() {
            return "bench";
        }

        @Override
        public void configure(dev.gulp.api.GameSettings settings) {
            settings.windowSize(1280, 720).debugTools(true);
        }

        @Override
        public void onStart() {}
    }

    private @Nullable HeadlessRunner runner;

    @AfterEach
    void tearDown() {
        if (runner != null) {
            runner.stop();
        }
    }

    private Host start() {
        Host game = new Host();
        long started = System.nanoTime();
        HeadlessRunner r = HeadlessRunner.start(game, b -> b.files().useClasspathAssets(true));
        runner = r;
        for (int i = 0; i < 30 && !r.engine().isRunning(); i++) {
            r.step(1);
        }
        double startMs = (System.nanoTime() - started) / 1e6;
        System.out.printf(Locale.ROOT, "BENCH startup %.1f ms%n", startMs);
        assertThat(startMs).as("start to the first frame").isLessThan(2000 * SLACK);
        return game;
    }

    /** Builds a scene, warms up, then returns milliseconds per frame (one tick and one render). */
    private double frameMillis(Host game, BenchScene scene, int count) {
        World world = scene.build(game, count);
        game.worlds().switchTo(world.name());
        runner.step(300);
        game.debug().profiler().start();
        long started = System.nanoTime();
        runner.step(300);
        double ms = (System.nanoTime() - started) / 1e6 / 300;
        ProfileReport report = game.debug().profiler().stop();
        System.out.printf(
                Locale.ROOT,
                "BENCH %s x %d: %.2f ms per frame (tick %.2f, render %.2f)%n",
                scene,
                count,
                ms,
                average(report, ProfileReport.Category.TICK),
                average(report, ProfileReport.Category.RENDER));
        return ms;
    }

    @Test
    void spritesFitAFrame() {
        Host game = start();
        assertThat(frameMillis(game, BenchScene.SPRITES, BenchScene.SPRITES.count(false)))
                .isLessThan(FRAME_MS * SLACK);
    }

    @Test
    void entitiesFitATick() {
        Host game = start();
        assertThat(frameMillis(game, BenchScene.ENTITIES, BenchScene.ENTITIES.count(false)))
                .isLessThan(FRAME_MS * SLACK);
    }

    @Test
    void particlesFitAFrame() {
        Host game = start();
        assertThat(frameMillis(game, BenchScene.PARTICLES, BenchScene.PARTICLES.count(false)))
                .isLessThan(FRAME_MS * SLACK);
    }

    @Test
    void aScreenOfWidgetsFitsTheRenderBudget() {
        Host game = start();
        assertThat(frameMillis(game, BenchScene.UI, BenchScene.UI.count(false))).isLessThan(8.0 * SLACK);
    }

    @Test
    void theTypicalSceneFitsTheTickAndRenderBudgets() {
        Host game = start();
        World world = BenchScene.MAP.build(game, BenchScene.MAP.count(false));
        game.worlds().switchTo(world.name());
        runner.step(300);
        game.debug().profiler().start();
        runner.step(300);
        ProfileReport report = game.debug().profiler().stop();
        double tick = average(report, ProfileReport.Category.TICK);
        double render = average(report, ProfileReport.Category.RENDER);
        System.out.printf(Locale.ROOT, "BENCH typical scene: tick %.2f ms, render %.2f ms%n", tick, render);
        assertThat(tick).as("tick").isLessThan(4.0 * SLACK);
        assertThat(render).as("render").isLessThan(8.0 * SLACK);
    }

    private static double average(ProfileReport report, ProfileReport.Category category) {
        for (ProfileReport.Entry entry : report.entries()) {
            if (entry.category() == category && entry.calls() > 0) {
                return entry.totalNanos() / 1e6 / entry.calls();
            }
        }
        return 0.0;
    }
}
