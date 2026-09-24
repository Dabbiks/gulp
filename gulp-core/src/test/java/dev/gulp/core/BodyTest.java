package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.physics.Body;
import dev.gulp.api.physics.BodyType;
import dev.gulp.api.physics.DistanceJoint;
import dev.gulp.api.physics.EntityCollideEvent;
import dev.gulp.api.physics.EntityLandEvent;
import dev.gulp.api.physics.MotorJoint;
import dev.gulp.api.physics.MouseJoint;
import dev.gulp.api.physics.Physics;
import dev.gulp.api.physics.PreCollideEvent;
import dev.gulp.api.physics.PrismaticJoint;
import dev.gulp.api.physics.RevoluteJoint;
import dev.gulp.api.physics.RopeJoint;
import dev.gulp.api.physics.Shape;
import dev.gulp.api.physics.WeldJoint;
import dev.gulp.api.physics.WheelJoint;
import dev.gulp.api.registry.Key;
import dev.gulp.api.world.World;
import dev.gulp.core.Fixtures.TestGame;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Rigid bodies: stacking, sleeping, bouncing, joints, continuous collision and contact events. */
class BodyTest extends PhysicsFixture {

    static final EntityType CRATE = EntityType.builder(Key.of("test", "crate"))
            .size(1f, 1f)
            .component(() -> new Body(BodyType.DYNAMIC).friction(0.6f))
            .build();
    static final EntityType BALL = EntityType.builder(Key.of("test", "ball"))
            .size(0.5f, 0.5f)
            .component(() -> new Body(BodyType.DYNAMIC).shape(Shape.circle(0.25f)))
            .build();
    static final EntityType ANCHOR = EntityType.builder(Key.of("test", "anchor"))
            .size(0.2f, 0.2f)
            .component(() -> new Body(BodyType.STATIC))
            .build();
    static final EntityType LINK = EntityType.builder(Key.of("test", "link"))
            .size(0.5f, 0.1f)
            .component(() ->
                    new Body(BodyType.DYNAMIC).density(2f).linearDamping(0.5f).angularDamping(0.5f))
            .build();

    private World worldWithFloor(TestGame game) {
        World world = world(game);
        floor(world.tileMap());
        return world;
    }

    @Test
    void cratesFallLandAndSleep() {
        TestGame game = start(g -> {});
        World world = worldWithFloor(game);
        List<String> events = new ArrayList<>();
        game.on(EntityLandEvent.class, e -> events.add("land"));
        game.on(EntityCollideEvent.class, e -> events.add(e.tile() != null ? "tile" : "entity"));
        Entity crate = world.spawn(CRATE, 5f, 5f);
        Body body = crate.get(Body.class);
        assertThat(body.mass()).isCloseTo(1f, within(1e-4f));
        runner.step(180);
        assertThat(crate.y()).isCloseTo(9.5f, within(0.02f));
        assertThat(crate.x()).isCloseTo(5f, within(0.01f));
        assertThat(crate.rotation()).isCloseTo(0f, within(0.5f));
        assertThat(body.isAwake()).isFalse();
        assertThat(events).contains("land", "tile");
        // Moving it by hand teleports and wakes it.
        crate.setPosition(6f, 3f);
        runner.step(1);
        assertThat(body.isAwake()).isTrue();
        runner.step(120);
        assertThat(crate.x()).isCloseTo(6f, within(0.01f));
        assertThat(crate.y()).isCloseTo(9.5f, within(0.02f));
    }

