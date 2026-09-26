package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gulp.api.debug.Debug;
import dev.gulp.api.debug.DebugFlag;
import dev.gulp.api.debug.ProfileReport;
import dev.gulp.api.debug.Stats;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.event.lifecycle.TickEndEvent;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.Keys;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.registry.Key;
import dev.gulp.api.world.DebugView;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;
import dev.gulp.backend.headless.HeadlessLog;
import dev.gulp.core.debug.DebugImpl;
import dev.gulp.platform.PlatformLog;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Developer tools of section 20.1: F3 overlay and combinations, drawings, the entity inspector and the profiler. */
class DebugTest extends UiFixture {

    static final EntityType WALKER = EntityType.builder(Key.of("test", "walker"))
            .component(EntityTest.Walker::new)
            .build();
    static final EntityType HOLDER = SaveTest.HOLDER;

    private World world() {
        World world = game.worlds().create("debug", WorldSettings.DEFAULT);
        game.worlds().switchTo("debug");
        step(2);
        return world;
    }

    private void f3With(dev.gulp.api.input.KeyboardKey key) {
        backend().input().inject(l -> l.keyDown(Keys.F3.code(), 0, 0, false));
        step(1);
        key(key);
        backend().input().inject(l -> l.keyUp(Keys.F3.code(), 0, 0));
        step(1);
    }

    @Test
    void f3ShowsTheOverlayAndCombinationsSwitchDrawings() {
        startUi();
        Debug debug = game.debug();
        World world = world();
        world.spawn(WALKER, 0, 0);
        assertThat(debug.isEnabled()).isTrue();
        assertThat(debug.isOverlayVisible()).isFalse();

        key(Keys.F3);
        assertThat(debug.isOverlayVisible()).isTrue();
        f3With(Keys.C);
        assertThat(debug.isOn(DebugFlag.COLLISION)).isTrue();
        assertThat(debug.isOverlayVisible())
                .as("a combination does not switch the overlay")
                .isTrue();
        f3With(Keys.U);
        assertThat(debug.isOn(DebugFlag.UI_INSPECTOR)).isTrue();
        assertThat(ui.isInspectorOpen()).isTrue();
        f3With(Keys.U);
        f3With(Keys.G);
        f3With(Keys.N);
        f3With(Keys.L);
        assertThat(debug.isOn(DebugFlag.CHUNKS)).isTrue();
        assertThat(debug.isOn(DebugFlag.NAVIGATION)).isTrue();
        assertThat(debug.isOn(DebugFlag.LIGHTS)).isTrue();
        step(70);

        Stats stats = debug.stats();
        assertThat(stats.fps()).isPositive();
        assertThat(stats.tps()).isPositive();
        assertThat(stats.entities()).isEqualTo(1);
        assertThat(stats.chunks()).isGreaterThanOrEqualTo(0);
        assertThat(stats.drawCalls()).isPositive();
        assertThat(stats.memoryUsed()).isPositive();
        assertThat(stats.memoryMax()).isGreaterThanOrEqualTo(stats.memoryUsed());

        var console = game.engine().commands().console();
        console.submit("/debug overlay off");
        assertThat(debug.isOverlayVisible()).isFalse();
        console.submit("/debug chunks");
        assertThat(debug.isOn(DebugFlag.CHUNKS)).isFalse();
        console.submit("/debug entity_inspector on");
        assertThat(debug.isOn(DebugFlag.ENTITY_INSPECTOR)).isTrue();
        console.submit("/debug overlay");
        assertThat(debug.isOverlayVisible()).isTrue();
        assertThat(DebugFlag.byName("COLLISION")).isEqualTo(DebugFlag.COLLISION);
        assertThat(DebugFlag.byName("nothing")).isNull();
        assertThat(DebugFlag.LIGHTS.key()).isEqualTo('L');
        assertThat(DebugImpl.keyOf(DebugFlag.ENTITY_INSPECTOR)).isEqualTo(Keys.E);
        assertThat(world.isDebugShown(DebugView.SHAPES))
                .as("flags do not change the world's own views")
                .isFalse();
    }

    @Test
    void shapesStayForTheirTicks() {
        startUi();
        Debug debug = game.debug();
        world();
        DebugImpl.Shapes shapes = (DebugImpl.Shapes) debug.draw();
        debug.draw()
                .line(Vec2.ZERO, new Vec2(3, 0), Color.YELLOW, 3)
                .rect(new Rect(0, 0, 2, 2), Color.RED)
                .circle(Vec2.ZERO, 2f, Color.GREEN)
                .arrow(Vec2.ZERO, new Vec2(0, 4), Color.CYAN)
                .arrow(Vec2.ZERO, Vec2.ZERO, Color.CYAN)
                .text(new Vec2(1, 1), "hello", Color.WHITE);
        assertThat(shapes.size()).isEqualTo(6);
        step(1);
        assertThat(shapes.size()).isEqualTo(1);
        step(2);
        assertThat(shapes.size()).isZero();
        for (int i = 0; i < 40; i++) {
            debug.draw().circle(new Vec2(i, 0), 1f, Color.RED, 5);
        }
        step(1);
        assertThat(shapes.size()).isEqualTo(40);
        debug.draw().clear();
        assertThat(shapes.size()).isZero();
    }

