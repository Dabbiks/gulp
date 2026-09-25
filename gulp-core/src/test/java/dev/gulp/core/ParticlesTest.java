package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.component.ParticleEmitter;
import dev.gulp.api.graphics.BlendMode;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Gradient;
import dev.gulp.api.math.FloatCurve;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.particle.EmitterConfig;
import dev.gulp.api.particle.EmitterShape;
import dev.gulp.api.particle.ParticleCollision;
import dev.gulp.api.particle.ParticleEffect;
import dev.gulp.api.particle.ParticleInstance;
import dev.gulp.api.particle.Particles;
import dev.gulp.api.registry.Key;
import dev.gulp.api.world.TileMap;
import dev.gulp.api.world.TileSet;
import dev.gulp.api.world.TileShape;
import dev.gulp.api.world.TileType;
import dev.gulp.api.world.World;
import dev.gulp.core.Fixtures.TestGame;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParticlesTest extends JuiceFixture {

    static final TileType WALL = TileType.builder(Key.of("test", "wall"))
            .tileSet(TileSet.of(AssetKey.texture("test:textures/sheet"), 16, 16), 0)
            .shape(TileShape.FULL)
            .build();

    static ParticleEffect effect(EmitterConfig... emitters) {
        return ParticleEffect.of(Key.of("test", "fx"), List.of(emitters));
    }

    @Test
    void burstsRatesLifetimesAndLimits() {
        TestGame game = start();
        World world = world(game);
        Particles particles = world.particles();
        ParticleEffect burst = effect(EmitterConfig.builder()
                .burst(30, 0f)
                .burst(10, 0.1f)
                .duration(0.2f)
                .lifetime(0.3f, 0.3f)
                .shape(EmitterShape.circle(1f))
                .build());
        assertThat(burst.isFinite()).isTrue();
        ParticleInstance instance = world.spawnParticles(burst, new Vec2(3f, 3f));
        assertThat(instance.effect()).isSameAs(burst);
        assertThat(instance.position()).isEqualTo(new Vec2(3f, 3f));
        step(2);
        assertThat(particles.count()).isEqualTo(30);
        assertThat(instance.particleCount()).isEqualTo(30);
        seconds(0.1f);
        assertThat(particles.count()).isEqualTo(40);
        seconds(0.4f);
        assertThat(particles.count()).isZero();
        assertThat(instance.isAlive()).isFalse();
        assertThat(particles.instanceCount()).isZero();

        ParticleEffect stream = effect(EmitterConfig.builder()
                .rate(100f)
                .loop(true)
                .duration(0.5f)
                .lifetime(1f, 1f)
                .maxParticles(40)
                .build());
        assertThat(stream.isFinite()).isFalse();
        ParticleInstance running = world.spawnParticles(stream, world.location(0f, 0f));
        seconds(0.3f);
        assertThat(running.particleCount()).isBetween(25, 35);
        seconds(0.5f);
        assertThat(running.particleCount()).isEqualTo(40);
        running.stop();
        assertThat(running.isEmitting()).isFalse();
        seconds(1.2f);
        assertThat(running.isAlive()).isFalse();

        particles.setLimit(5);
        assertThat(particles.limit()).isEqualTo(5);
        world.spawnParticles(burst, Vec2.ZERO);
        step(2);
        assertThat(particles.count()).isEqualTo(5);
        particles.clear();
        assertThat(particles.count()).isZero();
        particles.setLimit(Particles.DEFAULT_LIMIT);

        ParticleInstance killed = world.spawnParticles(burst, Vec2.ZERO);
        step(2);
        killed.kill();
        assertThat(killed.isAlive()).isFalse();
        assertThat(particles.count()).isZero();
    }

    @Test
    void movementCollisionSubEmittersAndFollowing() {
        TestGame game =
                start(b -> b.files().putAsset("test/textures/sheet.png", "png".getBytes(StandardCharsets.UTF_8)));
        World world = world(game);
        TileMap map = world.tileMap();
        map.fill("ground", -5, 5, 10, 1, WALL);
        ParticleEffect pop =
                effect(EmitterConfig.builder().burst(3, 0f).lifetime(0.1f, 0.1f).build());
        ParticleEffect falling = effect(EmitterConfig.builder()
                .burst(10, 0f)
                .direction(90f)
                .spread(0f)
                .speed(5f, 5f)
                .gravity(0f, 10f)
                .drag(0.1f)
                .spin(90f, 90f)
                .rotation(0f, 10f)
                .lifetime(2f, 2f)
                .collision(ParticleCollision.DIE, 0f)
                .onDeath(pop)
                .build());
        world.spawnParticles(falling, new Vec2(0f, 0f));
        seconds(1.5f);
        assertThat(world.particles().count())
                .as("they died on the floor and popped")
                .isLessThan(10);
        assertThat(world.particles().instanceCount()).isGreaterThan(1);

        world.particles().clear();
        ParticleEffect bouncy = effect(EmitterConfig.builder()
                .burst(5, 0f)
                .direction(90f)
                .spread(10f)
                .speed(6f, 6f)
                .lifetime(1f, 1f)
                .collision(ParticleCollision.BOUNCE, 0.5f)
                .build());
        world.spawnParticles(bouncy, new Vec2(0f, 3f));
        seconds(0.5f);
        assertThat(world.particles().count())
                .as("bouncing particles stay alive")
                .isEqualTo(5);

        world.particles().clear();
        Entity carrier = world.spawn(THING, 0f, 0f);
        ParticleEffect trail = effect(EmitterConfig.builder()
                .rate(50f)
                .loop(true)
                .local(true)
                .lifetime(5f, 5f)
                .speed(0f, 0f)
                .shape(EmitterShape.rect(1f, 1f))
                .build());
        ParticleInstance following = world.spawnParticles(trail, Vec2.ZERO).follow(carrier);
        following.setPosition(Vec2.ZERO);
        seconds(0.2f);
        carrier.setPosition(10f, 0f);
        seconds(0.2f);
        assertThat(following.position().x()).isEqualTo(10f);
        carrier.remove();
        step(3);
        assertThat(following.isEmitting()).isFalse();
        following.follow(null);
    }

    @Test
    void emitterComponentAndJsonEffectsWithHotReload() {
        String json = """
                {"emitters": [{
                  "rate": 20, "bursts": [{"count": 4}], "duration": 1, "loop": true,
                  "shape": {"type": "ring", "radius": 1, "thickness": 0.2},
                  "direction": -90, "spread": 45, "speed": [1, 2], "gravity": [0, 3], "drag": 0.5,
                  "spin": 90, "rotation": [0, 360], "lifetime": [0.5, 1],
                  "size": [[0, 0.2], [1, 0]], "color": ["#ffffff", "#ff8800"], "alpha": 1,
                  "blend": "add", "local": false, "collision": "bounce", "bounce": 0.3, "max": 50,
                  "layer": "effects", "region": "sprites/spark",
                  "onDeath": {"emitters": [{"bursts": [{"count": 1, "at": 0}], "shape": {"type": "point"},
                                            "color": [[0, "#ff0000"], [1, "#0000ff"]], "size": 0.1,
                                            "frames": ["sprites/spark"], "onDeath": "particles/smoke"}]}
                }]}
                """;
        String smoke = """
                {"emitters": [{"bursts": [{"count": 2}], "shape": {"type": "circle", "radius": 0.5},
                               "color": "#888888"},
                              {"shape": {"type": "rect", "width": 1, "height": 1}},
                              {"shape": {"type": "line", "length": 2}}]}
                """;
        TestGame game = start(b -> {
            b.files().putAsset("test/particles/sparks.json", json.getBytes(StandardCharsets.UTF_8));
            b.files().putAsset("test/particles/smoke.json", smoke.getBytes(StandardCharsets.UTF_8));
            b.files()
                    .putAsset(
                            "test/particles/bad.json",
                            "{\"emitters\": [{\"shape\": {\"type\": \"star\"}}]}".getBytes(StandardCharsets.UTF_8));
        });
        AssetKey<ParticleEffect> sparks = AssetKey.particles("test:particles/sparks");
        game.assets().load(sparks);
        game.assets().load(AssetKey.particles("test:particles/bad"));
        step(5);
        ParticleEffect loaded = game.assets().get(sparks);
        EmitterConfig first = loaded.emitters().get(0);
        assertThat(first.rate()).isEqualTo(20f);
        assertThat(first.bursts()).hasSize(1);
        assertThat(first.shape()).isEqualTo(EmitterShape.ring(1f, 0.2f));
        assertThat(first.speedMin()).isEqualTo(1f);
        assertThat(first.speedMax()).isEqualTo(2f);
        assertThat(first.spinMin()).isEqualTo(90f);
        assertThat(first.blend()).isEqualTo(BlendMode.ADD);
        assertThat(first.collision()).isEqualTo(ParticleCollision.BOUNCE);
        assertThat(first.bounce()).isEqualTo(0.3f);
        assertThat(first.maxParticles()).isEqualTo(50);
        assertThat(first.frames()).containsExactly(AssetKey.region("test:sprites/spark"));
        assertThat(first.color().at(1f)).isEqualTo(Color.hex("#ff8800"));
        assertThat(first.size().at(1f)).isZero();
        ParticleEffect death = first.onDeath();
        assertThat(death).isNotNull();
        assertThat(death.emitters().get(0).onDeath().emitters()).hasSize(3);
        assertThat(game.assets().isLoaded(AssetKey.particles("test:particles/bad")))
                .isFalse();

        World world = world(game);
        Entity torch = world.spawn(THING, 2f, 2f);
        ParticleEmitter emitter = torch.add(new ParticleEmitter(sparks).offset(0f, -1f));
        step(3);
        assertThat(emitter.offset()).isEqualTo(new Vec2(0f, -1f));
        assertThat(emitter.instance()).isNotNull();
        seconds(0.2f);
        assertThat(world.particles().count()).isPositive();
        emitter.play();
        emitter.stop();
        assertThat(emitter.instance()).isNull();

        Entity manual = world.spawn(THING, 0f, 0f);
        ParticleEmitter code = manual.add(
                new ParticleEmitter(effect(EmitterConfig.builder().burst(2, 0f).build())).autoPlay(false));
        step(2);
        assertThat(code.instance()).isNull();
        code.play();
        manual.remove();
        step(2);

        runner.backend().files().putAsset("test/particles/sparks.json", """
                {"emitters": [{"rate": 99}]}""".getBytes(StandardCharsets.UTF_8));
        runner.backend().files().simulateAssetChange("test/particles/sparks.json");
        step(8);
        assertThat(game.assets().get(sparks).emitters().get(0).rate()).isEqualTo(99f);
        ParticleInstance fresh = world.spawnParticles(sparks, Vec2.ZERO);
        assertThat(fresh.effect().emitters().get(0).rate()).isEqualTo(99f);
    }

    @Test
    void valuesCurvesAndGradients() {
        FloatCurve curve = FloatCurve.of(0f, 0f, 0.5f, 1f, 1f, 0f);
        assertThat(curve.at(0.25f)).isEqualTo(0.5f);
        assertThat(curve.at(-1f)).isZero();
        assertThat(curve.at(2f)).isZero();
        assertThat(curve.size()).isEqualTo(3);
        assertThat(curve.time(1)).isEqualTo(0.5f);
        assertThat(curve.value(1)).isEqualTo(1f);
        assertThat(FloatCurve.of(0f, 1f, 0f, 2f).at(0.5f)).isEqualTo(2f);
        assertThat(FloatCurve.linear(0f, 2f)).isEqualTo(FloatCurve.linear(0f, 2f));
        assertThat(FloatCurve.linear(0f, 2f).hashCode())
                .isEqualTo(FloatCurve.linear(0f, 2f).hashCode());
        assertThat(FloatCurve.ONE.toString()).contains("1.0");
        assertThatThrownBy(() -> FloatCurve.of(1f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FloatCurve.of(1f, 0f, 0f, 0f)).isInstanceOf(IllegalArgumentException.class);

        Gradient gradient = Gradient.of(Color.BLACK, Color.WHITE).with(0.5f, Color.RED);
        assertThat(gradient.size()).isEqualTo(3);
        assertThat(gradient.at(0.5f)).isEqualTo(Color.RED);
        assertThat(gradient.at(-1f)).isEqualTo(Color.BLACK);
        assertThat(gradient.at(2f)).isEqualTo(Color.WHITE);
        assertThat(gradient.time(1)).isEqualTo(0.5f);
        assertThat(gradient.color(2)).isEqualTo(Color.WHITE);
        assertThat(Gradient.of(Color.RED)).isEqualTo(Gradient.constant(Color.RED));
        assertThat(Gradient.of(Color.RED).hashCode())
                .isEqualTo(Gradient.constant(Color.RED).hashCode());
        assertThat(Gradient.WHITE.toString()).contains("Gradient");
        assertThatThrownBy(Gradient::of).isInstanceOf(IllegalArgumentException.class);

        assertThat(EmitterShape.point()).isEqualTo(new EmitterShape.Point());
        assertThat(EmitterShape.line(2f).length()).isEqualTo(2f);
        EmitterConfig config = EmitterConfig.builder()
                .rate(-1f)
                .duration(-1f)
                .lifetime(2f, 1f)
                .alpha(FloatCurve.ONE)
                .maxParticles(-3)
                .region(AssetKey.region("test:sprites/a"))
                .build();
        assertThat(config.rate()).isZero();
        assertThat(config.duration()).isZero();
        assertThat(config.lifetimeMax()).isEqualTo(2f);
        assertThat(config.maxParticles()).isZero();
        assertThat(config.alpha()).isEqualTo(FloatCurve.ONE);
        assertThat(config.isLocal()).isFalse();
        assertThat(config.gravity()).isEqualTo(Vec2.ZERO);
        assertThat(config.rotationMax()).isZero();
        assertThat(config.drag()).isZero();
        assertThat(config.direction()).isEqualTo(-90f);
        assertThat(config.spread()).isEqualTo(360f);
        assertThat(config.spinMax()).isZero();
        assertThat(config.rotationMin()).isZero();
        assertThat(config.lifetimeMin()).isEqualTo(2f);
        assertThat(config.layer()).isEqualTo("effects");
        assertThat(config.loop()).isFalse();
        ParticleEffect built =
                ParticleEffect.builder(Key.of("test", "b")).emitter(config).build();
        assertThat(built.key()).isEqualTo(Key.of("test", "b"));
        assertThat(built.toString()).contains("1 emitters");
    }
}
