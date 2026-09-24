package dev.gulp.examples.topdown;

import dev.gulp.api.ai.StateMachine;
import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.DamageType;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityClickEvent;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.entity.component.Health;
import dev.gulp.api.entity.component.Interactable;
import dev.gulp.api.entity.component.Lifetime;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.entity.component.WorldText;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.GamepadAxis;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.input.Keys;
import dev.gulp.api.input.SystemCursor;
import dev.gulp.api.math.Ease;
import dev.gulp.api.math.Noise;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleInfo;
import dev.gulp.api.nav.NavAgent;
import dev.gulp.api.physics.Collider;
import dev.gulp.api.physics.Mover;
import dev.gulp.api.physics.Shape;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.render.RenderLayerEvent;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextStyle;
import dev.gulp.api.world.Chunk;
import dev.gulp.api.world.ChunkData;
import dev.gulp.api.world.DebugView;
import dev.gulp.api.world.Terrain;
import dev.gulp.api.world.TileSet;
import dev.gulp.api.world.TileShape;
import dev.gulp.api.world.TileType;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;
import dev.gulp.api.world.WorldSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Registers the tiles, terrain, entities and actions, generates the world, spawns slimes that wander and chase the
 * player along paths from the navigation grid, and draws a small HUD.
 */
@ModuleInfo(id = "overworld")
final class OverworldModule extends GameModule {

    /** Sea level: noise below this is water. */
    private static final float SEA = -0.05f;

    private static final Noise NOISE = new Noise(20_260_924L);

    private TileType grass;
    private TileType meadow;
    private TileType sand;
    private Terrain shore;
    private EntityType player;
    private EntityType tree;
    private EntityType rock;
    private EntityType floatingText;
    private EntityType slime;
    private InputAction left;
    private InputAction right;
    private InputAction up;
    private InputAction down;
    private InputAction zoomIn;
    private InputAction zoomOut;
    private InputAction debug;
    private int chopped;
    private int ticks;

