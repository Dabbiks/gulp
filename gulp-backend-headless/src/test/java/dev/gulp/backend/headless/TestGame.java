package dev.gulp.backend.headless;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import java.util.ArrayList;
import java.util.List;

/** Game that records its lifecycle calls. */
final class TestGame extends Game {

    final List<String> calls = new ArrayList<>();

    @Override
    public String id() {
        return "test";
    }

    @Override
    public void configure(GameSettings settings) {
        settings.windowSize(320, 180);
    }

    @Override
    public void onStart() {
        calls.add("onStart");
    }

    @Override
    public void onStop() {
        calls.add("onStop");
    }
}
