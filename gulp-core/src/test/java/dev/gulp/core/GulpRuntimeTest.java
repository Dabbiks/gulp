package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.graphics.Color;
import dev.gulp.backend.headless.HeadlessBackend;
import dev.gulp.platform.WindowConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GulpRuntimeTest {

    private final List<String> calls = new ArrayList<>();

    private final Game game = new Game() {
        @Override
        public String id() {
            return "test";
        }

        @Override
        public void configure(GameSettings settings) {
            calls.add("configure");
            settings.title("Runtime test").windowSize(320, 180).clearColor(new Color(0.25f, 0.5f, 0.75f, 1f));
        }

        @Override
        public void onLoad() {
            calls.add("onLoad");
        }

        @Override
        public void onStart() {
            calls.add("onStart");
        }

        @Override
        public void onStop() {
            calls.add("onStop");
        }
    };

    @Test
    void configureAppliesGameChoicesToDefaults() {
        GameSettings settings = GulpRuntime.configure(game);

        assertThat(calls).containsExactly("configure");
        assertThat(settings.title()).isEqualTo("Runtime test");
        assertThat(settings.ticksPerSecond()).isEqualTo(60);
    }

    @Test
    void windowConfigMirrorsSettings() {
        GameSettings settings = new GameSettings()
                .title("T")
                .windowSize(640, 360)
                .resizable(false)
                .fullscreen(true)
                .vsync(false);

        assertThat(GulpRuntime.windowConfig(settings)).isEqualTo(new WindowConfig("T", 640, 360, false, true, false));
    }

    @Test
    void runsLifecycleInOrderAndClearsEveryFrame() {
        GameSettings settings = GulpRuntime.configure(game);
        HeadlessBackend backend = new HeadlessBackend(GulpRuntime.windowConfig(settings));
        GulpRuntime runtime = new GulpRuntime(game, settings, backend);

        runtime.start();
        assertThat(calls).containsExactly("configure", "onLoad", "onStart");
        assertThat(runtime.frameCount()).isZero();

        backend.loop().step(3);

        assertThat(runtime.frameCount()).isEqualTo(3);
        assertThat(backend.gl().clearCount()).isEqualTo(3);
        assertThat(backend.gl().lastClearColor()).containsExactly(0.25f, 0.5f, 0.75f, 1f);
        assertThat(backend.gl().viewport()).containsExactly(0, 0, 320, 180);
        assertThat(runtime.isStopped()).isFalse();
    }

    @Test
    void stopsWhenWindowCloses() {
        GameSettings settings = GulpRuntime.configure(game);
        HeadlessBackend backend = new HeadlessBackend(GulpRuntime.windowConfig(settings));
        GulpRuntime runtime = new GulpRuntime(game, settings, backend);
        runtime.start();

        backend.loop().step(2);
        backend.window().simulateCloseRequest();
        int ran = backend.loop().step(10);

        assertThat(ran).isEqualTo(1);
        assertThat(runtime.isStopped()).isTrue();
        assertThat(backend.loop().isRunning()).isFalse();
        assertThat(calls).endsWith("onStop");
    }

    @Test
    void viewportFollowsHiDpiFramebuffer() {
        GameSettings settings = GulpRuntime.configure(game);
        HeadlessBackend backend = new HeadlessBackend(GulpRuntime.windowConfig(settings));
        new GulpRuntime(game, settings, backend).start();

        backend.window().simulateResize(400, 300, 2f);
        backend.loop().step(1);

        assertThat(backend.gl().viewport()).containsExactly(0, 0, 800, 600);
    }

    @Test
    void onStopRunsOnlyOnce() {
        GameSettings settings = GulpRuntime.configure(game);
        GulpRuntime runtime = new GulpRuntime(game, settings, new HeadlessBackend(GulpRuntime.windowConfig(settings)));

        runtime.exit();
        runtime.exit();

        assertThat(calls).filteredOn("onStop"::equals).hasSize(1);
    }

    @Test
    void cannotStartTwice() {
        GameSettings settings = GulpRuntime.configure(game);
        GulpRuntime runtime = new GulpRuntime(game, settings, new HeadlessBackend(GulpRuntime.windowConfig(settings)));
        runtime.start();

        assertThatThrownBy(runtime::start).isInstanceOf(IllegalStateException.class);
    }
}
