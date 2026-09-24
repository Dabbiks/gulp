package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.physics.Collider;
import dev.gulp.api.physics.CollisionLayer;
import dev.gulp.api.physics.CollisionMask;
import dev.gulp.api.physics.Contact;
import dev.gulp.api.physics.EntityCollideEndEvent;
import dev.gulp.api.physics.EntityCollideEvent;
import dev.gulp.api.physics.EntityLandEvent;
import dev.gulp.api.physics.Mover;
import dev.gulp.api.physics.Physics;
import dev.gulp.api.physics.RayHit;
import dev.gulp.api.physics.Shape;
import dev.gulp.api.physics.Trigger;
import dev.gulp.api.physics.TriggerEnterEvent;
import dev.gulp.api.physics.TriggerExitEvent;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.world.DebugView;
import dev.gulp.api.world.TileMap;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;
import dev.gulp.core.Fixtures.TestGame;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Kinematic physics: movers against tiles and colliders, triggers, layers and queries. */
class PhysicsTest extends PhysicsFixture {

    @Test
    void moverFallsLandsWalksAndStopsAtWalls() {
        TestGame game = start(g -> {});
        World world = world(game);
        floor(world.tileMap());
        world.tileMap().fill("ground", 12, 5, 1, 5, SOLID);
        Entity hero = world.spawn(HERO, 5f, 5f);
        List<String> events = new ArrayList<>();
        game.on(EntityLandEvent.class, e -> events.add("land " + Math.round(e.speed())));
        game.on(EntityCollideEvent.class, e -> events.add("collide " + (e.tile() != null ? "tile" : "entity")));
        game.on(EntityCollideEndEvent.class, e -> events.add("end"));
        drive(world, hero, 0f, 90);
        Mover m = mover(hero);
        assertThat(m.isOnFloor()).isTrue();
        assertThat(hero.y()).isCloseTo(9.6f, within(0.01f));
        assertThat(m.velocity().y()).isEqualTo(0f);
        assertThat(m.floorNormal()).isEqualTo(new Vec2(0f, -1f));
        assertThat(events).contains("collide tile").anyMatch(s -> s.startsWith("land "));
        drive(world, hero, 6f, 120);
        assertThat(hero.x()).isCloseTo(11.6f, within(0.01f));
        assertThat(m.isOnWall()).isTrue();
        assertThat(m.wallNormal().x()).isEqualTo(-1f);
        assertThat(m.velocity().x()).isEqualTo(0f);
        assertThat(m.contacts()).isNotEmpty();
        // Walking away ends the wall contact.
        drive(world, hero, -6f, 20);
        assertThat(m.isOnWall()).isFalse();
        assertThat(events).contains("end");
    }

    @Test
    void fastMoversDoNotTunnelThroughThinWalls() {
        TestGame game = start(g -> {});
        World world = world(game);
        floor(world.tileMap());
        world.tileMap().fill("ground", 8, 0, 1, 10, SOLID);
        Entity hero = world.spawn(HERO, 5f, 9.6f);
        drive(world, hero, 0f, 5);
        drive(world, hero, 400f, 10);
        assertThat(hero.x()).isLessThan(8f);
    }

    @Test
    void moversClimbAndDescendSlopes() {
        TestGame game = start(g -> {});
        World world = world(game);
        TileMap map = world.tileMap();
        floor(map);
        // A 45 degree ramp from (10, 10) up to (13, 7), then a plateau.
        for (int i = 0; i < 3; i++) {
            map.setTile("ground", 10 + i, 9 - i, SLOPE);
            map.fill("ground", 10 + i, 10 - i, 1, i, SOLID);
        }
        map.fill("ground", 13, 7, 10, 3, SOLID);
        Entity hero = world.spawn(HERO, 7f, 9.6f);
        drive(world, hero, 0f, 10);
        drive(world, hero, 4f, 120);
        assertThat(hero.x()).as("climbed at walking speed").isCloseTo(15f, within(0.05f));
        assertThat(hero.y()).isCloseTo(6.6f, within(0.05f));
        assertThat(mover(hero).isOnFloor()).isTrue();
        drive(world, hero, -4f, 30);
        // Floor snapping keeps it on the ramp on the way down.
        int airborne = 0;
        for (int i = 0; i < 60; i++) {
            drive(world, hero, -4f, 1);
            if (!mover(hero).isOnFloor()) {
                airborne++;
            }
        }
        assertThat(airborne).isLessThanOrEqualTo(2);
        assertThat(hero.y()).isCloseTo(9.6f, within(0.05f));
    }