    @Test
    void aStackOfTenCratesStaysStandingAndSleeps() {
        TestGame game = start(g -> {});
        World world = worldWithFloor(game);
        List<Entity> stack = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            stack.add(world.spawn(CRATE, 5f, 9.5f - i * 1.02f));
        }
        runner.step(600);
        for (int i = 0; i < stack.size(); i++) {
            Entity crate = stack.get(i);
            assertThat(crate.x()).as("crate %d x", i).isCloseTo(5f, within(0.05f));
            assertThat(crate.y()).as("crate %d y", i).isCloseTo(9.5f - i, within(0.1f));
            assertThat(crate.rotation()).as("crate %d rotation", i).isCloseTo(0f, within(2f));
            assertThat(crate.get(Body.class).isAwake()).as("crate %d asleep", i).isFalse();
        }
        // A pyramid of circles and boxes also settles.
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col <= row; col++) {
                world.spawn(CRATE, 15f + col * 1.05f - row * 0.525f, 5.5f + row * 1.02f);
            }
        }
        runner.step(600);
        assertThat(world.physics().joints()).isEmpty();
        long awake = world.entities().stream()
                .filter(e -> e.has(Body.class) && e.get(Body.class).isAwake())
                .count();
        assertThat(awake).as("everything asleep").isZero();
        Entity top = world.entities().get(world.entities().size() - 15);
        assertThat(top.y()).as("pyramid top").isCloseTo(5.5f, within(0.1f));
    }

    @Test
    void ballsBounceRollAndReactToForces() {
        TestGame game = start(g -> {});
        World world = worldWithFloor(game);
        Entity ball = world.spawn(BALL, 5f, 5f);
        Body body = ball.get(Body.class).restitution(0.8f);
        assertThat(body.restitution()).isEqualTo(0.8f);
        runner.step(40);
        float highest = Float.MAX_VALUE;
        for (int i = 0; i < 60; i++) {
            runner.step(1);
            if (body.velocity().y() < 0f) {
                highest = Math.min(highest, ball.y());
            }
        }
        assertThat(highest).as("bounced back up").isLessThan(8f);
        // A ramp of a static body: the ball rolls down it.
        EntityType rampType = EntityType.builder(Key.of("test", "ramp"))
                .component(() ->
                        new Body(BodyType.STATIC).shape(Shape.polygon(new Vec2(0, 0), new Vec2(6, 3), new Vec2(0, 3))))
                .build();
        world.spawn(rampType, 20f, 6.9f);
        Entity roller = world.spawn(BALL, 20.5f, 6f);
        roller.get(Body.class).friction(1f);
        runner.step(90);
        assertThat(roller.x()).as("rolled down the ramp").isGreaterThan(22f);
        assertThat(roller.get(Body.class).angularVelocity()).as("spinning").isNotZero();
        // Forces, impulses and torque.
        Entity crate = world.spawn(CRATE, 10f, 9.5f);
        runner.step(2);
        Body push = crate.get(Body.class);
        push.applyImpulse(new Vec2(3f, 0f));
        assertThat(push.velocity().x()).isCloseTo(3f, within(1e-3f));
        runner.step(30);
        // 3 units/s against sliding friction sqrt(0.6 * 1) * gravity 20 stops within about 0.3 units.
        assertThat(crate.x()).isCloseTo(10.29f, within(0.05f));
        push.applyForce(new Vec2(0f, -60f));
        push.applyForce(new Vec2(0f, -60f), crate.position().add(0.5f, 0f));
        push.applyTorque(20f);
        push.applyImpulse(new Vec2(0f, -1f), crate.position().add(-0.5f, 0f));
        runner.step(1);
        assertThat(push.velocity().y()).isLessThan(0f);
        assertThat(push.angularVelocity()).isNotZero();
        push.setVelocity(Vec2.ZERO).setAngularVelocity(0f);
        assertThat(push.velocity()).isEqualTo(Vec2.ZERO);
        push.wakeUp();
        assertThat(push.isAwake()).isTrue();
    }

    @Test
    void settingsAndMass() {
        TestGame game = start(g -> {});
        World world = worldWithFloor(game);
        EntityType heavy = EntityType.builder(Key.of("test", "heavy"))
                .size(2f, 1f)
                .component(() -> new Body(BodyType.DYNAMIC).density(2f))
                .build();
        Entity plank = world.spawn(heavy, 5f, 5f);
        Body body = plank.get(Body.class);
        assertThat(body.mass()).isCloseTo(4f, within(1e-3f));
        body.mass(10f);
        assertThat(body.mass()).isCloseTo(10f, within(1e-3f));
        assertThat(body.fixedMass()).isEqualTo(10f);
        body.linearDamping(0.5f)
                .angularDamping(1f)
                .gravityScale(0f)
                .fixedRotation(true)
                .bullet(true)
                .sleepingAllowed(false);
        assertThat(body.linearDamping()).isEqualTo(0.5f);
        assertThat(body.angularDamping()).isEqualTo(1f);
        assertThat(body.gravityScale()).isZero();
        assertThat(body.isFixedRotation()).isTrue();
        assertThat(body.isBullet()).isTrue();
        assertThat(body.isSleepingAllowed()).isFalse();
        float y = plank.y();
        runner.step(60);
        assertThat(plank.y()).as("floats without gravity").isCloseTo(y, within(1e-3f));
        body.type(BodyType.KINEMATIC).setVelocity(new Vec2(1f, 0f));
        assertThat(body.type()).isEqualTo(BodyType.KINEMATIC);
        assertThat(body.mass()).isZero();
        runner.step(60);
        assertThat(plank.x()).isCloseTo(6f, within(0.02f));
        body.type(BodyType.STATIC);
        body.setVelocity(new Vec2(5f, 5f));
        runner.step(10);
        assertThat(plank.x()).isCloseTo(6f, within(0.02f));
        body.shape(Shape.circle(0.5f)).density(1f);
        assertThat(body.shape()).isEqualTo(Shape.circle(0.5f));
        assertThat(body.density()).isEqualTo(1f);
        Body loose = new Body(BodyType.DYNAMIC);
        assertThat(loose.isAwake()).isFalse();
        loose.setVelocity(new Vec2(1, 2)).setAngularVelocity(30f);
        assertThat(loose.velocity()).isEqualTo(new Vec2(1, 2));
        assertThat(loose.angularVelocity()).isEqualTo(30f);
        assertThatThrownBy(() -> loose.applyForce(Vec2.ONE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void kinematicBodiesPushDynamicOnes() {
        TestGame game = start(g -> {});
        World world = worldWithFloor(game);
        EntityType pusher = EntityType.builder(Key.of("test", "pusher"))
                .size(1f, 2f)
                .component(() -> new Body(BodyType.KINEMATIC))
                .build();
        Entity wall = world.spawn(pusher, 2f, 9f);
        Entity crate = world.spawn(CRATE, 4f, 9.5f);
        runner.step(60);
        wall.get(Body.class).setVelocity(new Vec2(2f, 0f));
        runner.step(90);
        assertThat(wall.x()).isCloseTo(5f, within(0.02f));
        assertThat(crate.x()).as("pushed ahead").isGreaterThan(5.9f);
    }

    @Test
    void chainsAndPendulumsHoldTogether() {
        TestGame game = start(g -> {});
        World world = world(game);
        Physics physics = world.physics();
        Entity top = world.spawn(ANCHOR, 10f, 2f);
        Entity previous = top;
        List<Entity> links = new ArrayList<>();
        List<RevoluteJoint> joints = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Entity link = world.spawn(LINK, 10.25f + i * 0.5f, 2f);
            runner.step(1);
            joints.add(physics.revolute(previous, link, new Vec2(10f + i * 0.5f, 2f)));
            previous = link;
            links.add(link);
        }
        assertThat(physics.joints()).hasSize(10);
        runner.step(600);
        for (RevoluteJoint joint : joints) {
            assertThat(joint.anchorA().distanceTo(joint.anchorB()))
                    .as("joint stays together")
                    .isLessThan(0.02f);
        }
        Entity last = links.get(links.size() - 1);
        assertThat(last.x()).as("hangs down").isCloseTo(10f, within(0.5f));
        assertThat(last.y()).isGreaterThan(6.5f);
        RevoluteJoint first = joints.get(0);
        assertThat(first.entityA()).isEqualTo(top);
        assertThat(first.isValid()).isTrue();
        assertThat(first.reactionForce()).isPositive();
        assertThat(first.collideConnected()).isFalse();
        first.setCollideConnected(true);
        assertThat(first.collideConnected()).isTrue();
        first.enableLimit(-10f, 10f).enableMotor(90f, 50f).disableMotor().disableLimit();
        float angle = first.angle();
        assertThat(angle).isBetween(-180f, 180f);
        // Removing a link removes its joints.
        links.get(4).remove();
        runner.step(1);
        assertThat(physics.joints()).hasSize(8);
        joints.get(0).remove();
        assertThat(joints.get(0).isValid()).isFalse();
        assertThat(physics.joints()).hasSize(7);
    }

    @Test
    void everyJointKeepsItsConstraint() {
        TestGame game = start(g -> {});
        World world = world(game);
        Physics physics = world.physics();
        Entity ground = world.spawn(ANCHOR, 0f, 0f);
        runner.step(1);

        Entity swing = world.spawn(CRATE, 5f, 0f);
        runner.step(1);
        DistanceJoint rod = physics.distance(ground, swing, Vec2.ZERO, swing.position());
        assertThat(rod.length()).isCloseTo(5f, within(1e-3f));
        Entity hanging = world.spawn(CRATE, 3f, 4f);
        runner.step(1);
        RopeJoint rope = physics.rope(ground, hanging, Vec2.ZERO, hanging.position(), 6f);
        assertThat(rope.maxLength()).isEqualTo(6f);
        Entity slider = world.spawn(CRATE, 0f, 3f);
        runner.step(1);
        PrismaticJoint rail =
                physics.prismatic(ground, slider, slider.position(), Vec2.DOWN).enableLimit(-1f, 2f);
        Entity welded = world.spawn(CRATE, -3f, 0f);
        runner.step(1);
        WeldJoint weld = physics.weld(ground, welded, new Vec2(-1.5f, 0f));
        Entity dragged = world.spawn(CRATE, 10f, 10f);
        runner.step(1);
        MouseJoint mouse = physics.mouse(dragged, dragged.position()).setTarget(new Vec2(12f, 10f));
        Entity motored = world.spawn(CRATE, 0f, -3f);
        runner.step(1);
        MotorJoint motor = physics.motor(ground, motored)
                .setLinearOffset(new Vec2(2f, -3f))
                .setMaxForce(500f);
        runner.step(240);
        assertThat(swing.position().distanceTo(Vec2.ZERO)).as("rigid rod").isCloseTo(5f, within(0.05f));
        assertThat(hanging.position().distanceTo(Vec2.ZERO)).as("rope").isLessThanOrEqualTo(6.05f);
        assertThat(slider.x()).as("on the rail").isCloseTo(0f, within(0.02f));
        assertThat(rail.translation()).as("stopped by the limit").isCloseTo(2f, within(0.05f));
        assertThat(welded.position())
                .as("glued")
                .satisfies(p -> assertThat(p.distanceTo(new Vec2(-3f, 0f))).isLessThan(0.2f));
        assertThat(dragged.x()).as("dragged to the target").isCloseTo(12f, within(0.3f));
        assertThat(motored.x()).as("driven by the motor").isCloseTo(2f, within(0.3f));
        // Springs, ranges, motors and setters.
        rod.setSpring(2f, 0.5f).setRange(4f, 6f).setLength(5f);
        rope.setMaxLength(7f);
        rail.disableLimit().enableMotor(1f, 100f).disableMotor();
        weld.setSpring(5f, 0.7f);
        mouse.setMaxForce(100f).setSpring(3f, 0.5f);
        assertThat(mouse.target()).isEqualTo(new Vec2(12f, 10f));
        motor.setAngularOffset(10f).setMaxTorque(50f).setCorrectionFactor(0.5f);
        runner.step(60);
        assertThat(swing.position().distanceTo(Vec2.ZERO))
                .as("spring within range")
                .isBetween(3.9f, 6.1f);

        // A car: chassis and wheels on suspension, driven by motors.
        floor(world.tileMap());
        EntityType chassis = EntityType.builder(Key.of("test", "chassis"))
                .size(3f, 0.5f)
                .component(() -> new Body(BodyType.DYNAMIC))
                .build();
        EntityType wheelType = EntityType.builder(Key.of("test", "wheel"))
                .size(0.8f, 0.8f)
                .component(() ->
                        new Body(BodyType.DYNAMIC).shape(Shape.circle(0.4f)).friction(1f))
                .build();
        Entity car = world.spawn(chassis, -5f, 8f);
        Entity rear = world.spawn(wheelType, -6f, 8.8f);
        Entity front = world.spawn(wheelType, -4f, 8.8f);
        runner.step(1);
        WheelJoint rearAxle = physics.wheel(car, rear, rear.position(), Vec2.UP).setSpring(5f, 0.7f);
        WheelJoint frontAxle = physics.wheel(car, front, front.position(), Vec2.UP);
        rearAxle.enableMotor(720f, 50f);
        frontAxle.enableMotor(720f, 50f);
        runner.step(180);
        assertThat(car.x()).as("the car drove right").isGreaterThan(-3f);
        rearAxle.disableMotor();
        assertThatThrownBy(() -> physics.mouse(ground, Vec2.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> physics.weld(ground, world.spawn(HERO, 0f, 0f), Vec2.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bulletsDoNotPassThroughThinWalls() {
        TestGame game = start(g -> {});
        World world = world(game);
        world.tileMap().fill("ground", 10, 0, 1, 20, SOLID);
        Entity bullet = world.spawn(BALL, 2f, 5f);
        Body body = bullet.get(Body.class).bullet(true).gravityScale(0f);
        runner.step(1);
        body.setVelocity(new Vec2(600f, 0f));
        runner.step(10);
        assertThat(bullet.x()).isLessThan(10f);
    }

    @Test
    void preCollideCanLetBodiesPass() {
        TestGame game = start(g -> {});
        World world = worldWithFloor(game);
        Entity ghost = world.spawn(CRATE, 5f, 5f);
        ghost.tags().add("ghost");
        Entity solid = world.spawn(CRATE, 8f, 5f);
        game.on(PreCollideEvent.class, e -> {
            if (e.a().tags().has("ghost")) {
                assertThat(e.b()).isNull();
                assertThat(e.normal().length()).isCloseTo(1f, within(1e-3f));
                e.setCancelled(true);
            }
        });
        runner.step(120);
        assertThat(ghost.y()).as("fell through the floor").isGreaterThan(11f);
        assertThat(solid.y()).isCloseTo(9.5f, within(0.02f));
    }
}