    @Override
    public void onLoad() {
        var registries = registries();
        TileSet sheet = TileSet.of(GameAssets.Textures.TILES, 16, 16);
        grass = registries.register(
                Registries.TILE_TYPE,
                TileType.builder(key("grass")).tileSet(sheet, 0).build());
        meadow = registries.register(
                Registries.TILE_TYPE,
                TileType.builder(key("meadow")).tileSet(sheet, 1).build());
        sand = registries.register(
                Registries.TILE_TYPE,
                TileType.builder(key("sand")).tileSet(sheet, 2).build());
        List<TileType> shoreTiles = new ArrayList<>();
        for (int mask = 0; mask < 16; mask++) {
            TileType.Builder builder = TileType.builder(key("shore_" + mask)).shape(TileShape.FULL);
            // The open-water piece waves between two frames; the edge pieces are still.
            if (mask == 15) {
                builder.animation(sheet, 0.6f, 4, 5);
            } else {
                builder.tileSet(sheet, 8 + mask);
            }
            shoreTiles.add(registries.register(Registries.TILE_TYPE, builder.build()));
        }
        shore = Terrain.sixteen(key("shore"), shoreTiles);

        player = registries.register(
                Registries.ENTITY_TYPE,
                EntityType.builder(key("player"))
                        .size(0.6f, 0.6f)
                        .component(() -> new SpriteComponent(GameAssets.Sprites.PLAYER).setAnchor(0.5f, 0.8f))
                        .component(() -> new Mover().topDown(true))
                        .component(() -> new Health(5f).invulnerableTicks(60))
                        .component(PlayerControl::new)
                        .zIndex(1)
                        .build());
        tree = registries.register(
                Registries.ENTITY_TYPE,
                EntityType.builder(key("tree"))
                        .size(0.8f, 0.8f)
                        .component(() -> new SpriteComponent(GameAssets.Sprites.TREE).setAnchor(0.5f, 0.85f))
                        // The whole 1 x 1.5 sprite is clickable, not just the trunk-sized body.
                        .component(() ->
                                new Interactable().cursor(SystemCursor.HAND).area(Rect.of(-0.5f, -1.275f, 1f, 1.5f)))
                        // Only the trunk blocks the way.
                        .component(() -> new Collider(Shape.circle(0.25f)).offset(0f, 0.1f))
                        .tags("tree")
                        .build());
        rock = registries.register(
                Registries.ENTITY_TYPE,
                EntityType.builder(key("rock"))
                        .size(0.8f, 0.6f)
                        .component(() -> new SpriteComponent(GameAssets.Sprites.ROCK))
                        .component(() -> new Collider(Shape.circle(0.35f)))
                        .build());
        slime = registries.register(
                Registries.ENTITY_TYPE,
                EntityType.builder(key("slime"))
                        .size(0.6f, 0.5f)
                        .component(() -> new SpriteComponent(GameAssets.Sprites.SLIME).setAnchor(0.5f, 0.7f))
                        .component(() -> new Mover().topDown(true))
                        .component(() -> new NavAgent().speed(2.2f).avoidance(0.9f))
                        .component(SlimeBrain::new)
                        .tags("slime")
                        .build());
        floatingText = registries.register(
                Registries.ENTITY_TYPE,
                EntityType.builder(key("floating_text"))
                        .size(0f, 0f)
                        .component(() -> new Lifetime(Duration.ofMillis(900)))
                        .component(Rise::new)
                        .persistent(false)
                        .build());

        left = registries.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("left"))
                        .bind(Keys.A, Keys.LEFT, GamepadAxis.LEFT_X.negative())
                        .build());
        right = registries.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("right"))
                        .bind(Keys.D, Keys.RIGHT, GamepadAxis.LEFT_X.positive())
                        .build());
        up = registries.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("up"))
                        .bind(Keys.W, Keys.UP, GamepadAxis.LEFT_Y.negative())
                        .build());
        down = registries.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("down"))
                        .bind(Keys.S, Keys.DOWN, GamepadAxis.LEFT_Y.positive())
                        .build());
        zoomIn = registries.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("zoom_in")).bind(Keys.E).build());
        zoomOut = registries.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("zoom_out")).bind(Keys.Q).build());
        debug = registries.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("debug")).bind(Keys.P).build());
    }

    @Override
    public void onEnable() {
        worlds().load("overworld", WorldSource.generator(this::generate).settings(WorldSettings.DEFAULT.seed(7)))
                .thenSync(world -> {
                    Vec2 start = findLand();
                    Entity hero = world.spawn(player, start.x(), start.y(), e -> e.setName("player"));
                    world.camera().follow(hero).smoothing(0.25f).lookAhead(0.15f);
                    world.camera().setZoom(1f);
                    worlds().switchTo("overworld");
                });
        on(EntityClickEvent.class, e -> {
            if (e.entity().tags().has("tree")) {
                chop(e.entity());
            }
        });
        every(1, this::tick);
        on(RenderLayerEvent.class, e -> {
            if (e.layer().name().equals("overlay")) {
                hud(e);
            }
        });
    }

    private void tick() {
        World world = worlds().active();
        if (world == null) {
            return;
        }
        if (input().justPressed(zoomIn)) {
            world.camera().zoomTo(Math.min(4f, world.camera().zoom() * 2f), 0.3f, Ease.OUT_QUAD);
        }
        if (input().justPressed(zoomOut)) {
            world.camera().zoomTo(Math.max(0.25f, world.camera().zoom() / 2f), 0.3f, Ease.OUT_QUAD);
        }
        if (input().justPressed(debug)) {
            boolean shown = !world.isDebugShown(DebugView.PATHS);
            world.showDebug(DebugView.PATHS, shown);
            world.showDebug(DebugView.SHAPES, shown);
        }
        Entity hero = world.entity("player");
        if (hero != null && ++ticks % 90 == 0 && world.query().tag("slime").count() < 6) {
            // A slime appears somewhere on land, out of sight but not far.
            for (int attempt = 0; attempt < 10; attempt++) {
                Vec2 spot = hero.position()
                        .add(Vec2.fromAngle(world.rng().nextFloat() * 360f).scale(11f));
                if (height((int) Math.floor(spot.x()), (int) Math.floor(spot.y())) > SEA + 0.1f) {
                    world.spawn(slime, spot.x(), spot.y());
                    break;
                }
            }
        }
    }

    private void chop(Entity target) {
        World world = target.world();
        world.spawn(
                floatingText,
                target.x(),
                target.y() - 1f,
                e -> e.add(new WorldText(Text.of("+1 drewno").color(Color.rgb(0xffec27)))
                        .style(TextStyle.of(12).outline(1, Color.BLACK))));
        world.camera().shake(0.3f, 0.3f);
        target.remove();
        chopped++;
    }

    private void hud(RenderLayerEvent event) {
        World world = worlds().active();
        if (world == null) {
            return;
        }
        Entity hero = world.entity("player");
        String position = hero == null ? "" : "x " + Math.round(hero.x()) + ", y " + Math.round(hero.y());
        float hearts = hero == null ? 0f : hero.get(Health.class).current();
        event.draw()
                .color(Color.rgba(0x00000088))
                .rect(4, 4, 250, 42)
                .color(Color.WHITE)
                .text(position + "   drewno: " + chopped + "   zdrowie: " + Math.round(hearts), 8, 8, TextStyle.of(10))
                .text(
                        "chunki: " + world.loadedChunks().size() + "   encje: " + world.entityCount() + "   FPS: "
                                + Math.round(display().fps()),
                        8,
                        22,
                        TextStyle.of(10));
    }

    // ------------------------------------------------------------------ world generation

    private static float height(int x, int y) {
        float h = NOISE.fractal(Noise.Type.SIMPLEX, x * 0.035f, y * 0.035f, 4, 2f, 0.5f);
        // Fewer islands far from the start, so there is always somewhere to explore nearby.
        return h - Math.min(0.3f, (x * x + y * y) * 0.00002f) + 0.1f;
    }

    private static boolean isWater(int x, int y) {
        return height(x, y) < SEA;
    }

    /** Runs outside the tick: uses only its arguments and the immutable noise. */
    private void generate(ChunkData data, int chunkX, int chunkY, dev.gulp.api.math.Rng rng) {
        for (int ly = 0; ly < Chunk.SIZE; ly++) {
            for (int lx = 0; lx < Chunk.SIZE; lx++) {
                int x = data.originX() + lx;
                int y = data.originY() + ly;
                float h = height(x, y);
                if (h < SEA) {
                    int mask = (isWater(x, y - 1) ? 1 : 0)
                            | (isWater(x + 1, y - 1) ? 2 : 0)
                            | (isWater(x + 1, y) ? 4 : 0)
                            | (isWater(x + 1, y + 1) ? 8 : 0)
                            | (isWater(x, y + 1) ? 16 : 0)
                            | (isWater(x - 1, y + 1) ? 32 : 0)
                            | (isWater(x - 1, y) ? 64 : 0)
                            | (isWater(x - 1, y - 1) ? 128 : 0);
                    data.setTile("ground", lx, ly, shore.tileFor(mask));
                } else if (h < SEA + 0.08f) {
                    data.setTile("ground", lx, ly, sand);
                } else {
                    data.setTile("ground", lx, ly, h > 0.35f ? meadow : grass);
                    if (rng.chance(h > 0.3f ? 0.06f : 0.02f)) {
                        data.spawn(tree, lx + 0.5f, ly + 0.5f);
                    } else if (rng.chance(0.004f)) {
                        data.spawn(rock, lx + 0.5f, ly + 0.5f);
                    }
                }
            }
        }
    }

    private static Vec2 findLand() {
        for (int radius = 0; radius < 200; radius++) {
            for (int x = -radius; x <= radius; x++) {
                if (height(x, radius) > SEA + 0.1f) {
                    return new Vec2(x + 0.5f, radius + 0.5f);
                }
            }
        }
        return Vec2.ZERO;
    }

    /** Moves the player with the move actions; water, trees and rocks block the way. Slimes bite. */
    final class PlayerControl extends Component {
        private static final float SPEED = 5f;
        private Vec2 start = Vec2.ZERO;

        @Override
        protected void onSpawn() {
            start = entity().position();
        }

        @Override
        protected void onTick() {
            Vec2 move = input().vector(left, right, up, down);
            Entity self = entity();
            self.get(Mover.class).moveAndSlide(move.scale(SPEED));
            if (move.x() != 0) {
                self.setFlipX(move.x() < 0);
            }
            Health health = self.get(Health.class);
            Entity biter = self.world()
                    .query()
                    .tag("slime")
                    .near(self.position(), 0.7f)
                    .first();
            if (biter != null && health.damage(1f, DamageType.GENERIC, biter) > 0f) {
                self.world()
                        .spawn(
                                floatingText,
                                self.x(),
                                self.y() - 1f,
                                e -> e.add(new WorldText(Text.of("-1").color(Color.rgb(0xff004d)))
                                        .style(TextStyle.of(12).outline(1, Color.BLACK))));
                self.world().camera().shake(0.4f, 0.3f);
            }
            if (health.isDead()) {
                self.teleport(start.x(), start.y());
                health.revive(health.max());
            }
        }
    }

    /** Slimes wander around until the player comes near, then chase along paths from the navigation grid. */
    final class SlimeBrain extends Component {
        private StateMachine<Mode> brain;
        private int wait;

        @Override
        protected void onSpawn() {
            NavAgent agent = entity().get(NavAgent.class);
            brain = new StateMachine<>(Mode.WANDER)
                    .onEnter(Mode.WANDER, () -> {
                        agent.stop();
                        wait = 0;
                    })
                    .onTick(Mode.WANDER, () -> {
                        if (--wait <= 0) {
                            wait = 60 + world().rng().nextInt(90);
                            Vec2 spot = entity().position()
                                    .add(Vec2.fromAngle(world().rng().nextFloat() * 360f)
                                            .scale(3f));
                            if (world().navGrid().isPassable((int) Math.floor(spot.x()), (int) Math.floor(spot.y()))) {
                                agent.moveTo(spot);
                            }
                        }
                    })
                    .onEnter(Mode.CHASE, () -> {
                        Entity hero = world().entity("player");
                        if (hero != null) {
                            agent.follow(hero);
                        }
                    })
                    .transition(Mode.WANDER, Mode.CHASE, () -> distanceToPlayer() < 7f)
                    .transition(Mode.CHASE, Mode.WANDER, () -> distanceToPlayer() > 11f);
        }

        private float distanceToPlayer() {
            Entity hero = world().entity("player");
            return hero == null ? Float.MAX_VALUE : hero.position().distanceTo(entity().position());
        }

        @Override
        protected void onTick() {
            brain.update();
            if (distanceToPlayer() > 30f) {
                entity().remove();
            }
        }
    }

    enum Mode {
        WANDER,
        CHASE
    }

    /** Floats text upwards until its lifetime ends. */
    static final class Rise extends Component {
        @Override
        protected void onTick() {
            entity().setPosition(entity().x(), entity().y() - 0.03f);
        }
    }
}
