package dev.gulp.core;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.registry.Key;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;
import dev.gulp.backend.headless.HeadlessBackend;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;

/** Shared setup of the stage 8 tests: a headless game with one active world. */
abstract class JuiceFixture {

    static final EntityType THING =
            EntityType.builder(Key.of("test", "thing")).size(1f, 1f).build();

    @Nullable HeadlessRunner runner;

    @AfterEach
    void tearDown() {
        if (runner != null) {
            runner.stop();
        }
    }

    TestGame start() {
        return start(b -> {});
    }

    TestGame start(Consumer<HeadlessBackend> prepare) {
        TestGame game = new TestGame();
        runner = HeadlessRunner.start(game, b -> {
            b.files().useClasspathAssets(true);
            prepare.accept(b);
        });
        for (int i = 0; i < 20 && !runner.engine().isRunning(); i++) {
            runner.step(1);
        }
        return game;
    }

    World world(TestGame game) {
        World world = game.worlds().create("main", WorldSettings.DEFAULT);
        game.worlds().switchTo("main");
        step(1);
        return world;
    }

    void step(int frames) {
        runner.step(frames);
    }

    /** Runs about a number of seconds at 60 frames per second. */
    void seconds(float seconds) {
        runner.step(Math.round(seconds * 60f));
    }

    TextureRegion region(TestGame game) {
        return game.graphics().texture(new Pixmap(4, 4)).region();
    }

    Entity sprite(TestGame game, World world) {
        TextureRegion region = region(game);
        Entity entity = world.spawn(THING, 0f, 0f);
        entity.add(new SpriteComponent(region));
        return entity;
    }
}
