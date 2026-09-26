package dev.gulp.examples.bench;

import static dev.gulp.api.ui.Ui.*;

import dev.gulp.api.Game;
import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.particle.EmitterConfig;
import dev.gulp.api.particle.ParticleEffect;
import dev.gulp.api.registry.Key;
import dev.gulp.api.ui.Grid;
import dev.gulp.api.ui.Node;
import dev.gulp.api.world.TileType;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;

/**
 * The benchmark scenes of section 20.5, with the counts the budgets name: sprites drawn at 60 FPS, entities ticked at
 * 60 TPS, particles, a screen of widgets and a large tile map with a typical crowd on it.
 */
public enum BenchScene {
    /** Static sprites spread over the screen: the cost of culling, sorting and batching. */
    SPRITES(50_000, 10_000),
    /** Entities whose component moves them every tick: the cost of ticking. */
    ENTITIES(20_000, 5_000),
    /** Emitters keeping particles alive: the cost of simulating and drawing them. */
    PARTICLES(20_000, 10_000),
    /** A grid of widgets, laid out once and drawn every frame. */
    UI(600, 300),
    /** A 256 x 256 tile map with a few hundred moving entities: the "typical scene" of the budgets. */
    MAP(500, 500);

    private final int desktopCount;
    private final int webCount;

    BenchScene(int desktopCount, int webCount) {
        this.desktopCount = desktopCount;
        this.webCount = webCount;
    }

    /**
     * Returns the count the budget names for a platform.
     *
     * @param web whether the game runs in a browser
     * @return sprites, entities, particles, widgets or map entities
     */
    public int count(boolean web) {
        return web ? webCount : desktopCount;
    }

    /** Moves its entity on a small circle, one step per tick. */
    public static final class Orbit extends Component {
        private float angle;

        /** Creates the component. */
        public Orbit() {}

        @Override
        protected void onSpawn() {
            angle = entity().x() * 0.37f + entity().y() * 0.11f;
        }

        @Override
        protected void onTick() {
            angle += 0.05f;
            entity().setPosition(
                            entity().x() + (float) Math.cos(angle) * 0.02f,
                            entity().y() + (float) Math.sin(angle) * 0.02f);
        }
    }

    /**
     * Builds the scene in a fresh world; the caller switches to it.
     *
     * @param game the game
     * @param count how many things to create
     * @return the world
     */
    public World build(Game game, int count) {
        String name = name().toLowerCase(java.util.Locale.ROOT);
        World world = game.worlds().create(name + "-" + count, WorldSettings.DEFAULT);
        TextureRegion region = square(game);
        int side = (int) Math.ceil(Math.sqrt(count));
        switch (this) {
            case SPRITES -> {
                EntityType dot = EntityType.builder(Key.of("bench", "sprite"))
                        .size(0.5f, 0.5f)
                        .component(() -> new SpriteComponent(region))
                        .build();
                spread(world, dot, count, side, 30f);
            }
            case ENTITIES -> {
                EntityType mover = EntityType.builder(Key.of("bench", "mover"))
                        .size(0.5f, 0.5f)
                        .component(Orbit::new)
                        .build();
                spread(world, mover, count, side, 30f);
            }
            case PARTICLES -> {
                // Each emitter keeps about 1000 particles alive: 500 per second living two seconds.
                ParticleEffect fountain = ParticleEffect.builder(Key.of("bench", "fountain"))
                        .emitter(EmitterConfig.builder()
                                .rate(500f)
                                .loop(true)
                                .speed(1f, 4f)
                                .spread(360f)
                                .lifetime(1.8f, 2.2f)
                                .build())
                        .build();
                int emitters = Math.max(1, count / 1000);
                for (int i = 0; i < emitters; i++) {
                    world.spawnParticles(fountain, new Vec2((i % 5 - 2) * 6f, (i / 5 - 2) * 5f));
                }
            }
            case UI -> {
                Node<?>[] cells = new Node<?>[count];
                for (int i = 0; i < count; i++) {
                    cells[i] = i % 3 == 0 ? button("Button " + i) : i % 3 == 1 ? label("Label " + i) : checkbox("" + i);
                }
                Grid grid = grid(30, cells).hGap(2).vGap(2);
                game.ui().hud().add(game, scroll(grid).fillParent());
            }
            case MAP -> {
                TileType grass = TileType.builder(Key.of("bench", "grass"))
                        .region(region)
                        .build();
                for (int y = -128; y < 128; y++) {
                    for (int x = -128; x < 128; x++) {
                        if (((x * 7 + y * 13) & 3) != 0) {
                            world.tileMap().setTile("ground", x, y, grass);
                        }
                    }
                }
                EntityType walker = EntityType.builder(Key.of("bench", "walker"))
                        .size(0.8f, 0.8f)
                        .component(() -> new SpriteComponent(region))
                        .component(Orbit::new)
                        .build();
                spread(world, walker, count, side, 20f);
                game.ui().hud().add(game, label("Coins: 42"));
            }
        }
        return world;
    }

    private static void spread(World world, EntityType type, int count, int side, float extent) {
        float step = extent * 2f / side;
        for (int i = 0; i < count; i++) {
            world.spawn(type, -extent + (i % side) * step, -extent + (i / side) * step);
        }
    }

    private static TextureRegion square(Game game) {
        Pixmap pixmap = new Pixmap(8, 8);
        pixmap.fill(Color.rgb(0x66bb6a));
        return game.graphics().texture(pixmap).region();
    }
}