    @Test
    void oneWayPlatformsHoldFromAboveOnly() {
        TestGame game = start(g -> {});
        World world = world(game);
        TileMap map = world.tileMap();
        floor(map);
        map.fill("ground", 3, 7, 6, 1, PLATFORM);
        Entity hero = world.spawn(HERO, 5f, 9.6f);
        drive(world, hero, 0f, 5);
        Mover m = mover(hero);
        assertThat(m.jump(14f)).isTrue();
        int highest = 0;
        float top = hero.y();
        for (int i = 0; i < 90; i++) {
            drive(world, hero, 0f, 1);
            if (hero.y() < top) {
                top = hero.y();
                highest = i;
            }
        }
        assertThat(top).as("jumped through from below").isLessThan(7f);
        assertThat(highest).isPositive();
        assertThat(m.isOnFloor()).isTrue();
        assertThat(hero.y()).as("landed on the platform").isCloseTo(6.6f, within(0.02f));
        m.dropThroughPlatform();
        assertThat(m.isDroppingThrough()).isTrue();
        drive(world, hero, 0f, 60);
        assertThat(hero.y()).as("dropped to the floor").isCloseTo(9.6f, within(0.02f));
    }

    @Test
    void coyoteTimeAndJumpBuffer() {
        TestGame game = start(g -> {});
        World world = world(game);
        world.tileMap().fill("ground", 0, 10, 6, 1, SOLID);
        world.tileMap().fill("ground", 20, 10, 6, 1, SOLID);
        Entity hero = world.spawn(HERO, 5f, 9.6f);
        Mover m = mover(hero).coyoteTicks(6).jumpBufferTicks(8);
        assertThat(m.coyoteTicks()).isEqualTo(6);
        assertThat(m.jumpBufferTicks()).isEqualTo(8);
        drive(world, hero, 0f, 5);
        // Walk off the edge; a few ticks later a jump still works.
        while (m.isOnFloor()) {
            drive(world, hero, 3f, 1);
        }
        drive(world, hero, 3f, 3);
        assertThat(m.isOnFloor()).isFalse();
        assertThat(m.canJump()).isTrue();
        assertThat(m.jump(10f)).isTrue();
        assertThat(m.canJump()).isFalse();
        // Falling; a jump pressed just before landing happens on landing.
        Entity second = world.spawn(HERO, 22f, 6f);
        Mover s = mover(second).jumpBufferTicks(8);
        boolean pressed = false;
        boolean jumped = false;
        for (int i = 0; i < 120 && !jumped; i++) {
            if (!pressed && second.y() > 9.0f && s.velocity().y() > 0f) {
                assertThat(s.jump(9f)).as("still in the air").isFalse();
                pressed = true;
            }
            drive(world, second, 0f, 1);
            jumped = pressed && s.velocity().y() < -5f;
        }
        assertThat(jumped).as("the buffered jump fired on landing").isTrue();
    }

    @Test
    void stepsAndMovingPlatforms() {
        TestGame game = start(g -> {});
        World world = world(game);
        floor(world.tileMap());
        world.tileMap().setTile("ground", 9, 9, SOLID);
        Entity hero = world.spawn(HERO, 7f, 9.6f);
        mover(hero).stepHeight(1.1f);
        assertThat(mover(hero).stepHeight()).isEqualTo(1.1f);
        drive(world, hero, 0f, 5);
        drive(world, hero, 3f, 80);
        assertThat(hero.x()).as("walked over the one-tile step").isGreaterThan(10.5f);

        EntityType platform = EntityType.builder(Key.of("test", "platform"))
                .size(3f, 0.5f)
                .component(() -> new Collider().oneWay(true))
                .build();
        Entity lift = world.spawn(platform, 20f, 6f);
        Entity rider = world.spawn(HERO, 20f, 4f);
        drive(world, rider, 0f, 40);
        assertThat(mover(rider).floorEntity()).isEqualTo(lift);
        float before = rider.x();
        for (int i = 0; i < 30; i++) {
            lift.setPosition(lift.x() + 0.05f, lift.y());
            drive(world, rider, 0f, 1);
        }
        assertThat(rider.x() - before).as("carried along").isCloseTo(1.5f, within(0.1f));
        mover(rider).platformCarry(false);
        assertThat(mover(rider).isPlatformCarry()).isFalse();
    }

