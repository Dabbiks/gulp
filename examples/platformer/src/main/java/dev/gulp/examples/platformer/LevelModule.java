package dev.gulp.examples.platformer;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.GamepadAxis;
import dev.gulp.api.input.GamepadButton;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.input.Keys;
import dev.gulp.api.math.Rect;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleInfo;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.render.RenderLayerEvent;
import dev.gulp.api.text.TextStyle;
import dev.gulp.api.ui.Transitions;
import dev.gulp.api.world.TileMap;
import dev.gulp.api.world.TileShape;
import dev.gulp.api.world.TileType;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;
import dev.gulp.api.world.WorldSource;

/** Loads the LDtk world, spawns its entities, moves the player and counts coins. */
@ModuleInfo(id = "level")
final class LevelModule extends GameModule {

    private EntityType player;
    private EntityType coin;
    private TileType platform;
    private InputAction left;
    private InputAction right;
    private InputAction jump;
    private int coins;
    private int total;

    @Override
    public void onLoad() {
        var registries = registries();
        platform = registries.register(
                Registries.TILE_TYPE,
                TileType.builder(key("platform")).shape(TileShape.ONE_WAY).build());
        player = registries.register(
                Registries.ENTITY_TYPE,
                EntityType.builder(key("player"))
                        .size(0.75f, 0.95f)
                        .component(() -> new SpriteComponent(GameAssets.Sprites.PLAYER).setAnchor(0.5f, 0.53f))
                        .component(Walker::new)
                        .zIndex(1)
                        .build());
        coin = registries.register(
                Registries.ENTITY_TYPE,
                EntityType.builder(key("coin"))
                        .size(0.75f, 0.75f)
                        .component(() -> new SpriteComponent(GameAssets.Sprites.COIN))
                        .component(Bob::new)
                        .tags("coin")
                        .build());
        left = registries.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("left"))
                        .bind(Keys.A, Keys.LEFT, GamepadAxis.LEFT_X.negative(), GamepadButton.DPAD_LEFT)
                        .build());
        right = registries.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("right"))
                        .bind(Keys.D, Keys.RIGHT, GamepadAxis.LEFT_X.positive(), GamepadButton.DPAD_RIGHT)
                        .build());
        jump = registries.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("jump"))
                        .bind(Keys.SPACE, Keys.W, Keys.UP, GamepadButton.SOUTH)
                        .build());
    }

    @Override
    public void onEnable() {
        worlds().register(
                        "world",
                        WorldSource.ldtk(GameAssets.Maps.WORLD)
                                .settings(WorldSettings.DEFAULT.tileSize(16))
                                .spawn("Player", player)
                                .spawn("Coin", coin)
                                .tile("platform", platform)
                                .onLoad(this::prepare));
        worlds().switchTo("world", Transitions.circleWipe(0.5f));
        on(RenderLayerEvent.class, e -> {
            if (e.layer().name().equals("overlay")) {
                e.draw()
                        .color(Color.rgba(0x00000088))
                        .rect(4, 4, 120, 18)
                        .color(Color.WHITE)
                        .text("Monety: " + coins + " / " + total, 8, 7, TextStyle.of(10));
            }
        });
    }

    private void prepare(World world) {
        // Offsets count from the camera centre: the sky starts at the top of the view, the hills end behind the ground.
        world.parallax().layer(GameAssets.Textures.SKY, 0f).repeatX().offset(0, -8.5f);
        world.parallax().layer(GameAssets.Textures.HILLS, 0.3f).repeatX().offset(0, -2f);
        total = world.query().tag("coin").count();
        Entity hero = world.query().type(player).first();
        Rect bounds = world.tileMap().bounds();
        if (hero != null) {
            world.camera().follow(hero).smoothing(0.15f).deadZone(2f, 1.5f).limits(bounds);
        }
    }

    private static boolean solid(TileMap map, float x, float y, boolean falling, float previousBottom) {
        int tx = (int) Math.floor(x);
        int ty = (int) Math.floor(y);
        TileType tile = map.tile("Ground.grid", tx, ty);
        if (tile == null || !tile.shape().isSolid()) {
            return false;
        }
        // One-way platforms only stop things falling onto them from above.
        return !tile.shape().isOneWay() || (falling && previousBottom <= ty + 0.01f);
    }

    /** Walks, falls and jumps against the solid tiles of the ground layer. */
    final class Walker extends Component {
        private static final float SPEED = 7f;
        private static final float GRAVITY = 40f;
        private static final float JUMP = 15f;
        private float vy;
        private boolean grounded;

        @Override
        protected void onTick() {
            Entity self = entity();
            TileMap map = self.world().tileMap();
            float dt = 1f / engine().targetTps();
            float hw = self.size().x() / 2f;
            float hh = self.size().y() / 2f;
            float vx = input().axis(left, right) * SPEED;
            if (vx != 0f) {
                self.setFlipX(vx < 0f);
            }
            if (grounded && input().justPressed(jump)) {
                vy = -JUMP;
                grounded = false;
            }
            vy = Math.min(vy + GRAVITY * dt, 25f);

            float nx = self.x() + vx * dt;
            float edge = vx > 0 ? nx + hw : nx - hw;
            if (vx != 0f
                    && (solid(map, edge, self.y() - hh + 0.05f, false, 0f)
                            || solid(map, edge, self.y() + hh - 0.05f, false, 0f))) {
                nx = vx > 0 ? (float) Math.floor(edge) - hw - 0.001f : (float) Math.floor(edge) + 1f + hw + 0.001f;
            }
            float bottom = self.y() + hh;
            float ny = self.y() + vy * dt;
            grounded = false;
            if (vy > 0f) {
                float foot = ny + hh;
                if (solid(map, nx - hw + 0.05f, foot, true, bottom)
                        || solid(map, nx + hw - 0.05f, foot, true, bottom)) {
                    ny = (float) Math.floor(foot) - hh;
                    vy = 0f;
                    grounded = true;
                }
            } else if (vy < 0f) {
                float head = ny - hh;
                if (solid(map, nx - hw + 0.05f, head, false, 0f) || solid(map, nx + hw - 0.05f, head, false, 0f)) {
                    ny = (float) Math.floor(head) + 1f + hh;
                    vy = 0f;
                }
            }
            self.setPosition(nx, ny);
            for (Entity found :
                    self.world().query().tag("coin").near(self.position(), 0.8f).list()) {
                found.remove();
                coins++;
                self.world().camera().shake(0.15f, 0.2f);
            }
            if (ny > map.bounds().y() + map.bounds().height() + 5f) {
                self.teleport(3.5f, 8f);
                vy = 0f;
            }
        }
    }

    /** Coins bob up and down. */
    static final class Bob extends Component {
        private float base = Float.NaN;
        private int ticks;

        @Override
        protected void onTick() {
            if (Float.isNaN(base)) {
                base = entity().y();
            }
            ticks++;
            entity().setPosition(entity().x(), base + (float) Math.sin(ticks * 0.08f) * 0.12f);
        }
    }
}