    @Test
    void clickingAnEntityInspectsIt() {
        startUi();
        Debug debug = game.debug();
        World world = world();
        move(640, 360);
        Vec2 under = game.input().mouseWorld(world.camera());
        Entity holder = world.spawn(HOLDER, under.x(), under.y(), e -> e.setName("banker"));
        holder.get(SaveTest.Purse.class).coins = 17;
        holder.tags().add("rich");
        click(640, 360);
        assertThat(debug.inspected())
                .as("clicks pick only with the inspector on")
                .isNull();

        debug.set(DebugFlag.ENTITY_INSPECTOR, true);
        click(640, 360);
        assertThat(debug.inspected()).isSameAs(holder);
        String text = ((DebugImpl) debug).describe(holder);
        assertThat(text).contains("test:holder", "\"banker\"", "rich", "Purse", "\"coins\":17", "Scratch");
        step(2);

        holder.remove();
        step(1);
        assertThat(debug.inspected()).isNull();
        debug.inspect(holder);
        debug.set(DebugFlag.ENTITY_INSPECTOR, false);
        assertThat(debug.inspected()).isNull();
    }

    @Test
    void theProfilerMeasuresModulesListenersTasksAndComponents() {
        Fixtures.BaseModule module = new Fixtures.BaseModule();
        module.onEnableHook = () -> {
            module.on(TickEndEvent.class, e -> {});
            module.every(1, () -> {});
        };
        startUi(settings -> settings.modules(module), b -> {});
        World world = world();
        world.spawn(WALKER, 0, 0);
        Debug debug = game.debug();
        assertThat(debug.profiler().stop()).isEqualTo(ProfileReport.EMPTY);
        try (var _ = debug.section("before start")) {
            // sections before start cost nothing and are not reported
        }

        debug.profiler().start();
        debug.profiler().start();
        assertThat(debug.profiler().isRunning()).isTrue();
        for (int i = 0; i < 5; i++) {
            try (var _ = debug.section("ai")) {
                try (var _ = debug.profiler().section("pathfinding")) {
                    step(1);
                }
            }
        }
        step(5);
        ProfileReport report = debug.profiler().stop();
        assertThat(debug.profiler().isRunning()).isFalse();
        assertThat(report.ticks()).isEqualTo(10);
        assertThat(report.frames()).isEqualTo(10);
        Set<ProfileReport.Category> categories =
                report.entries().stream().map(ProfileReport.Entry::category).collect(Collectors.toSet());
        assertThat(categories)
                .contains(
                        ProfileReport.Category.TICK,
                        ProfileReport.Category.RENDER,
                        ProfileReport.Category.LISTENER,
                        ProfileReport.Category.TASK,
                        ProfileReport.Category.COMPONENT,
                        ProfileReport.Category.MODULE,
                        ProfileReport.Category.SECTION);
        List<String> names =
                report.entries().stream().map(ProfileReport.Entry::name).toList();
        assertThat(names).contains("Walker", "ai", "pathfinding", "base", "tasks of base");
        assertThat(names).doesNotContain("before start");
        ProfileReport.Entry walker = report.entries().stream()
                .filter(e -> e.name().equals("Walker"))
                .findFirst()
                .orElseThrow();
        assertThat(walker.calls()).isEqualTo(10);
        assertThat(walker.millisPerTick(report.ticks())).isGreaterThanOrEqualTo(0.0);
        assertThat(walker.millisPerTick(0)).isZero();
        assertThat(report.text()).contains("category", "component", "Walker", "10 ticks");

        var console = game.engine().commands().console();
        console.submit("/profile stop");
        console.submit("/profile start");
        step(3);
        console.submit("/profile stop");
        step(2);
        HeadlessLog log = backend().log();
        assertThat(log.messages(PlatformLog.INFO)).anyMatch(m -> m.startsWith("Wrote profiles/profile-"));
        assertThat(backend().console().printed()).anyMatch(line -> line.contains("ticks profiled"));
        assertThat(backend().console().printed()).anyMatch(line -> line.contains("not running"));
    }

    @Test
    void productionBuildsTurnTheToolsOff() {
        startUi(settings -> settings.debugTools(false), b -> {});
        Debug debug = game.debug();
        world();
        assertThat(debug.isEnabled()).isFalse();
        debug.draw().line(Vec2.ZERO, Vec2.ONE, Color.RED, 10);
        assertThat(((DebugImpl.Shapes) debug.draw()).size()).isZero();
        debug.set(DebugFlag.COLLISION, true);
        debug.setOverlayVisible(true);
        debug.inspect(null);
        assertThat(debug.isOn(DebugFlag.COLLISION)).isFalse();
        assertThat(debug.isOverlayVisible()).isFalse();
        key(Keys.F3);
        assertThat(debug.isOverlayVisible()).isFalse();
        debug.profiler().start();
        assertThat(debug.profiler().isRunning()).isFalse();
        game.engine().commands().console().submit("/debug overlay on");
        game.engine().commands().console().submit("/profile start");
        assertThat(backend().console().printed()).anyMatch(line -> line.contains("off in this build"));
    }
}
