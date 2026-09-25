package dev.gulp.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gulp.api.Game;
import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.registry.Key;
import dev.gulp.api.render.Light;
import dev.gulp.api.world.TileSet;
import dev.gulp.api.world.TileShape;
import dev.gulp.api.world.TileType;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;
import dev.gulp.backend.headless.HeadlessRunner;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LightRendererTest {

    static final TileType ROCK = TileType.builder(Key.of("light", "rock"))
            .tileSet(TileSet.of(AssetKey.texture("light:textures/sheet"), 16, 16), 0)
            .shape(TileShape.FULL)
            .build();

    private HeadlessRunner runner;

    @AfterEach
    void stop() {
        if (runner != null) {
            runner.stop();
        }
    }

    @Test
    void shadowMapStopsAtTilesAndReachesTheRadiusElsewhere() {
        Game game = new Game() {
            @Override
            public String id() {
                return "light";
            }

            @Override
            public void onStart() {}
        };
        runner = HeadlessRunner.start(game, b -> {
            b.files().useClasspathAssets(true);
            b.files().putAsset("light/textures/sheet.png", "png".getBytes(StandardCharsets.UTF_8));
        });
        for (int i = 0; i < 20 && !runner.engine().isRunning(); i++) {
            runner.step(1);
        }
        World world = game.worlds().create("main", WorldSettings.DEFAULT);
        game.worlds().switchTo("main");
        runner.step(1);
        world.tileMap().fill("ground", -11, 5, 23, 2, ROCK);
        world.tileMap().fill("ground", -11, -3, 1, 8, ROCK);
        WorldImpl impl = (WorldImpl) world;
        LightRenderer renderer = new LightRenderer();
        Light torch = Light.point(Color.WHITE, 5.5f).setPosition(new Vec2(-7f, 3.9f));
        renderer.shadowMap(impl, impl.lighting, torch);
        int down = LightRenderer.BINS / 4;
        int up = LightRenderer.BINS * 3 / 4;
        int left = LightRenderer.BINS / 2;
        assertThat(renderer.reach(down))
                .as("floor 1.1 below")
                .isCloseTo(1.4f, org.assertj.core.api.Assertions.within(0.05f));
        assertThat(renderer.reach(up)).as("open above").isEqualTo(5.5f);
        assertThat(renderer.reach(left))
                .as("wall 3 to the left")
                .isCloseTo(3.3f, org.assertj.core.api.Assertions.within(0.05f));
    }
}
