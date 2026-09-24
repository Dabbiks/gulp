package dev.gulp.examples.sandbox;

import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.input.Keys;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleInfo;
import dev.gulp.api.physics.Body;
import dev.gulp.api.physics.BodyType;
import dev.gulp.api.physics.CollisionMask;
import dev.gulp.api.physics.MouseJoint;
import dev.gulp.api.physics.Physics;
import dev.gulp.api.physics.Shape;
import dev.gulp.api.physics.WheelJoint;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.render.Draw;
import dev.gulp.api.render.RenderLayerEvent;
import dev.gulp.api.text.TextStyle;
import dev.gulp.api.world.DebugView;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Builds the scene, drives the car, drags bodies with a mouse joint and draws every body from its shape. */
@ModuleInfo(id = "sandbox")
final class SandboxModule extends GameModule {

    private static final Color GROUND = Color.rgb(0x5f574f);
    private static final Color CRATE = Color.rgb(0xffa300);
    private static final Color PLANK = Color.rgb(0xab5236);
    private static final Color BALL = Color.rgb(0x29adff);
    private static final Color CAR = Color.rgb(0xff004d);
    private static final Color WHEEL = Color.rgb(0xc2c3c7);
    private static final Color SLEEPING = Color.rgb(0x83769c);

    private EntityType ground;
    private EntityType crate;
    private EntityType plank;
    private EntityType ball;
    private EntityType post;
    private EntityType chassis;
    private EntityType wheel;
    private InputAction left;
    private InputAction right;
    private InputAction drop;
    private InputAction reset;
    private InputAction debug;

    private World world;
    private final List<WheelJoint> axles = new ArrayList<>();
    private @Nullable MouseJoint drag;
    private int debugMode;