    @Test
    void topDownMoversSlideAlongWallsAndCollideWithEachOther() {
        TestGame game = start(g -> {});
        World world = world(game);
        world.tileMap().fill("ground", 0, 0, 1, 20, SOLID);
        Entity hero = world.spawn(HERO, 3f, 5f);
        Mover m = mover(hero).topDown(true).gravity(false);
        assertThat(m.isTopDown()).isTrue();
        assertThat(m.hasGravity()).isFalse();
        for (int i = 0; i < 60; i++) {
            m.moveAndSlide(new Vec2(-4f, 2f));
            runner.step(1);
        }
        assertThat(hero.x()).isCloseTo(1.4f, within(0.02f));
        assertThat(hero.y()).as("slid along the wall").isGreaterThan(6.5f);
        assertThat(m.isOnFloor()).isFalse();
        assertThat(m.isOnWall()).isTrue();
        Entity other = world.spawn(HERO, 1.4f, 10f);
        mover(other).topDown(true);
        for (int i = 0; i < 60; i++) {
            m.moveAndSlide(new Vec2(0f, 3f));
            runner.step(1);
        }
        assertThat(hero.y()).as("blocked by the other mover").isLessThan(9.3f);
        List<Contact> contacts = m.contacts();
        assertThat(contacts).anyMatch(c -> c.entity() == other);
    }

