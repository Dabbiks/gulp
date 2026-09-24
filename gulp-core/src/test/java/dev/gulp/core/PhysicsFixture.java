package dev.gulp.core;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.physics.Collider;
import dev.gulp.api.physics.CollisionLayer;
import dev.gulp.api.physics.Mover;
import dev.gulp.api.registry.Key;
import dev.gulp.api.world.TileMap;
import dev.gulp.api.world.TileSet;
import dev.gulp.api.world.TileShape;
import dev.gulp.api.world.TileType;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;
import dev.gulp.backend.headless.HeadlessBackend;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;

/** Shared setup of the physics tests: a headless game with one world and a few tile and entity types. */
abstract class PhysicsFixture {

    static final TileSet SHEET = TileSet.of(AssetKey.texture("test:textures/sheet"), 16, 16);
    static final TileType SOLID = TileType.builder(Key.of("test", "solid"))
            .tileSet(SHEET, 0)
            .shape(TileShape.FULL)
            .build();
    static final TileType SLOPE = TileType.builder(Key.of("test", "slope"))
            .tileSet(SHEET, 1)
            .shape(TileShape.SLOPE_RIGHT)
            .build();
    static final TileType PLATFORM = TileType.builder(Key.of("test", "platform"))
            .tileSet(SHEET, 2)
            .shape(TileShape.ONE_WAY)
            .build();

    static CollisionLayer pickup;
    static CollisionLayer ghost;

    static final EntityType HERO = EntityType.builder(Key.of("test", "hero"))
            .size(0.8f, 0.8f)
            .component(Mover::new)
            .build();
    static final EntityType WALL = EntityType.builder(Key.of("test", "wall"))
            .size(1f, 4f)
            .component(Collider::new)
            .build();

    @Nullable HeadlessRunner runner;

    @AfterEach
    void tearDown() {
        if (runner != null) {
            runner.stop();
        }
    }

    private static void put(HeadlessBackend backend, String path, String text) {
        backend.files().putAsset(path, text.getBytes(StandardCharsets.UTF_8));
    }

    TestGame start(Consumer<TestGame> onLoad) {
        TestGame game = new TestGame();
        game.onLoad = () -> onLoad.accept(game);
        runner = HeadlessRunner.start(game, b -> {
            b.files().useClasspathAssets(true);
            put(b, "test/textures/sheet.png", "png");
        });
        for (int i = 0; i < 20 && !runner.engine().isRunning(); i++) {
            runner.step(1);
        }
        return game;
    }

    World world(TestGame game) {
        World world = game.worlds().create("main", WorldSettings.DEFAULT);
        game.worlds().switchTo("main");
        runner.step(1);
        return world;
    }

    /** A floor on row 10 from x = -10 to 30. */
    static void floor(TileMap map) {
        map.fill("ground", -10, 10, 40, 1, SOLID);
    }

    Mover mover(Entity entity) {
        return entity.get(Mover.class);
    }

    /** Drives a mover every tick with a fixed horizontal speed, like a controller would. */
    void drive(World world, Entity hero, float speed, int ticks) {
        for (int i = 0; i < ticks; i++) {
            Mover m = mover(hero);
            m.moveAndSlide(m.velocity().withX(speed));
            runner.step(1);
        }
    }
}
