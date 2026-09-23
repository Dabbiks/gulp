package dev.gulp.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.spi.GameLauncher;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GulpTest {

    private final FakeLauncher desktop = new FakeLauncher("desktop", 100);
    private final FakeLauncher headless = new FakeLauncher("headless", 0);

    @Test
    void selectsHighestPriorityLauncher() {
        assertThat(Gulp.select(List.of(headless, desktop), null)).isSameAs(desktop);
        assertThat(Gulp.select(List.of(desktop, headless), null)).isSameAs(desktop);
    }

    @Test
    void forcedNameOverridesPriority() {
        assertThat(Gulp.select(List.of(desktop, headless), "headless")).isSameAs(headless);
    }

    @Test
    void failsWhenForcedLauncherIsMissing() {
        assertThatThrownBy(() -> Gulp.select(List.of(desktop, headless), "web"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'web'")
                .hasMessageContaining("[desktop, headless]");
    }

    @Test
    void failsWhenNoLauncherIsAvailable() {
        assertThatThrownBy(() -> Gulp.select(List.of(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No Gulp backend");
    }

    @Test
    void launchUsesServiceLoaderAndForcedBackend() {
        String previous = System.getProperty(Gulp.BACKEND_PROPERTY);
        System.setProperty(Gulp.BACKEND_PROPERTY, "test");
        try {
            Game game = new Game() {
                @Override
                public String id() {
                    return "test";
                }

                @Override
                public void onStart() {}
            };

            Gulp.launch(game);

            assertThat(TestGameLauncher.LAUNCHED).containsExactly(game);
        } finally {
            TestGameLauncher.LAUNCHED.clear();
            if (previous == null) {
                System.clearProperty(Gulp.BACKEND_PROPERTY);
            } else {
                System.setProperty(Gulp.BACKEND_PROPERTY, previous);
            }
        }
    }

    private record FakeLauncher(String name, int priority) implements GameLauncher {
        @Override
        public void launch(Game game) {}
    }

    /** Registered in src/test/resources/META-INF/services. */
    public static final class TestGameLauncher implements GameLauncher {
        static final List<Game> LAUNCHED = new ArrayList<>();

        @Override
        public String name() {
            return "test";
        }

        @Override
        public int priority() {
            return -1;
        }

        @Override
        public void launch(Game game) {
            LAUNCHED.add(game);
        }
    }
}