    @Test
    void collidersLayersAndTheCollisionMatrix() {
        TestGame game = start(g -> {
            pickup = g.registries().register(Registries.COLLISION_LAYER, CollisionLayer.of(Key.of("test", "pickup")));
            ghost = g.registries().register(Registries.COLLISION_LAYER, CollisionLayer.of(Key.of("test", "ghost")));
        });
        World world = world(game);
        assertThat(CollisionLayer.DEFAULT.bit()).isZero();
        assertThat(CollisionLayer.TILES.bit()).isEqualTo(1);
        assertThat(pickup.bit()).isEqualTo(2);
        floor(world.tileMap());
        Entity wall = world.spawn(WALL, 8f, 8f);
        Entity hero = world.spawn(HERO, 5f, 9.6f);
        drive(world, hero, 5f, 60);
        assertThat(hero.x()).isCloseTo(7.1f, within(0.02f));
        // A ghost passes through the wall but still stands on tiles.
        wall.get(Collider.class).layer(ghost);
        Physics physics = world.physics();
        physics.setCollides(CollisionLayer.DEFAULT, ghost, false);
        assertThat(physics.collides(ghost, CollisionLayer.DEFAULT)).isFalse();
        drive(world, hero, 5f, 60);
        assertThat(hero.x()).isGreaterThan(9f);
        assertThat(mover(hero).isOnFloor()).isTrue();
        physics.setCollides(CollisionLayer.DEFAULT, ghost, true);
        assertThat(physics.collides(ghost, CollisionLayer.DEFAULT)).isTrue();
        // Masks: a collider that only touches pickups is ignored by the default mover.
        Collider only = wall.get(Collider.class).layer(CollisionLayer.DEFAULT).collidesWith(pickup);
        assertThat(only.mask().contains(pickup)).isTrue();
        drive(world, hero, -5f, 90);
        assertThat(hero.x()).isLessThan(7f);
        assertThatThrownBy(() -> physics.setCollides(CollisionLayer.of(Key.of("test", "x")), ghost, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(CollisionMask.ALL.without(ghost).contains(ghost)).isFalse();
        assertThat(CollisionMask.ALL.without(ghost).with(ghost).contains(ghost)).isTrue();
        assertThat(CollisionMask.of(ghost).without(ghost)).isEqualTo(CollisionMask.NONE);
        assertThat(CollisionMask.ALL.toString()).contains("all");
        assertThat(CollisionMask.ALL.without(ghost).toString()).contains("except");
        assertThat(CollisionMask.of(ghost).hashCode())
                .isEqualTo(CollisionMask.of(ghost).hashCode());
        assertThat(ghost.toString()).contains("ghost");
    }

    @Test
    void triggersReportEntitiesEnteringAndLeaving() {
        TestGame game = start(g -> pickup =
                g.registries().register(Registries.COLLISION_LAYER, CollisionLayer.of(Key.of("test", "pickup"))));
        World world = world(game);
        floor(world.tileMap());
        EntityType coinType = EntityType.builder(Key.of("test", "coin"))
                .size(0.5f, 0.5f)
                .component(() -> new Trigger().layer(pickup))
                .build();
        Entity coin = world.spawn(coinType, 8f, 9.5f);
        Trigger trigger = coin.get(Trigger.class);
        assertThat(trigger.layer()).isEqualTo(pickup);
        List<String> log = new ArrayList<>();
        game.on(
                TriggerEnterEvent.class,
                e -> log.add("enter " + e.other().type().key().path() + " " + (e.trigger() == trigger) + " "
                        + (e.entity() == coin)));
        game.on(TriggerExitEvent.class, e -> log.add("exit " + (e.trigger() == trigger) + " " + (e.other() != null)));
        Entity hero = world.spawn(HERO, 5f, 9.6f);
        drive(world, hero, 4f, 60);
        assertThat(log).contains("enter hero true true");
        drive(world, hero, 4f, 60);
        assertThat(log).contains("exit true true");
        assertThat(trigger.triggered()).isEmpty();
        drive(world, hero, -4f, 75);
        assertThat(trigger.triggered()).containsExactly(hero);
        trigger.setEnabled(false);
        runner.step(1);
        assertThat(trigger.triggered()).isEmpty();
        // A mask that leaves out the hero's layer.
        trigger.setEnabled(true);
        trigger.collidesWith(pickup);
        runner.step(2);
        assertThat(trigger.triggered()).isEmpty();
        trigger.mask(CollisionMask.ALL).shape(Shape.circle(3f)).offset(0f, 0f);
        runner.step(2);
        assertThat(trigger.triggered()).containsExactly(hero);
        assertThat(trigger.shape()).isEqualTo(Shape.circle(3f));
        assertThat(trigger.offset()).isEqualTo(Vec2.ZERO);
        coin.remove();
        runner.step(2);
        assertThat(trigger.triggered()).isEmpty();
    }

    @Test
    void queriesSeeTilesAndEntities() {
        TestGame game = start(g -> {});
        World world = world(game);
        floor(world.tileMap());
        world.tileMap().setTile("ground", 12, 9, SLOPE);
        Entity wall = world.spawn(WALL, 8f, 8f);
        Physics physics = world.physics();
        RayHit down = physics.raycast(new Vec2(3.5f, 2f), new Vec2(3.5f, 20f), CollisionMask.ALL);
        assertThat(down).isNotNull();
        assertThat(down.entity()).isNull();
        assertThat(down.tile()).isNotNull();
        assertThat(down.point().y()).isCloseTo(10f, within(1e-4f));
        assertThat(down.normal()).isEqualTo(new Vec2(0f, -1f));
        RayHit right = physics.raycast(new Vec2(2f, 8f), new Vec2(20f, 8f));
        assertThat(right).isNotNull();
        assertThat(right.entity()).isEqualTo(wall);
        assertThat(right.point().x()).isCloseTo(7.5f, within(1e-4f));
        assertThat(right.fraction()).isCloseTo(5.5f / 18f, within(1e-4f));
        List<RayHit> all = physics.raycastAll(new Vec2(2f, 9.5f), new Vec2(20f, 9.5f), CollisionMask.ALL);
        assertThat(all).extracting(h -> h.entity() != null ? "wall" : "tile").containsExactly("wall", "tile");
        assertThat(physics.raycast(new Vec2(2f, 8f), new Vec2(20f, 8f), CollisionMask.of(CollisionLayer.TILES)))
                .isNull();
        assertThat(physics.overlapPoint(new Vec2(8f, 8f), CollisionMask.ALL)).containsExactly(wall);
        assertThat(physics.overlapPoint(new Vec2(3f, 8f), CollisionMask.ALL)).isEmpty();
        assertThat(physics.overlapCircle(new Vec2(7.2f, 8f), 0.4f, CollisionMask.ALL))
                .containsExactly(wall);
        assertThat(physics.overlapRect(new Rect(7f, 6f, 2f, 1f), CollisionMask.ALL))
                .containsExactly(wall);
        assertThat(physics.overlapRect(new Rect(7f, 6f, 2f, 1f), CollisionMask.of(CollisionLayer.TILES)))
                .isEmpty();
        assertThat(physics.isSolidTile(new Vec2(3.5f, 10.5f))).isTrue();
        assertThat(physics.isSolidTile(new Vec2(3.5f, 9.5f))).isFalse();
        assertThat(physics.isSolidTile(new Vec2(12.9f, 9.9f)))
                .as("inside the slope")
                .isTrue();
        assertThat(physics.isSolidTile(new Vec2(12.1f, 9.1f)))
                .as("above the slope")
                .isFalse();
        RayHit cast = physics.shapeCast(Shape.box(1f, 1f), new Vec2(3f, 8f), new Vec2(20f, 8f), CollisionMask.ALL);
        assertThat(cast).isNotNull();
        assertThat(cast.entity()).isEqualTo(wall);
        assertThat(3f + 17f * cast.fraction()).isCloseTo(7f, within(0.02f));
        RayHit fall = physics.shapeCast(Shape.circle(0.5f), new Vec2(3f, 2f), new Vec2(3f, 20f), CollisionMask.ALL);
        assertThat(fall).isNotNull();
        assertThat(fall.tile()).isNotNull();
        assertThat(2f + 18f * fall.fraction()).isCloseTo(9.5f, within(0.02f));
        assertThat(physics.shapeCast(Shape.capsule(0.3f, 1f), new Vec2(3f, 2f), new Vec2(3f, 5f), CollisionMask.ALL))
                .isNull();
        assertThat(physics.gravity()).isEqualTo(WorldSettings.DEFAULT.gravity());
        physics.setGravity(new Vec2(0f, 10f));
        assertThat(physics.gravity()).isEqualTo(new Vec2(0f, 10f));
        assertThat(physics.subSteps()).isEqualTo(4);
        physics.setSubSteps(8);
        assertThat(physics.subSteps()).isEqualTo(8);
        assertThatThrownBy(() -> physics.setSubSteps(0)).isInstanceOf(IllegalArgumentException.class);
        // Debug drawing of every view runs without errors.
        for (DebugView view : DebugView.values()) {
            world.showDebug(view, true);
            assertThat(world.isDebugShown(view)).isTrue();
        }
        runner.step(2);
        world.showDebug(DebugView.SHAPES, false);
        assertThat(world.isDebugShown(DebugView.SHAPES)).isFalse();
    }

    @Test
    void shapesValidateTheirSize() {
        assertThatThrownBy(() -> Shape.box(0f, 1f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Shape.circle(-1f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Shape.capsule(1f, 1f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Shape.polygon(Vec2.ZERO, Vec2.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Shape.chain(List.of(Vec2.ZERO), false)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Shape.box(2f, 4f).bounds()).isEqualTo(new Rect(-1f, -2f, 2f, 4f));
        assertThat(Shape.circle(1f).bounds()).isEqualTo(new Rect(-1f, -1f, 2f, 2f));
        assertThat(Shape.capsule(0.5f, 2f).bounds()).isEqualTo(new Rect(-0.5f, -1f, 1f, 2f));
        assertThat(Shape.polygon(List.of(Vec2.ZERO, new Vec2(2, 0), new Vec2(0, 1)))
                        .bounds())
                .isEqualTo(new Rect(0f, 0f, 2f, 1f));
        assertThat(Shape.segment(Vec2.ZERO, new Vec2(1, 1)).bounds()).isEqualTo(new Rect(0f, 0f, 1f, 1f));
        assertThat(Shape.chain(List.of(Vec2.ZERO, new Vec2(3, -1)), true).bounds())
                .isEqualTo(new Rect(0f, -1f, 3f, 1f));
    }
}
