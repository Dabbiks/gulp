package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gulp.api.anim.AnimationSet;
import dev.gulp.api.anim.Animator;
import dev.gulp.api.anim.Props;
import dev.gulp.api.anim.SpriteAnimation;
import dev.gulp.api.anim.Tween;
import dev.gulp.api.anim.Tweens;
import dev.gulp.api.command.Commands;
import dev.gulp.api.data.DataType;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.component.Follow;
import dev.gulp.api.entity.component.Health;
import dev.gulp.api.entity.component.Interactable;
import dev.gulp.api.entity.component.LightSource;
import dev.gulp.api.entity.component.Occluder;
import dev.gulp.api.entity.component.ParticleEmitter;
import dev.gulp.api.entity.component.WorldText;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleInfo;
import dev.gulp.api.nav.NavAgent;
import dev.gulp.api.nav.Path;
import dev.gulp.api.nav.PathFollower;
import dev.gulp.api.particle.EmitterConfig;
import dev.gulp.api.particle.ParticleEffect;
import dev.gulp.api.particle.ParticleInstance;
import dev.gulp.api.physics.Body;
import dev.gulp.api.physics.BodyType;
import dev.gulp.api.physics.Collider;
import dev.gulp.api.physics.CollisionMask;
import dev.gulp.api.physics.Mover;
import dev.gulp.api.physics.RayHit;
import dev.gulp.api.physics.Trigger;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.render.Light;
import dev.gulp.api.ui.Transitions;
import dev.gulp.api.world.TileShape;
import dev.gulp.api.world.TileType;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;
import dev.gulp.backend.headless.HeadlessLog;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Stages 6 to 8 together: entities with every kind of component, worlds switching and unloading, pause, modules. */
class CrossWorldTest extends JuiceFixture {

    static final ParticleEffect SMOKE = ParticleEffect.of(
            Key.of("test", "smoke"),
            List.of(EmitterConfig.builder()
                    .rate(60f)
                    .loop(true)
                    .duration(1f)
                    .lifetime(0.5f, 0.5f)
                    .build()));

    /** A module that owns a tween and a navigation block. */
    @ModuleInfo(id = "fx")
    public static final class FxModule extends GameModule {
        Tween glow;

        @Override
        public void onEnable() {}
    }

    private void assertNoErrors() {
        assertThat(runner.backend().log().messages(HeadlessLog.ERROR)).isEmpty();
        assertThat(runner.backend().log().messages(HeadlessLog.WARN)).isEmpty();
    }

    private AnimationSet animations(TestGame game) {
        TextureRegion frame = region(game);
        return AnimationSet.of(SpriteAnimation.builder("idle")
                .frames(List.of(frame, frame), 8f)
                .build());
    }

    @Test
    void anEntityWithEveryComponentCleansUpEverythingWhenRemoved() {
        TestGame game = start();
        World world = world(game);
        world.lighting().ambient(Color.GRAY);
        Entity hero = sprite(game, world);
        hero.add(new Animator(animations(game))).play("idle");
        hero.add(new Collider());
        hero.add(new Mover().topDown(true));
        hero.add(new Health(5f));
        hero.add(new Interactable());
        hero.add(new WorldText("hero"));
        hero.add(new NavAgent());
        LightSource light = hero.add(new LightSource(Light.point(Color.WHITE, 4f)));
        hero.add(new Occluder());
        hero.add(new ParticleEmitter(SMOKE));
        Entity child = sprite(game, world);
        hero.attach(child, new Vec2(0f, -1f));
        Entity crate = world.spawn(THING, 3f, 0f);
        crate.add(new Body(BodyType.DYNAMIC));
        Entity zone = world.spawn(THING, -3f, 0f);
        zone.add(new Trigger());
        zone.add(new PathFollower(Path.of(new Vec2(-3f, 0f), new Vec2(-3f, 3f))));
        Entity follower = world.spawn(THING, 6f, 6f);
        follower.add(new Follow(hero));
        Tween pulse = Tweens.pulse(hero, 0.2f, 0.5f).repeat(-1).start();
        ParticleInstance trail = world.spawnParticles(SMOKE, hero.position()).follow(hero);
        seconds(0.5f);
        assertThat(world.particles().count()).isPositive();
        assertThat(world.lighting().lights()).contains(light.light());
        assertThat(world.physics().overlapPoint(hero.position(), CollisionMask.ALL))
                .isNotEmpty();

        hero.remove();
        step(2);
        assertThat(hero.isRemoved()).isTrue();
        assertThat(child.isRemoved()).isTrue();
        assertThat(pulse.isRunning()).isFalse();
        assertThat(world.lighting().lights()).doesNotContain(light.light());
        assertThat(world.physics().overlapPoint(Vec2.ZERO, CollisionMask.ALL)).isEmpty();
        seconds(1f);
        assertThat(trail.isAlive()).isFalse();
        assertThat(world.particles().instanceCount()).isZero();
        assertThat(follower.isRemoved()).isFalse();
        assertNoErrors();
    }