    @Override
    public void onLoad() {
        var r = registries();
        ground = r.register(
                Registries.ENTITY_TYPE,
                EntityType.builder(key("ground"))
                        .component(() -> new Body(BodyType.STATIC))
                        .build());
        crate = r.register(
                Registries.ENTITY_TYPE,
                EntityType.builder(key("crate"))
                        .size(1f, 1f)
                        .component(() -> new Body(BodyType.DYNAMIC).friction(0.6f))
                        .build());
        plank = r.register(
                Registries.ENTITY_TYPE,
                EntityType.builder(key("plank"))
                        .size(0.8f, 0.25f)
                        .component(() -> new Body(BodyType.DYNAMIC).density(2f))
                        .build());
        ball = r.register(
                Registries.ENTITY_TYPE,
                EntityType.builder(key("ball"))
                        .size(0.8f, 0.8f)
                        .component(() -> new Body(BodyType.DYNAMIC)
                                .shape(Shape.circle(0.4f))
                                .restitution(0.6f))
                        .build());
        post = r.register(
                Registries.ENTITY_TYPE,
                EntityType.builder(key("post"))
                        .size(0.6f, 8f)
                        .component(() -> new Body(BodyType.STATIC))
                        .build());
        chassis = r.register(
                Registries.ENTITY_TYPE,
                EntityType.builder(key("chassis"))
                        .size(3f, 0.6f)
                        .component(() -> new Body(BodyType.DYNAMIC).density(2f))
                        .build());
        wheel = r.register(
                Registries.ENTITY_TYPE,
                EntityType.builder(key("wheel"))
                        .size(1f, 1f)
                        .component(() -> new Body(BodyType.DYNAMIC)
                                .shape(Shape.circle(0.5f))
                                .friction(1f))
                        .build());
        left = r.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("left")).bind(Keys.A, Keys.LEFT).build());
        right = r.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("right")).bind(Keys.D, Keys.RIGHT).build());
        drop = r.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("drop")).bind(Keys.SPACE).build());
        reset = r.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("reset")).bind(Keys.R).build());
        debug = r.register(
                Registries.INPUT_ACTION,
                InputAction.builder(key("debug")).bind(Keys.TAB).build());
    }

    @Override
    public void onEnable() {
        world = worlds().create("sandbox", WorldSettings.DEFAULT.gravity(new Vec2(0f, 20f)));
        world.camera().setPosition(new Vec2(20f, 11.25f));
        build();
        worlds().switchTo("sandbox");
        every(1, this::tick);
        on(RenderLayerEvent.class, e -> {
            if (e.layer().name().equals("entities")) {
                drawBodies(e.draw());
            } else if (e.layer().name().equals("overlay")) {
                hud(e.draw());
            }
        });
    }

    // ------------------------------------------------------------------ scene

    private void build() {
        axles.clear();
        drag = null;
        Physics physics = world.physics();
        world.spawn(ground, 20f, 22f, e -> e.setSize(48f, 2f));
        world.spawn(ground, -3.5f, 11f, e -> e.setSize(1f, 24f));
        world.spawn(ground, 43.5f, 11f, e -> e.setSize(1f, 24f));
        // A stack of ten crates.
        for (int i = 0; i < 10; i++) {
            world.spawn(crate, 2f, 20.5f - i * 1.01f);
        }
        // A pyramid.
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 6 - row; col++) {
                world.spawn(crate, 6f + row * 0.52f + col * 1.04f, 20.5f - row * 1.01f);
            }
        }
        // A bridge of planks between two posts.
        Entity westPost = world.spawn(post, 17f, 17f);
        Entity eastPost = world.spawn(post, 29f, 17f);
        Entity previous = westPost;
        float x = 17.3f;
        for (int i = 0; i < 14; i++) {
            Entity link = world.spawn(plank, x + 0.4f, 13f);
            physics.revolute(previous, link, new Vec2(x, 13f));
            previous = link;
            x += 0.8f;
        }
        physics.revolute(previous, eastPost, new Vec2(x, 13f));
        // A pendulum: a chain of planks ending in a heavy ball.
        Entity hook = world.spawn(ground, 33f, 1f, e -> e.setSize(0.4f, 0.4f));
        previous = hook;
        for (int i = 0; i < 6; i++) {
            Entity link = world.spawn(plank, 33.4f + i * 0.8f, 1f);
            physics.revolute(previous, link, new Vec2(33f + i * 0.8f, 1f));
            previous = link;
        }
        Entity weight = world.spawn(ball, 38.2f, 1f);
        weight.get(Body.class).density(6f);
        physics.revolute(previous, weight, new Vec2(37.8f, 1f));
        // A car on sprung wheels under the bridge.
        Entity car = world.spawn(chassis, 22f, 19.5f);
        Entity rear = world.spawn(wheel, 21f, 20.4f);
        Entity front = world.spawn(wheel, 23f, 20.4f);
        axles.add(physics.wheel(car, rear, rear.position(), Vec2.UP).setSpring(4f, 0.7f));
        axles.add(physics.wheel(car, front, front.position(), Vec2.UP).setSpring(4f, 0.7f));
    }

    private void tick() {
        if (input().justPressed(reset)) {
            for (Entity entity : List.copyOf(world.entities())) {
                entity.remove();
            }
            // Removal happens at the end of the tick; build the new scene in the next one.
            scheduler().later(1, this::build);
            return;
        }
        if (input().justPressed(debug)) {
            debugMode = (debugMode + 1) % 3;
            world.showDebug(DebugView.SHAPES, debugMode >= 1);
            world.showDebug(DebugView.JOINTS, debugMode >= 1);
            world.showDebug(DebugView.CONTACTS, debugMode == 2);
        }
        float throttle = input().axis(left, right);
        for (WheelJoint axle : axles) {
            if (!axle.isValid()) {
                continue;
            }
            if (throttle != 0f) {
                axle.enableMotor(throttle * 900f, 60f);
            } else {
                axle.disableMotor();
            }
        }
        Vec2 mouse = input().mouseWorld(world.camera());
        if (input().justPressed(drop)) {
            world.spawn(ball, mouse.x(), 0f);
        }
        MouseJoint current = drag;
        if (input().isDown(MouseButton.LEFT)) {
            if (current == null || !current.isValid()) {
                for (Entity hit : world.physics().overlapPoint(mouse, CollisionMask.ALL)) {
                    Body body = hit.get(Body.class);
                    if (body.type() == BodyType.DYNAMIC) {
                        drag = world.physics().mouse(hit, mouse).setMaxForce(400f * body.mass());
                        break;
                    }
                }
            } else {
                current.setTarget(mouse);
            }
        } else if (current != null) {
            if (current.isValid()) {
                current.remove();
            }
            drag = null;
        }
    }

    // ------------------------------------------------------------------ drawing

    private void drawBodies(Draw draw) {
        for (Entity entity : world.query().with(Body.class).list()) {
            Body body = entity.get(Body.class);
            Color color = colorOf(entity, body);
            Shape shape = body.shape();
            if (shape instanceof Shape.Circle circle) {
                draw.color(color).circle(entity.x(), entity.y(), circle.radius());
                Vec2 spoke = Vec2.fromAngle(entity.rotation()).scale(circle.radius());
                draw.color(Color.BLACK.withAlpha(0.5f))
                        .line(entity.x(), entity.y(), entity.x() + spoke.x(), entity.y() + spoke.y(), 0.06f);
            } else {
                float w = entity.size().x();
                float h = entity.size().y();
                draw.push()
                        .translate(entity.x(), entity.y())
                        .rotate(entity.rotation())
                        .color(color)
                        .rect(-w / 2f, -h / 2f, w, h)
                        .color(Color.BLACK.withAlpha(0.35f))
                        .rectOutline(new dev.gulp.api.math.Rect(-w / 2f, -h / 2f, w, h), 0.05f)
                        .pop();
            }
        }
        MouseJoint current = drag;
        if (current != null && current.isValid()) {
            Vec2 target = current.target();
            Vec2 grip = current.anchorB();
            draw.color(Color.WHITE).line(grip.x(), grip.y(), target.x(), target.y(), 0.05f);
        }
        draw.color(Color.WHITE);
    }

    private Color colorOf(Entity entity, Body body) {
        if (body.type() == BodyType.STATIC) {
            return GROUND;
        }
        if (!body.isAwake()) {
            return SLEEPING;
        }
        if (entity.type() == plank) {
            return PLANK;
        }
        if (entity.type() == ball) {
            return BALL;
        }
        if (entity.type() == chassis) {
            return CAR;
        }
        if (entity.type() == wheel) {
            return WHEEL;
        }
        return CRATE;
    }

    private void hud(Draw draw) {
        int bodies = 0;
        int awake = 0;
        for (Entity entity : world.query().with(Body.class).list()) {
            Body body = entity.get(Body.class);
            if (body.type() == BodyType.DYNAMIC) {
                bodies++;
                if (body.isAwake()) {
                    awake++;
                }
            }
        }
        draw.color(Color.rgba(0x00000099))
                .rect(6, 6, 470, 38)
                .color(Color.WHITE)
                .text(
                        "Ciała: " + bodies + "   obudzone: " + awake + "   złącza: "
                                + world.physics().joints().size() + "   FPS: "
                                + Math.round(display().fps()),
                        12,
                        10,
                        TextStyle.of(12))
                .text(
                        "Mysz: przeciąganie   A/D: auto   Spacja: piłka   R: od nowa   Tab: debug",
                        12,
                        26,
                        TextStyle.of(12));
    }
}
