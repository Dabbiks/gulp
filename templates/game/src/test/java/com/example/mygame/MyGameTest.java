package com.example.mygame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.input.Keys;
import dev.gulp.backend.headless.HeadlessRunner;
import org.junit.jupiter.api.Test;

/** Games are tested without a window: the headless backend runs ticks and injects input. */
class MyGameTest {

    @Test
    void thePlayerWalksRight() {
        MyGame game = new MyGame();
        HeadlessRunner runner = HeadlessRunner.start(game, backend -> backend.files().useClasspathAssets(true));
        try {
            runner.step(5);
            Entity player = game.worlds().active().query().tag("player").first();
            float start = player.x();
            runner.backend().input().inject(l -> l.keyDown(Keys.D.code(), 0, 0, false));
            runner.step(60);
            assertTrue(player.x() > start + 5f, "one second at 6 units per second");
            assertEquals(60, player.get(Walker.class).steps());
        } finally {
            runner.stop();
        }
    }
}
