package dev.gulp.examples.platformer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.api.world.World;
import dev.gulp.backend.headless.HeadlessRunner;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The milestone of stage 10: the platformer keeps its progress across a restart. */
class ProgressSaveTest {

    private @Nullable HeadlessRunner runner;

    @AfterEach
    void tearDown() {
        if (runner != null) {
            runner.stop();
        }
    }

    private PlatformerGame start() {
        PlatformerGame game = new PlatformerGame();
        HeadlessRunner started = HeadlessRunner.start(game, b -> b.files().useClasspathAssets(true));
        runner = started;
        for (int i = 0; i < 30 && !started.engine().isRunning(); i++) {
            started.step(1);
        }
        assertThat(started.engine().isRunning()).isTrue();
        return game;
    }

    private <T> T await(Promise<T> promise) {
        List<T> result = new ArrayList<>();
        List<Throwable> failure = new ArrayList<>();
        promise.thenSync(result::add).onFailure(failure::add);
        for (int i = 0; i < 60 && result.isEmpty() && failure.isEmpty(); i++) {
            runner.step(1);
        }
        if (!failure.isEmpty()) {
            throw new AssertionError("Promise failed", failure.getFirst());
        }
        assertThat(result).hasSize(1);
        return result.getFirst();
    }

    @Test
    void coinsAndTheLevelSurviveARestart() {
        PlatformerGame game = start();
        game.saves().autosave(PlatformerGame.SLOT, Duration.ofMinutes(5));
        game.worlds().switchTo("level1");
        runner.step(60);
        World level = game.worlds().get("level1");
        assertThat(level).isNotNull();
        int coinsAtStart = level.query().tag("coin").count();
        assertThat(coinsAtStart).isPositive();
        Entity player = level.query().tag("player").first();
        Entity coin = level.query().tag("coin").first();
        player.setPosition(coin.x(), coin.y());
        runner.step(40);
        HudModule hud = game.modules().get(HudModule.class);
        assertThat(hud.coins().get()).isEqualTo(1);
        assertThat(level.query().tag("coin").count()).isEqualTo(coinsAtStart - 1);
        runner.step(60);
        assertThat(await(game.saves().slot(PlatformerGame.SLOT).exists())).isTrue();
        byte[] saved = await(game.saves().slot(PlatformerGame.SLOT).exportData());
        runner.stop();

        PlatformerGame again = start();
        await(again.saves().slot(PlatformerGame.SLOT).importData(saved));
        await(again.saves().slot(PlatformerGame.SLOT).load());
        World restored = again.worlds().active();
        assertThat(restored).isNotNull();
        assertThat(restored.name()).isEqualTo("level1");
        assertThat(again.modules().get(HudModule.class).coins().get()).isEqualTo(1);
        assertThat(restored.query().tag("coin").count()).isEqualTo(coinsAtStart - 1);
        assertThat(restored.query().tag("player").count()).isEqualTo(1);
        assertThat(restored.query().tag("enemy").count()).isPositive();
        assertThat(restored.entities().stream()
                        .filter(e -> e.type() == EnemyModule.lift)
                        .count())
                .as("the lift comes from the map, once")
                .isEqualTo(1);
    }
}