    @Test
    void switchingAndUnloadingWorldsStopsTheirEffects() {
        TestGame game = start();
        World first = world(game);
        first.lighting().ambient(Color.GRAY);
        Entity torch = sprite(game, first);
        torch.add(new LightSource(Light.point(Color.RED, 3f)));
        torch.add(new ParticleEmitter(SMOKE));
        Tween spin = Tweens.to(torch, Props.ROTATION, 360f, 1f).repeat(-1).start();
        Tween zoom = Tweens.to(first.camera(), Props.CAMERA_ZOOM, 2f, 5f).start();
        World second = game.worlds().create("second", WorldSettings.DEFAULT);
        sprite(game, second).add(new ParticleEmitter(SMOKE));
        seconds(0.2f);

        game.worlds().switchTo("second", Transitions.fade(0.2f));
        seconds(0.6f);
        assertThat(game.worlds().active()).isSameAs(second);
        int frozen = first.particles().count();
        seconds(0.2f);
        assertThat(first.particles().count()).isEqualTo(frozen);
        assertThat(second.particles().count()).isPositive();

        game.worlds().unload("main");
        step(2);
        assertThat(spin.isRunning()).isFalse();
        assertThat(first.particles().count()).isZero();
        seconds(0.5f);
        assertThat(zoom.isRunning()).isTrue();
        game.worlds().create("main", WorldSettings.DEFAULT);
        game.worlds().switchTo("main", Transitions.circleWipe(0.1f));
        seconds(0.5f);
        assertThat(game.worlds().active().name()).isEqualTo("main");
        assertNoErrors();
    }

    @Test
    void teleportingToAnotherWorldMovesPhysicsLightsAndParticles() {
        TestGame game = start();
        World first = world(game);
        World second = game.worlds().create("second", WorldSettings.DEFAULT);
        Entity hero = sprite(game, first);
        hero.add(new Collider());
        hero.add(new Mover().topDown(true));
        LightSource light = hero.add(new LightSource(Light.point(Color.WHITE, 4f)));
        hero.add(new ParticleEmitter(SMOKE));
        Entity crate = sprite(game, first);
        crate.setPosition(3f, 0f);
        Body body = crate.add(new Body(BodyType.DYNAMIC));
        body.setVelocity(new Vec2(1f, 0f));
        seconds(0.3f);

        hero.teleport(second.location(1f, 1f));
        crate.teleport(second.location(5f, 0f));
        step(1);
        assertThat(hero.world()).isSameAs(second);
        assertThat(first.physics().overlapPoint(hero.position(), CollisionMask.ALL))
                .isEmpty();
        assertThat(first.lighting().lights()).doesNotContain(light.light());
        assertThat(second.lighting().lights()).contains(light.light());
        assertThat(body.velocity().x()).isPositive();
        game.worlds().switchTo("second");
        seconds(0.3f);
        assertThat(second.physics().overlapPoint(new Vec2(1f, 1f), CollisionMask.ALL))
                .containsExactly(hero);
        assertThat(second.particles().count()).isPositive();
        hero.get(Mover.class).moveAndSlide(new Vec2(1f, 0f));
        step(2);
        game.worlds().switchTo("main");
        seconds(1f);
        assertThat(first.particles().instanceCount()).isZero();
        assertNoErrors();
    }

