package dev.gulp.core;

import static dev.gulp.core.Fixtures.LOG;
import static dev.gulp.core.Fixtures.started;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.data.DataType;
import dev.gulp.api.event.lifecycle.ModuleDisableEvent;
import dev.gulp.api.event.lifecycle.ModuleEnableEvent;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleManager;
import dev.gulp.api.module.ModuleState;
import dev.gulp.api.service.ServicePriority;
import dev.gulp.api.spi.ModuleDescriptor;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.BaseModule;
import dev.gulp.core.Fixtures.ExtraModule;
import dev.gulp.core.Fixtures.LazyModule;
import dev.gulp.core.Fixtures.ManualModule;
import dev.gulp.core.Fixtures.MiddleModule;
import dev.gulp.core.Fixtures.OrphanModule;
import dev.gulp.core.Fixtures.OtherManualModule;
import dev.gulp.core.Fixtures.PingEvent;
import dev.gulp.core.Fixtures.PingListener;
import dev.gulp.core.Fixtures.TestGame;
import dev.gulp.core.Fixtures.TopModule;
import dev.gulp.platform.PlatformLog;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModulesTest {

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
    void loadsAndEnablesInDependencyOrderAndDisablesInReverse() {
        TopModule top = new TopModule();
        MiddleModule middle = new MiddleModule();
        BaseModule base = new BaseModule();
        ExtraModule extra = new ExtraModule();
        TestGame game = new TestGame().modules(top, middle, extra, base);
        runner = started(game);
        ModuleManager modules = runner.engine().modules();

        assertThat(modules.ids()).containsExactly("base", "middle", "extra", "top");
        assertThat(LOG)
                .containsExactly(
                        "game.onLoad",
                        "BaseModule.onLoad",
                        "MiddleModule.onLoad",
                        "ExtraModule.onLoad",
                        "TopModule.onLoad",
                        "game.onStart",
                        "BaseModule.onEnable",
                        "MiddleModule.onEnable",
                        "ExtraModule.onEnable",
                        "TopModule.onEnable");
        assertThat(top.isEnabled()).isTrue();
        assertThat(top.id()).isEqualTo("top");
        assertThat(top.state()).isEqualTo(ModuleState.ENABLED);
        assertThat(modules.get(TopModule.class)).isSameAs(top);
        assertThat(modules.get(GameModule.class)).isNotNull();
        assertThat(modules.get(LazyModule.class)).isNull();
        assertThat(modules.get("middle")).isSameAs(middle);
        assertThat(modules.get("nope")).isNull();
        assertThat(modules.isEnabled("nope")).isFalse();
        assertThat(top.require(MiddleModule.class)).isSameAs(middle);
        assertThat(top.logger().name()).isEqualTo("top");
        assertThat(top.key("x").toString()).isEqualTo("test:x");

        LOG.clear();
        runner.stop();
        runner = null;
        assertThat(LOG)
                .containsExactly(
                        "TopModule.onDisable",
                        "ExtraModule.onDisable",
                        "MiddleModule.onDisable",
                        "BaseModule.onDisable",
                        "game.onStop");
    }

    @Test
    void disablingCascadesToDependentsAndRemovesRegistrations() {
        BaseModule base = new BaseModule();
        MiddleModule middle = new MiddleModule();
        TopModule top = new TopModule();
        PingListener listener = new PingListener();
        middle.onEnableHook = () -> {
            middle.listen(listener);
            middle.every(1, () -> LOG.add("tick"));
            middle.command("middle", ctx -> ctx.reply("hi"));
            middle.services().register(Runnable.class, () -> {}, middle, ServicePriority.NORMAL);
        };
        TestGame game = new TestGame().modules(base, middle, top);
        runner = started(game);
        GulpEngine engine = runner.engine();
        game.on(ModuleDisableEvent.class, e -> LOG.add("disabled " + e.moduleId()));
        game.on(
                ModuleEnableEvent.class,
                e -> LOG.add("enabled " + e.moduleId() + " " + e.module().id()));
        assertThat(engine.registrationsOf(middle)).isGreaterThan(0);

        LOG.clear();
        engine.modules().disable("base");

        assertThat(LOG)
                .containsExactly(
                        "TopModule.onDisable",
                        "disabled top",
                        "MiddleModule.onDisable",
                        "disabled middle",
                        "BaseModule.onDisable",
                        "disabled base");
        assertThat(engine.registrationsOf(middle)).isZero();
        assertThat(engine.commands().get("middle")).isNull();
        assertThat(engine.services().isProvided(Runnable.class)).isFalse();
        engine.events().call(new PingEvent());
        runner.step(3);
        assertThat(listener.calls).isEmpty();
        assertThat(LOG).doesNotContain("tick");
        assertThatThrownBy(() -> top.require(MiddleModule.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DISABLED");
        assertThatThrownBy(() -> middle.every(1, () -> {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");

        LOG.clear();
        assertThat(engine.modules().enable("top")).isTrue();
        assertThat(LOG)
                .containsSubsequence(
                        "BaseModule.onEnable", "MiddleModule.onEnable", "TopModule.onEnable", "enabled top top");
        runner.step(1);
        assertThat(LOG).contains("tick");
        engine.modules().disable("top");
        engine.modules().disable("top");
    }

    @Test
    void failingOnEnableMarksTheModuleFailedAndKeepsDependentsOff() {
        BaseModule base = new BaseModule();
        MiddleModule middle = new MiddleModule();
        base.onEnableHook = () -> {
            base.every(1, () -> LOG.add("base tick"));
            throw new IllegalStateException("enable broke");
        };
        runner = started(new TestGame().modules(base, middle));
        ModuleManager modules = runner.engine().modules();

        assertThat(modules.state("base")).isEqualTo(ModuleState.FAILED);
        assertThat(modules.state("middle")).isEqualTo(ModuleState.DISABLED);
        runner.step(2);
        assertThat(LOG).doesNotContain("base tick");
        assertThat(runner.backend().log().messages(PlatformLog.ERROR))
                .contains("onEnable failed; module disabled, the game continues");

        assertThat(LOG).containsOnlyOnce("BaseModule.onEnable");
        base.onEnableHook = () -> {};
        assertThat(modules.enable("middle"))
                .as("failed dependency is not retried implicitly")
                .isFalse();
        assertThat(modules.enable("base")).isTrue();
        assertThat(modules.enable("middle")).isTrue();
        assertThat(modules.state("base")).isEqualTo(ModuleState.ENABLED);
    }

    @Test
    void failingOnLoadDisablesTheModuleAndItsDependents() {
        BaseModule base = new BaseModule();
        MiddleModule middle = new MiddleModule();
        base.onLoadHook = () -> {
            throw new IllegalStateException("load broke");
        };
        runner = started(new TestGame().modules(base, middle));
        ModuleManager modules = runner.engine().modules();

        assertThat(modules.state("base")).isEqualTo(ModuleState.FAILED);
        assertThat(modules.state("middle")).isEqualTo(ModuleState.DISABLED);
        assertThat(LOG).doesNotContain("MiddleModule.onLoad", "BaseModule.onEnable");
        assertThat(modules.enable("base")).isFalse();
        assertThat(modules.enable("middle")).isFalse();
    }

    @Test
    void missingDependencyKeepsModuleDisabled() {
        runner = started(new TestGame().modules(new OrphanModule()));

        assertThat(runner.engine().modules().state("orphan")).isEqualTo(ModuleState.DISABLED);
        assertThat(runner.backend().log().messages(PlatformLog.ERROR))
                .anyMatch(m -> m.contains("Missing required module(s) [missing]"));
        assertThat(runner.engine().modules().enable("orphan")).isFalse();
    }

    @Test
    void modulesDisabledByDefaultLoadButDoNotEnable() {
        LazyModule lazy = new LazyModule();
        runner = started(new TestGame().modules(lazy));
        ModuleManager modules = runner.engine().modules();

        assertThat(LOG).contains("LazyModule.onLoad").doesNotContain("LazyModule.onEnable");
        assertThat(modules.state("lazy")).isEqualTo(ModuleState.LOADED);
        assertThat(lazy.isEnabled()).isFalse();

        assertThat(modules.enable("lazy")).isTrue();
        assertThat(modules.enable("lazy")).isTrue();
        assertThat(LOG).containsOnlyOnce("LazyModule.onEnable");
    }

    @Test
    void modulesHaveDataAndConfig() {
        BaseModule base = new BaseModule();
        runner = started(
                new TestGame().modules(base),
                backend -> backend.files()
                        .putAsset(
                                "test/config/base.yml", "speed: 4".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        base.data().set(base.key("kills"), DataType.INT, 3);

        assertThat(base.data().get(base.key("kills"), DataType.INT)).isEqualTo(3);
        assertThat(base.config().getInt("speed", 0)).isEqualTo(4);
        assertThat(base.config().name()).isEqualTo("base");
    }

    @Test
    void undeclaredModulesAreRejected() {
        runner = started(new TestGame());
        ModuleManager modules = runner.engine().modules();
        BaseModule stranger = new BaseModule();

        assertThatThrownBy(() -> modules.idOf(stranger)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> modules.data(stranger)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> modules.config(stranger)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> modules.state("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No module 'nope'");
        assertThat(modules.logger(stranger).name()).isEqualTo("gulp");
    }

    @Test
    void cyclesAreReportedWithTheFullPath() {
        ManualModule a = new ManualModule();
        OtherManualModule b = new OtherManualModule();

        assertThatThrownBy(() -> HeadlessRunner.start(new TestGame().modules(a, b), backend -> {
                    backend.modules()
                            .register(
                                    ModuleDescriptor.class,
                                    ManualModule.class,
                                    new ModuleDescriptor("a", List.of("b"), List.of(), true));
                    backend.modules()
                            .register(
                                    ModuleDescriptor.class,
                                    OtherManualModule.class,
                                    new ModuleDescriptor("b", List.of(), List.of("a"), true));
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Module dependency cycle: a -> b -> a");
    }

    @Test
    void duplicateIdsAndMissingDescriptorsAreRejected() {
        assertThatThrownBy(() -> HeadlessRunner.start(new TestGame().modules(new ManualModule())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no generated descriptor");

        assertThatThrownBy(() -> HeadlessRunner.start(
                        new TestGame().modules(new BaseModule(), new ManualModule()),
                        backend -> backend.modules()
                                .register(
                                        ModuleDescriptor.class,
                                        ManualModule.class,
                                        new ModuleDescriptor("base", List.of(), List.of(), true))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate module id 'base'");

        BaseModule twice = new BaseModule();
        assertThatThrownBy(() -> new dev.gulp.api.GameSettings().modules(twice, twice))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onDisableFailureIsLoggedAndCleanupStillHappens() {
        BaseModule base = new BaseModule();
        base.onEnableHook = () -> base.every(1, () -> LOG.add("tick"));
        base.onDisableHook = () -> {
            throw new IllegalStateException("disable broke");
        };
        runner = started(new TestGame().modules(base));

        runner.engine().modules().disable("base");
        LOG.clear();
        runner.step(2);

        assertThat(LOG).isEmpty();
        assertThat(runner.backend().log().messages(PlatformLog.ERROR)).contains("onDisable failed");
    }
}
