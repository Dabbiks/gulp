package dev.gulp.examples.platformer;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.DamageType;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityDeathEvent;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.entity.component.Health;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleInfo;
import dev.gulp.api.nav.Path;
import dev.gulp.api.nav.PathFollower;
import dev.gulp.api.physics.Collider;
import dev.gulp.api.physics.CollisionLayer;
import dev.gulp.api.physics.CollisionMask;
import dev.gulp.api.physics.EntityCollideEvent;
import dev.gulp.api.physics.Mover;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.world.MapObject;
import dev.gulp.api.world.World;
import org.jspecify.annotations.Nullable;

/** Slimes that patrol between walls and ledges, hurt from the side and die when stomped; and the lift. */
@ModuleInfo(id = "enemy", dependsOn = "player")
public final class EnemyModule extends GameModule {

    static EntityType slime;
    static EntityType lift;

    @Override
    public void onLoad() {
        slime = registries()
                .register(
                        Registries.ENTITY_TYPE,
                        EntityType.builder(key("slime"))
                                .size(0.9f, 0.7f)
                                .component(() -> new SpriteComponent(GameAssets.Sprites.SLIME).setAnchor(0.5f, 0.55f))
                                .component(Mover::new)
                                .component(() -> new Health(1f))
                                .component(Patrol::new)
                                .tags("enemy")
                                .build());
        lift = registries()
                .register(
                        Registries.ENTITY_TYPE,
                        EntityType.builder(key("lift"))
                                .size(3f, 0.5f)
                                .component(() -> new SpriteComponent(GameAssets.Sprites.LIFT))
                                .component(() -> new Collider().oneWay(true))
                                .build());
    }

    /** Spawns the lift of the map on the path drawn in its "path" field. */
    static @Nullable Entity spawnLift(World world, MapObject object) {
        if (!object.type().equals("Lift") || object.points().isEmpty()) {
            return null;
        }
        return world.spawn(
                lift,
                object.x(),
                object.y(),
                e -> e.add(new PathFollower(Path.fromObject(object)).speed(1.5f).pingPong(true)));
    }

    @Override
    public void onEnable() {
        // Touching a slime from the side hurts and knocks the player back.
        on(EntityCollideEvent.class, e -> {
            Entity other = e.other();
            if (other == null
                    || !e.entity().tags().has("player")
                    || !other.tags().has("enemy")) {
                return;
            }
            if (e.normal().y() < -0.7f) {
                return; // landed on top: the stomp is handled by the player
            }
            Entity player = e.entity();
            if (player.get(Health.class).damage(1f, DamageType.GENERIC, other) > 0f) {
                float away = player.x() < other.x() ? -8f : 8f;
                player.get(Mover.class).setVelocity(new Vec2(away, -6f));
            }
        });
        on(EntityDeathEvent.class, e -> {
            if (e.entity().tags().has("enemy")) {
                e.entity().remove();
            } else if (e.entity().tags().has("player")) {
                e.entity().get(PlayerModule.PlayerController.class).respawn();
            }
        });
    }

    /** Walks one way until a wall or a ledge, then turns. */
    static final class Patrol extends Component {
        private float direction = -1f;

        @Override
        protected void onTick() {
            Entity self = entity();
            Mover mover = self.get(Mover.class);
            mover.moveAndSlide(mover.velocity().withX(direction * 2f));
            Vec2 foot = new Vec2(self.x() + direction * 0.5f, self.y());
            boolean ground =
                    world().physics().raycast(foot, foot.add(0f, 1f), CollisionMask.of(CollisionLayer.TILES)) != null;
            if (mover.isOnWall() || (mover.isOnFloor() && !ground)) {
                direction = -direction;
            }
            self.setFlipX(direction > 0);
        }
    }
}