    @Test
    void worldShortcutsTileDataAndWorldCommands() {
        TestGame game = new TestGame();
        game.onLoad = () -> game.registries().register(Registries.ENTITY_TYPE, THING);
        runner = HeadlessRunner.start(game, b -> b.files().useClasspathAssets(true));
        for (int i = 0; i < 20 && !runner.engine().isRunning(); i++) {
            runner.step(1);
        }
        World world = world(game);
        Key hardness = Key.of("test", "hardness");
        TileType rock = TileType.builder(Key.of("test", "rock"))
                .shape(TileShape.FULL)
                .data(hardness, DataType.INT, 3)
                .build();
        assertThat(rock.data().getOrDefault(hardness, DataType.INT, 0)).isEqualTo(3);
        world.tileMap().fill("ground", 2, -1, 1, 3, rock);
        step(1);
        RayHit hit = world.raycast(new Vec2(0f, 0.5f), new Vec2(5f, 0.5f));
        assertThat(hit).isNotNull();
        assertThat(hit.point().x()).isEqualTo(2f, org.assertj.core.data.Offset.offset(0.01f));
        assertThat(world.raycast(new Vec2(0f, 0.5f), new Vec2(1f, 0.5f), CollisionMask.ALL))
                .isNull();

        List<String> output = new java.util.ArrayList<>();
        Commands commands = game.engine().commands();
        commands.dispatch("/spawn test:nothing", output::add);
        commands.dispatch("/spawn thing 4 5", output::add);
        commands.dispatch("/tp 7 8", output::add);
        Entity player = world.spawn(THING, 0f, 0f);
        player.tags().add("player");
        commands.dispatch("/tp -3 1", output::add);
        commands.dispatch("/reload assets", output::add);
        step(2);
        assertThat(world.query().near(new Vec2(4f, 5f), 0.1f).count()).isOne();
        assertThat(world.camera().position()).isEqualTo(new Vec2(7f, 8f));
        assertThat(player.position()).isEqualTo(new Vec2(-3f, 1f));
        assertThat(output)
                .contains("Invalid <type>: No entity type 'test:nothing'", "Reloading loaded assets", "Assets reloaded")
                .anyMatch(line -> line.startsWith("Spawned test:thing"));
        assertThat(commands.complete("/spawn test:t")).contains("/spawn test:thing");
    }

    @Test
    void pauseFreezesGameTimeEffectsButNotRealtimeTweens() {
        TestGame game = start();
        World world = world(game);
        Entity entity = sprite(game, world);
        entity.add(new ParticleEmitter(SMOKE));
        Animator animator = entity.add(new Animator(animations(game)));
        animator.play("idle");
        Entity crate = world.spawn(THING, 0f, -5f);
        crate.add(new Body(BodyType.DYNAMIC));
        Tween game1 = Tweens.to(entity, Props.X, 10f, 1f).start();
        Tween menu = Tweens.to(entity, Props.ALPHA, 0f, 1f).realtime().start();
        seconds(0.25f);
        game.engine().pause();
        step(1);
        float x = entity.x();
        float crateY = crate.y();
        int particles = world.particles().count();
        seconds(0.5f);
        assertThat(entity.x()).isEqualTo(x);
        assertThat(crate.y()).isEqualTo(crateY);
        assertThat(world.particles().count()).isEqualTo(particles);
        assertThat(menu.progress()).isGreaterThan(0.6f);
        game.engine().resume();
        seconds(0.25f);
        assertThat(entity.x()).isGreaterThan(x);
        assertThat(crate.y()).isGreaterThan(crateY);
        assertThat(game1.isRunning()).isTrue();

        game.engine().setTimeScale(0.5f);
        float before = game1.progress();
        seconds(0.2f);
        assertThat(game1.progress() - before).isBetween(0.07f, 0.13f);
        assertNoErrors();
    }

    @Test
    void disablingAModuleKillsItsTweensAndReleasesItsTickets() {
        FxModule module = new FxModule();
        TestGame game = new TestGame().modules(module);
        runner = HeadlessRunner.start(game, b -> b.files().useClasspathAssets(true));
        for (int i = 0; i < 20 && !runner.engine().isRunning(); i++) {
            runner.step(1);
        }
        World world = world(game);
        Entity entity = sprite(game, world);
        module.glow = Tweens.to(entity, Props.ALPHA, 0f, 5f).owner(module).start();
        world.navGrid().block(new Rect(0f, 0f, 2f, 2f), module);
        world.keepLoaded(new Rect(100f, 100f, 10f, 10f), module);
        step(2);
        assertThat(world.navGrid().isPassable(1, 1)).isFalse();

        game.modules().disable("fx");
        step(2);
        assertThat(module.glow.isRunning()).isFalse();
        assertThat(world.navGrid().isPassable(1, 1)).isTrue();
        assertNoErrors();
    }
}
