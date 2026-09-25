package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.gulp.api.anim.Animation;
import dev.gulp.api.anim.Interpolator;
import dev.gulp.api.anim.Interpolators;
import dev.gulp.api.anim.Property;
import dev.gulp.api.anim.Props;
import dev.gulp.api.anim.Timeline;
import dev.gulp.api.anim.Tween;
import dev.gulp.api.anim.Tweens;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Material;
import dev.gulp.api.graphics.Materials;
import dev.gulp.api.math.Ease;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.render.Bloom;
import dev.gulp.api.render.Light;
import dev.gulp.api.world.World;
import dev.gulp.core.Fixtures.TestGame;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TweenTest extends JuiceFixture {

    static final class Box {
        float value;

        float value() {
            return value;
        }

        void setValue(float v) {
            value = v;
        }
    }

    static final Property<Box, Float> VALUE = Property.of(Box::value, Box::setValue, Interpolators.FLOAT);

    @Test
    void toFromByAndFromToReachTheirValues() {
        TestGame game = start();
        Box a = new Box();
        Box b = new Box();
        Box c = new Box();
        Box d = new Box();
        b.value = 5f;
        c.value = 2f;
        Tweens.to(a, VALUE, 10f, 0.5f).ease(Ease.LINEAR).start();
        Tweens.from(b, VALUE, 0f, 0.5f).start();
        Tweens.by(c, VALUE, 3f, 0.5f).start();
        Tween fromTo = Tweens.fromTo(d, VALUE, 1f, 2f, 0.5f).start();
        seconds(0.25f);
        assertThat(a.value).isBetween(3f, 7f);
        assertThat(fromTo.isRunning()).isTrue();
        assertThat(fromTo.progress()).isBetween(0.2f, 0.8f);
        seconds(0.5f);
        assertThat(a.value).isEqualTo(10f);
        assertThat(b.value).isEqualTo(5f);
        assertThat(c.value).isEqualTo(5f);
        assertThat(d.value).isEqualTo(2f);
        assertThat(fromTo.isRunning()).isFalse();
        assertThat(fromTo.progress()).isEqualTo(1f);
        assertThat(runner.isRunning()).isTrue();
    }

    @Test
    void delayRepeatYoyoAndCallbacks() {
        start();
        Box box = new Box();
        List<String> log = new ArrayList<>();
        Tween tween = Tweens.to(box, VALUE, 1f, 0.2f)
                .ease(Ease.LINEAR)
                .delay(0.2f)
                .repeat(1)
                .yoyo()
                .onUpdate(() -> log.add("update"))
                .onComplete(() -> log.add("done"))
                .start();
        assertThat(tween.duration()).isEqualTo(0.2f);
        assertThat(tween.totalDuration()).isCloseTo(0.6f, within(1e-5f));
        seconds(0.1f);
        assertThat(box.value).isZero();
        seconds(0.2f);
        assertThat(box.value).isGreaterThan(0.3f);
        seconds(0.5f);
        assertThat(box.value).isZero();
        assertThat(log).contains("update").endsWith("done");
    }

    @Test
    void sequencesParallelsAndThenChain() {
        start();
        Box a = new Box();
        Box b = new Box();
        List<String> order = new ArrayList<>();
        Tween chain = Tweens.to(a, VALUE, 1f, 0.1f)
                .then(Tweens.call(() -> order.add("call")))
                .then(Tweens.wait(0.1f))
                .then(Tweens.parallel(Tweens.to(a, VALUE, 2f, 0.1f), Tweens.to(b, VALUE, 3f, 0.3f)))
                .onComplete(() -> order.add("done"))
                .start();
        assertThat(chain.duration()).isCloseTo(0.5f, within(1e-4f));
        seconds(0.15f);
        assertThat(order).containsExactly("call");
        assertThat(a.value).isEqualTo(1f);
        seconds(0.6f);
        assertThat(a.value).isEqualTo(2f);
        assertThat(b.value).isEqualTo(3f);
        assertThat(order).containsExactly("call", "done");

        Box looped = new Box();
        List<String> ticks = new ArrayList<>();
        Tween forever = Tweens.sequence(Tweens.call(() -> ticks.add("x")), Tweens.to(looped, VALUE, 1f, 0.05f))
                .repeat(-1)
                .start();
        seconds(0.3f);
        assertThat(ticks.size()).isGreaterThan(3);
        assertThat(forever.progress()).isZero();
        forever.kill();
        assertThat(forever.isRunning()).isFalse();
    }

    @Test
    void pauseResumeKillAndKillAll() {
        start();
        Box box = new Box();
        Tween tween = Tweens.to(box, VALUE, 1f, 1f).ease(Ease.LINEAR).start();
        seconds(0.2f);
        tween.pause();
        assertThat(tween.isPaused()).isTrue();
        float held = box.value;
        seconds(0.2f);
        assertThat(box.value).isEqualTo(held);
        tween.resume();
        seconds(0.2f);
        assertThat(box.value).isGreaterThan(held);
        assertThat(Tweens.killAll(box)).isEqualTo(1);
        float stopped = box.value;
        seconds(0.2f);
        assertThat(box.value).isEqualTo(stopped);
        assertThat(Tweens.killAll(box)).isZero();

        // Restarting a running tween does not run it twice as fast.
        Box again = new Box();
        Tween twice = Tweens.to(again, VALUE, 1f, 0.5f).ease(Ease.LINEAR).start();
        twice.start();
        seconds(0.25f);
        assertThat(again.value).isBetween(0.3f, 0.7f);
    }

    @Test
    void gameTweensStopWhilePausedAndRealtimeOnesDoNot() {
        TestGame game = start();
        Box game1 = new Box();
        Box real = new Box();
        Tweens.to(game1, VALUE, 1f, 0.3f).start();
        Tweens.to(real, VALUE, 1f, 0.3f).realtime().start();
        game.engine().pause();
        seconds(0.5f);
        assertThat(game1.value).isZero();
        assertThat(real.value).isEqualTo(1f);
        game.engine().resume();
        seconds(0.5f);
        assertThat(game1.value).isEqualTo(1f);
    }

    @Test
    void tweensOfARemovedEntityOrDisabledOwnerStop() {
        TestGame game = start();
        World world = world(game);
        Entity entity = world.spawn(THING, 0f, 0f);
        Tween move = Tweens.to(entity, Props.X, 10f, 1f).start();
        seconds(0.1f);
        entity.remove();
        seconds(0.2f);
        assertThat(move.isRunning()).isFalse();

        Box box = new Box();
        Fixtures.ManualModule module = new Fixtures.ManualModule();
        Tween owned = Tweens.to(box, VALUE, 1f, 1f).owner(module).start();
        assertThat(owned.owner()).isSameAs(module);
        seconds(0.1f);
        assertThat(owned.isRunning()).isFalse();
    }

    @Test
    void failingTweenIsStoppedAndLogged() {
        start();
        Box box = new Box();
        Tween bad = Tweens.custom(box, 0.2f, t -> {
                    throw new IllegalStateException("boom");
                })
                .start();
        seconds(0.1f);
        assertThat(bad.isRunning()).isFalse();
    }

    @Test
    void entityCameraLightAndEffectProperties() {
        TestGame game = start();
        World world = world(game);
        Entity entity = sprite(game, world);
        Tweens.parallel(
                        Tweens.to(entity, Props.POSITION, new Vec2(2f, 3f), 0.1f),
                        Tweens.to(entity, Props.ROTATION, 350f, 0.1f),
                        Tweens.to(entity, Props.SCALE, new Vec2(2f, 2f), 0.1f),
                        Tweens.to(entity, Props.TINT, Color.RED, 0.1f),
                        Tweens.to(entity, Props.SPRITE_OFFSET, new Vec2(0f, 1f), 0.1f))
                .start();
        Tweens.to(world.camera(), Props.CAMERA_ZOOM, 2f, 0.1f).start();
        Tweens.to(world.camera(), Props.CAMERA_POSITION, new Vec2(5f, 5f), 0.1f).start();
        Tweens.to(world.camera(), Props.CAMERA_ROTATION, 10f, 0.1f).start();
        Light light = Light.point(Color.WHITE, 2f);
        Tweens.to(light, Props.LIGHT_RADIUS, 6f, 0.1f).start();
        Tweens.to(light, Props.LIGHT_INTENSITY, 0.5f, 0.1f).start();
        Tweens.to(light, Props.LIGHT_COLOR, Color.BLUE, 0.1f).start();
        Bloom bloom = new Bloom();
        Tweens.to(bloom, Bloom.INTENSITY, 3f, 0.1f).start();
        Tweens.to(game.audio().bus("sfx"), Props.BUS_VOLUME, 0.5f, 0.1f).start();
        seconds(0.3f);
        assertThat(entity.position()).isEqualTo(new Vec2(2f, 3f));
        assertThat((entity.rotation() % 360f + 360f) % 360f).isCloseTo(350f, within(0.01f));
        assertThat(entity.scale()).isEqualTo(new Vec2(2f, 2f));
        assertThat(entity.tint()).isEqualTo(Color.RED);
        assertThat(entity.get(SpriteComponent.class).offset()).isEqualTo(new Vec2(0f, 1f));
        assertThat(world.camera().zoom()).isEqualTo(2f);
        assertThat(light.radius()).isEqualTo(6f);
        assertThat(light.intensity()).isEqualTo(0.5f);
        assertThat(light.color()).isEqualTo(Color.BLUE);
        assertThat(bloom.intensity()).isEqualTo(3f);
        assertThat(game.audio().bus("sfx").volume()).isEqualTo(0.5f);
        assertThat(Props.Y.get(entity)).isEqualTo(3f);
        Props.Y.set(entity, 1f);
        assertThat(Props.ALPHA.get(entity)).isEqualTo(1f);
        Interpolator<Float> plain = (from, to, t) -> from;
        assertThatThrownBy(() -> plain.add(1f, 2f)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void readyMadeEffects() {
        TestGame game = start();
        World world = world(game);
        Entity entity = sprite(game, world);
        SpriteComponent sprite = entity.get(SpriteComponent.class);

        Tweens.flash(entity, Color.WHITE, 0.2f).start();
        seconds(0.05f);
        assertThat(sprite.material()).isSameAs(Materials.FLASH);
        assertThat(sprite.effect().a()).isBetween(0.3f, 1f);
        seconds(0.3f);
        assertThat(sprite.material()).isSameAs(Material.DEFAULT);
        assertThat(sprite.effect().a()).isZero();

        Tweens.punchScale(entity, 0.5f, 0.2f).start();
        seconds(0.1f);
        assertThat(entity.scale().x()).isGreaterThan(1.2f);
        seconds(0.2f);
        assertThat(entity.scale().x()).isEqualTo(1f);

        Tweens.fadeOut(entity, 0.1f).start();
        seconds(0.2f);
        assertThat(entity.tint().a()).isZero();
        Tweens.fadeIn(entity, 0.1f).start();
        seconds(0.2f);
        assertThat(entity.tint().a()).isEqualTo(1f);

        Tweens.shake(entity, 0.5f, 0.2f).start();
        seconds(0.1f);
        seconds(0.2f);
        assertThat(sprite.offset()).isEqualTo(Vec2.ZERO);

        Tween bob = Tweens.bob(entity, 0.5f, 0.4f).start();
        seconds(0.2f);
        assertThat(sprite.offset().y()).isLessThan(-0.3f);
        bob.kill();
        Tween pulse = Tweens.pulse(entity, 0.2f, 0.4f).start();
        seconds(0.2f);
        assertThat(entity.scale().x()).isGreaterThan(1.1f);
        pulse.kill();

        Tweens.shake(world.camera(), 0.5f, 0.2f).start();
        seconds(0.3f);
        assertThat(runner.isRunning()).isTrue();
    }

    @Test
    void timelinePlaysTracksEventsLoopsAndReverses() {
        start();
        Box a = new Box();
        Box b = new Box();
        List<String> events = new ArrayList<>();
        Timeline timeline = Timeline.builder()
                .track(a, VALUE, k -> k.key(0f, 0f).key(0.2f, 1f).key(0.4f, 3f, Ease.IN_QUAD))
                .track(b, VALUE, k -> k.key(0.1f, 5f))
                .at(0.3f, () -> events.add("late"))
                .at(0f, () -> events.add("start"))
                .build();
        assertThat(timeline.length()).isEqualTo(0.4f);
        timeline.play();
        seconds(0.1f);
        assertThat(a.value).isBetween(0.2f, 0.8f);
        assertThat(b.value).isEqualTo(5f);
        seconds(0.4f);
        assertThat(a.value).isEqualTo(3f);
        assertThat(events).containsExactly("start", "late");
        assertThat(timeline.isRunning()).isFalse();
        assertThat(timeline.progress()).isEqualTo(1f);

        timeline.seek(0.2f);
        assertThat(a.value).isEqualTo(1f);
        assertThat(timeline.time()).isEqualTo(0.2f);

        events.clear();
        timeline.reverse().speed(2f).play();
        assertThat(timeline.isReversed()).isTrue();
        assertThat(timeline.speed()).isEqualTo(2f);
        seconds(0.3f);
        assertThat(a.value).isZero();
        assertThat(events).containsExactly("late", "start");

        events.clear();
        Timeline looping = Timeline.builder()
                .length(0.1f)
                .loop(true)
                .at(0.05f, () -> events.add("tick"))
                .build()
                .realtime();
        assertThat(looping.isLooping()).isTrue();
        looping.play();
        seconds(0.35f);
        assertThat(events.size()).isBetween(3, 4);
        looping.stop();
        assertThat(looping.isRunning()).isFalse();
        looping.loop(false);

        Timeline backwardsLoop = Timeline.builder()
                .length(0.1f)
                .loop(true)
                .at(0.05f, () -> events.add("back"))
                .build()
                .reverse();
        backwardsLoop.play();
        seconds(0.25f);
        assertThat(events).contains("back");
        backwardsLoop.stop();

        Timeline empty = Timeline.builder().build();
        empty.play();
        step(2);
        assertThat(empty.isRunning()).isFalse();
        assertThatThrownBy(() ->
                        Timeline.builder().track(a, VALUE, k -> k.key(1f, 0f).key(0.5f, 1f)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aTweenStoppedWithItsEngineStartsInTheNextOne() {
        start();
        Box box = new Box();
        Tween tween = Tweens.to(box, VALUE, 1f, 1f).start();
        step(2);
        runner.stop();
        runner = null;
        start();
        box.value = 0f;
        tween.start();
        seconds(1.2f);
        assertThat(box.value).isEqualTo(1f);
    }

    @Test
    void animationsNeedARunningEngine() {
        Box box = new Box();
        Animation animation = Tweens.to(box, VALUE, 1f, 1f);
        assertThat(animation.isRunning()).isFalse();
        assertThat(animation.progress()).isZero();
    }
}
