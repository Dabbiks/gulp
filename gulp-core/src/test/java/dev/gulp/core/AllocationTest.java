package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.event.Event;
import dev.gulp.api.event.lifecycle.TickEndEvent;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.physics.Body;
import dev.gulp.api.physics.BodyType;
import dev.gulp.api.physics.Collider;
import dev.gulp.api.physics.Mover;
import dev.gulp.api.registry.Key;
import dev.gulp.api.ui.Ui;
import dev.gulp.api.world.TileShape;
import dev.gulp.api.world.TileType;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;
import java.lang.management.ManagementFactory;
import org.junit.jupiter.api.Test;

/**
 * The budget of section 20.5: no allocations per frame in a steady state. Each scene runs until it settles, then the
 * main thread's allocated bytes are read per window of frames; a median below 16 bytes per frame means no object is
 * created every frame (the smallest is 16 bytes), while the JVM's own occasional allocations and rare growth of reused
 * buffers still pass.
 */
class AllocationTest extends UiFixture {

    private static final int FRAMES = 100;
    private static final int WINDOWS = 10;
    private static final long BUDGET_PER_FRAME = 16;

    /** Turns its entity every tick. */
    static final class Spin extends Component {
        @Override
        protected void onTick() {
            entity().setRotation(entity().rotation() + 1f);
        }
    }

    /** Walks left and right on a mover. */
    static final class Pace extends Component {
        private static final Vec2 RIGHT = new Vec2(3f, 0f);
        private static final Vec2 LEFT = new Vec2(-3f, 0f);
        private boolean right = true;

        @Override
        protected void onTick() {
            // Constant vectors: the game's own allocations are not the engine's.
            Mover mover = entity().get(Mover.class);
            mover.moveAndSlide(right ? RIGHT : LEFT);
            if (mover.isOnWall()) {
                right = !right;
            }
        }
    }

    /** An event game code fires every tick. */
    static final class PingEvent extends Event {}

    /**
     * Returns the median allocation per frame over ten windows of 100 frames, after the JIT settled: a per-frame
     * allocation shows in every window, while warm-up noise of the compiler does not move the median.
     */
    private long bytesPerFrame() {
        step(1200);
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long[] windows = new long[WINDOWS];
        for (int w = 0; w < WINDOWS; w++) {
            long before = threads.getThreadAllocatedBytes(thread);
            step(FRAMES);
            windows[w] = (threads.getThreadAllocatedBytes(thread) - before) / FRAMES;
        }
        java.util.Arrays.sort(windows);
        return windows[WINDOWS / 2];
    }

    private TextureRegion region() {
        Pixmap pixmap = new Pixmap(4, 4);
        pixmap.fill(Color.WHITE);
        return game.graphics().texture(pixmap).region();
    }

    @Test
    void spritesTilesAndTheHudDrawWithoutAllocating() {
        startUi();
        TextureRegion region = region();
        EntityType spinner = EntityType.builder(Key.of("test", "spinner"))
                .component(() -> new SpriteComponent(region))
                .component(Spin::new)
                .build();
        TileType stone =
                TileType.builder(Key.of("test", "stone")).region(region).build();
        World world = game.worlds().create("sprites", WorldSettings.DEFAULT);
        game.worlds().switchTo("sprites");
        for (int i = 0; i < 500; i++) {
            world.spawn(spinner, i % 25 - 12, i / 25f - 10);
        }
        for (int x = -40; x < 40; x++) {
            world.tileMap().setTile("ground", x, 12, stone);
        }
        ui.hud().add(game, Ui.column(Ui.label("Coins: 10"), Ui.progressBar(dev.gulp.api.ui.State.of(0.5f))));
        assertThat(bytesPerFrame()).isLessThan(BUDGET_PER_FRAME);
    }

    @Test
    void physicsStepsWithoutAllocating() {
        startUi();
        TextureRegion region = region();
        EntityType walker = EntityType.builder(Key.of("test", "walker"))
                .size(0.8f, 0.8f)
                .component(() -> new SpriteComponent(region))
                .component(Mover::new)
                .component(Pace::new)
                .build();
        EntityType crate = EntityType.builder(Key.of("test", "crate"))
                .size(1f, 1f)
                .component(() -> new Collider())
                .component(() -> new Body(BodyType.DYNAMIC))
                .build();
        TileType wall = TileType.builder(Key.of("test", "wall"))
                .region(region)
                .shape(TileShape.FULL)
                .build();
        World world = game.worlds().create("physics", WorldSettings.DEFAULT.gravity(new Vec2(0, 20)));
        game.worlds().switchTo("physics");
        for (int x = -30; x <= 30; x++) {
            world.tileMap().setTile("ground", x, 5, wall);
        }
        for (int y = -5; y < 5; y++) {
            world.tileMap().setTile("ground", -30, y, wall);
            world.tileMap().setTile("ground", 30, y, wall);
        }
        for (int i = 0; i < 40; i++) {
            world.spawn(walker, -25 + i, 3);
        }
        for (int i = 0; i < 20; i++) {
            world.spawn(crate, -20 + i * 2, -3);
        }
        assertThat(bytesPerFrame()).isLessThan(BUDGET_PER_FRAME);
    }

    @Test
    void eventsAndTasksRunWithoutAllocating() {
        startUi();
        int[] counts = new int[3];
        PingEvent ping = new PingEvent();
        game.on(TickEndEvent.class, e -> counts[0]++);
        game.on(PingEvent.class, e -> counts[1]++);
        game.every(1, () -> {
            counts[2]++;
            game.events().call(ping);
        });
        assertThat(bytesPerFrame()).isLessThan(BUDGET_PER_FRAME);
        assertThat(counts[0]).isPositive();
        assertThat(counts[1]).isEqualTo(counts[2]);
    }
}
