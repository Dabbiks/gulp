package dev.gulp.examples.platformer;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.DamageType;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.entity.component.Health;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.input.Input;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleInfo;
import dev.gulp.api.physics.Mover;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.world.TileShape;
import dev.gulp.api.world.TileType;

/** The player: a mover with coyote time and a jump buffer, stomping on enemies and respawning after a fall. */
@ModuleInfo(id = "player")
public final class PlayerModule extends GameModule {

    static EntityType player;
    static TileType platform;

    @Override
    public void onLoad() {
        platform = registries()
                .register(
                        Registries.TILE_TYPE,
                        TileType.builder(key("platform"))
                                .shape(TileShape.ONE_WAY)
                                .build());
        player = registries()
                .register(
                        Registries.ENTITY_TYPE,
                        EntityType.builder(key("player"))
                                .size(0.75f, 0.95f)
                                .component(() -> new SpriteComponent(GameAssets.Sprites.PLAYER).setAnchor(0.5f, 0.53f))
                                .component(() -> new Mover().coyoteTicks(6).jumpBufferTicks(6))
                                .component(() -> new Health(3f).invulnerableTicks(60))
                                .component(PlayerController::new)
                                .zIndex(1)
                                .tags("player")
                                .build());
    }

    /** Reads the controls every tick and moves the player. */
    public static final class PlayerController extends Component {
        private Mover mover;
        private Vec2 start = Vec2.ZERO;

        /** Creates the controller. */
        public PlayerController() {}

        @Override
        protected void onSpawn() {
            mover = entity().get(Mover.class);
            start = entity().position();
            world().camera()
                    .follow(entity())
                    .smoothing(0.15f)
                    .deadZone(2f, 1.5f)
                    .limits(world().tileMap().bounds());
        }

        @Override
        protected void onTick() {
            Input input = input();
            float x = input.axis(PlatformerGame.moveLeft, PlatformerGame.moveRight) * 7f; // units per second
            if (input.justPressed(PlatformerGame.jump)) {
                if (input.pressed(PlatformerGame.down) && mover.isOnFloor()) {
                    mover.dropThroughPlatform();
                } else {
                    mover.jump(12f); // jumps now, or on landing within the jump buffer
                }
            }
            mover.moveAndSlide(mover.velocity().withX(x)); // gravity is added by the mover
            if (x != 0) {
                entity().setFlipX(x < 0);
            }
            Entity under = mover.floorEntity();
            if (under != null && under.tags().has("enemy")) {
                under.get(Health.class).damage(1f, DamageType.GENERIC, entity());
                mover.setVelocity(mover.velocity().withY(-9f));
            }
            if (entity().y()
                    > world().tileMap().bounds().y()
                            + world().tileMap().bounds().height()
                            + 4f) {
                respawn();
            }
        }

        /** Puts the player back at the start with full health. */
        void respawn() {
            entity().teleport(start.x(), start.y());
            mover.setVelocity(Vec2.ZERO);
            Health health = entity().get(Health.class);
            health.revive(health.max());
        }
    }
}
