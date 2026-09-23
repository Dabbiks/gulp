package dev.gulp.core;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.event.Cancellable;
import dev.gulp.api.event.Event;
import dev.gulp.api.event.EventHandler;
import dev.gulp.api.event.EventPriority;
import dev.gulp.api.event.Listener;
import dev.gulp.api.event.TargetedEvent;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleInfo;
import dev.gulp.backend.headless.HeadlessBackend;
import dev.gulp.backend.headless.HeadlessRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Games, modules, events and listeners shared by the engine tests. */
final class Fixtures {

    private Fixtures() {}

    /** Shared call log; tests clear it in their setup. */
    static final List<String> LOG = new ArrayList<>();

    /** Game whose lifecycle is scripted by the test. */
    static class TestGame extends Game {
        final String id;
        Consumer<GameSettings> configure = s -> {};
        Runnable onLoad = () -> {};
        Runnable onStart = () -> {};
        Runnable onStop = () -> {};

        TestGame() {
            this("test");
        }

        TestGame(String id) {
            this.id = id;
        }

        TestGame modules(GameModule... modules) {
            Consumer<GameSettings> previous = configure;
            configure = s -> {
                previous.accept(s);
                s.modules(modules);
            };
            return this;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void configure(GameSettings settings) {
            settings.windowSize(320, 180);
            configure.accept(settings);
        }

        @Override
        public void onLoad() {
            LOG.add("game.onLoad");
            onLoad.run();
        }

        @Override
        public void onStart() {
            LOG.add("game.onStart");
            onStart.run();
        }

        @Override
        public void onStop() {
            LOG.add("game.onStop");
            onStop.run();
        }
    }

    /** Starts a game and runs the first frame, after which it is running. */
    static HeadlessRunner started(Game game) {
        HeadlessRunner runner = HeadlessRunner.start(game);
        runner.step(1);
        return runner;
    }

    /** Starts a game with a prepared backend and runs the first frame. */
    static HeadlessRunner started(Game game, Consumer<HeadlessBackend> prepare) {
        HeadlessRunner runner = HeadlessRunner.start(game, prepare);
        runner.step(1);
        return runner;
    }

    /** Module base that logs its lifecycle and runs optional hooks. */
    abstract static class LoggingModule extends GameModule {
        Runnable onLoadHook = () -> {};
        Runnable onEnableHook = () -> {};
        Runnable onDisableHook = () -> {};

        private String name() {
            return getClass().getSimpleName();
        }

        @Override
        public void onLoad() {
            LOG.add(name() + ".onLoad");
            onLoadHook.run();
        }

        @Override
        public void onEnable() {
            LOG.add(name() + ".onEnable");
            onEnableHook.run();
        }

        @Override
        public void onDisable() {
            LOG.add(name() + ".onDisable");
            onDisableHook.run();
        }
    }

    @ModuleInfo(id = "base")
    static final class BaseModule extends LoggingModule {}

    @ModuleInfo(id = "middle", dependsOn = "base")
    static final class MiddleModule extends LoggingModule {}

    @ModuleInfo(
            id = "top",
            dependsOn = {"middle"},
            softDependsOn = {"extra", "absent"})
    static final class TopModule extends LoggingModule {}

    @ModuleInfo(id = "extra")
    static final class ExtraModule extends LoggingModule {}

    @ModuleInfo(id = "lazy", enabledByDefault = false)
    static final class LazyModule extends LoggingModule {}

    @ModuleInfo(id = "orphan", dependsOn = "missing")
    static final class OrphanModule extends LoggingModule {}

    /** A module without {@code @ModuleInfo}; tests register its descriptor by hand. */
    static final class ManualModule extends LoggingModule {}

    /** Another module without {@code @ModuleInfo}. */
    static final class OtherManualModule extends LoggingModule {}

    /** A plain event. */
    static class PingEvent extends Event {
        final List<String> seen = new ArrayList<>();
    }

    /** A subclass event, delivered to {@link PingEvent} listeners too. */
    static final class LoudPingEvent extends PingEvent {}

    /** A cancellable event. */
    static final class DamageEvent extends Event implements Cancellable {
        private boolean cancelled;
        int amount;

        DamageEvent(int amount) {
            this.amount = amount;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }

    /** An event about one object. */
    static final class TouchEvent extends Event implements TargetedEvent {
        private final Object target;

        TouchEvent(Object target) {
            this.target = target;
        }

        @Override
        public Object target() {
            return target;
        }
    }

    /** Listener with annotated handlers, dispatched through generated code. */
    static class PingListener implements Listener {
        final List<String> calls = new ArrayList<>();

        @EventHandler
        void onPing(PingEvent event) {
            calls.add("ping:" + event.getClass().getSimpleName());
            event.seen.add("listener");
        }

        @EventHandler(priority = EventPriority.LOWEST)
        void first(PingEvent event) {
            event.seen.add("lowest");
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        void onDamage(DamageEvent event) {
            calls.add("damage:" + event.amount);
        }
    }

    /** Subclass of a listener: inherits the handlers of {@link PingListener} and adds its own. */
    static final class ExtendedPingListener extends PingListener {
        @EventHandler(priority = EventPriority.MONITOR)
        void last(PingEvent event) {
            event.seen.add("monitor");
        }
    }

    /** A listener class without handlers, so without generated code. */
    static final class EmptyListener implements Listener {}
}
