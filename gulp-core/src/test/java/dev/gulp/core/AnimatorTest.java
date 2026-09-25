package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.anim.AnimationEndEvent;
import dev.gulp.api.anim.AnimationFrameEvent;
import dev.gulp.api.anim.AnimationSet;
import dev.gulp.api.anim.Animator;
import dev.gulp.api.anim.PlayMode;
import dev.gulp.api.anim.SpriteAnimation;
import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureAtlas;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.world.World;
import dev.gulp.core.Fixtures.TestGame;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class AnimatorTest extends JuiceFixture {

    private List<TextureRegion> frames(TestGame game, int count) {
        Texture sheet = game.graphics().texture(new Pixmap(16 * count, 16));
        List<TextureRegion> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(new TextureRegion(sheet, i * 16, 0, 16, 16));
        }
        return result;
    }

    private Animator animated(TestGame game, World world, AnimationSet set) {
        Entity entity = sprite(game, world);
        return entity.add(new Animator(set));
    }

    @Test
    void loopsPingPongsReversesAndPlaysOnce() {
        TestGame game = start();
        World world = world(game);
        List<TextureRegion> f = frames(game, 4);
        List<Integer> steps = new ArrayList<>();
        AnimationSet set = AnimationSet.of(
                SpriteAnimation.builder("run")
                        .frames(f, 10f)
                        .onFrame(2, e -> steps.add(2))
                        .build(),
                SpriteAnimation.builder("swing")
                        .frames(f, 10f)
                        .mode(PlayMode.PING_PONG)
                        .build(),
                SpriteAnimation.builder("fall")
                        .frames(f, 10f)
                        .mode(PlayMode.REVERSED)
                        .build(),
                SpriteAnimation.builder("flicker")
                        .frames(f, 20f)
                        .mode(PlayMode.LOOP_RANDOM)
                        .build(),
                SpriteAnimation.builder("hit")
                        .frame(f.get(0), 0.1f)
                        .frame(f.get(1), 0.1f)
                        .mode(PlayMode.ONCE)
                        .build());
        assertThat(set.names()).containsExactly("run", "swing", "fall", "flicker", "hit");
        Animator animator = animated(game, world, set);
        assertThat(animator.current()).isNull();
        assertThat(animator.currentName()).isEmpty();
        animator.play("run");
        seconds(0.25f);
        assertThat(animator.frame()).isEqualTo(2);
        assertThat(steps).containsExactly(2);
        assertThat(animator.entity().get(SpriteComponent.class).region()).isSameAs(f.get(2));
        seconds(0.2f);
        assertThat(animator.frame()).isZero();
        animator.play("run");
        assertThat(animator.frame()).isZero();

        animator.play("swing");
        seconds(0.45f);
        assertThat(animator.frame()).isBetween(1, 2);

        animator.play("fall");
        assertThat(animator.frame()).isEqualTo(3);
        seconds(0.5f);
        assertThat(animator.isFinished()).isTrue();
        assertThat(animator.frame()).isZero();

        animator.speed(2f).play("flicker");
        assertThat(animator.speed()).isEqualTo(2f);
        seconds(0.3f);
        assertThat(animator.frame()).isBetween(0, 3);

        animator.speed(1f).playOnce("run").then("hit");
        seconds(0.45f);
        assertThat(animator.currentName()).isEqualTo("hit");
        seconds(0.3f);
        assertThat(animator.isFinished()).isTrue();
        assertThatThrownBy(() -> animator.play("missing")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void autoRulesPickTheAnimationAndFireEvents() {
        TestGame game = start();
        World world = world(game);
        List<TextureRegion> f = frames(game, 2);
        AnimationSet set = AnimationSet.of(
                SpriteAnimation.builder("idle").frames(f, 10f).build(),
                SpriteAnimation.builder("jump").frames(f, 10f).build(),
                SpriteAnimation.builder("land")
                        .frames(f, 20f)
                        .mode(PlayMode.ONCE)
                        .build());
        Animator animator = animated(game, world, set);
        AtomicBoolean air = new AtomicBoolean();
        animator.auto().when(air::get, "jump").otherwise("idle");
        List<String> ended = new ArrayList<>();
        List<Integer> shown = new ArrayList<>();
        game.on(AnimationEndEvent.class, e -> ended.add(e.animation()));
        game.on(AnimationFrameEvent.class, e -> shown.add(e.frame()));
        step(2);
        assertThat(animator.currentName()).isEqualTo("idle");
        air.set(true);
        step(2);
        assertThat(animator.currentName()).isEqualTo("jump");
        animator.playOnce("land");
        step(2);
        assertThat(animator.currentName()).as("single plays pause the rules").isEqualTo("land");
        seconds(0.2f);
        assertThat(ended).contains("land");
        assertThat(animator.currentName()).isEqualTo("jump");
        assertThat(shown).isNotEmpty();
        animator.stopAuto();
        air.set(false);
        step(2);
        assertThat(animator.currentName()).isEqualTo("jump");
    }

    @Test
    void setsFromAtlasAndAsepriteFiles() {
        String aseprite = """
                {"frames": {
                  "hero 0.png": {"frame": {"x": 0, "y": 0, "w": 16, "h": 16}, "duration": 100},
                  "hero 1.png": {"frame": {"x": 16, "y": 0, "w": 16, "h": 16}, "duration": 200},
                  "hero 2.png": {"frame": {"x": 32, "y": 0, "w": 16, "h": 16}, "duration": 100},
                  "hero 3.png": {"frame": {"x": 48, "y": 0, "w": 16, "h": 16}, "duration": 100}
                 },
                 "meta": {"image": "hero.png", "size": {"w": 64, "h": 16}, "frameTags": [
                   {"name": "idle", "from": 0, "to": 1, "direction": "forward"},
                   {"name": "back", "from": 1, "to": 3, "direction": "reverse"},
                   {"name": "bounce", "from": 0, "to": 3, "direction": "pingpong"},
                   {"name": "once", "from": 2, "to": 3, "direction": "forward", "repeat": "1"}
                 ]}}
                """;
        String untagged = """
                {"frames": [
                  {"filename": "a", "frame": {"x": 0, "y": 0, "w": 8, "h": 8}},
                  {"filename": "b", "frame": {"x": 8, "y": 0, "w": 8, "h": 8}}
                 ], "meta": {"image": "art/hero.png"}}
                """;
        TestGame game = start(b -> {
            b.files().putAsset("test/animations/hero.json", aseprite.getBytes(StandardCharsets.UTF_8));
            b.files().putAsset("test/animations/plain.json", untagged.getBytes(StandardCharsets.UTF_8));
            b.files().putAsset("test/animations/hero.png", "png".getBytes(StandardCharsets.UTF_8));
        });
        AssetKey<AnimationSet> hero = AssetKey.animations("test:animations/hero");
        AssetKey<AnimationSet> plain = AssetKey.animations("test:animations/plain");
        game.assets().load(hero);
        game.assets().load(plain);
        step(5);
        AnimationSet set = game.assets().get(hero);
        assertThat(set.names()).containsExactly("idle", "back", "bounce", "once");
        SpriteAnimation idle = set.getOrThrow("idle");
        assertThat(idle.frameCount()).isEqualTo(2);
        assertThat(idle.duration(1)).isEqualTo(0.2f);
        assertThat(idle.totalDuration()).isEqualTo(0.3f);
        assertThat(idle.mode()).isEqualTo(PlayMode.LOOP);
        assertThat(set.getOrThrow("back").frame(0).x()).isEqualTo(48);
        assertThat(set.getOrThrow("bounce").mode()).isEqualTo(PlayMode.PING_PONG);
        assertThat(set.getOrThrow("once").mode()).isEqualTo(PlayMode.ONCE);
        assertThat(set.get("nope")).isNull();
        assertThat(set.has("idle")).isTrue();
        assertThat(game.assets().get(plain).names()).containsExactly("default");

        World world = world(game);
        Entity entity = sprite(game, world);
        Animator animator = entity.add(new Animator(hero));
        animator.play("idle");
        step(2);
        assertThat(animator.animations()).isSameAs(set);
        assertThat(animator.currentName()).isEqualTo("idle");

        Entity waiting = sprite(game, world);
        Animator later = waiting.add(new Animator(AssetKey.animations("test:animations/missing")));
        later.playOnce("x");
        step(1);
        assertThat(later.current()).isNull();

        SpriteAnimation withStep = idle.onFrame(1, e -> {}).withMode(PlayMode.ONCE);
        assertThat(withStep.hasFrameEvents()).isTrue();
        assertThat(set.with(withStep).getOrThrow("idle").mode()).isEqualTo(PlayMode.ONCE);
        assertThat(withStep.name()).isEqualTo("idle");
        assertThat(withStep.toString()).contains("idle");
        assertThat(set.toString()).contains("idle");

        List<TextureRegion> f = frames(game, 3);
        TextureAtlas atlas = new FakeAtlas(Map.of("slime/hop_0", f.get(0), "slime/hop_1", f.get(1)));
        AnimationSet slime = AnimationSet.fromAtlas(atlas, "slime/", 8f, "hop");
        assertThat(slime.getOrThrow("hop").frameCount()).isEqualTo(2);
        assertThatThrownBy(() -> AnimationSet.fromAtlas(atlas, "slime/", 8f, "fly"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpriteAnimation.builder("empty").build()).isInstanceOf(IllegalStateException.class);
        assertThat(PlayMode.ONCE.ends()).isTrue();
        assertThat(PlayMode.LOOP.ends()).isFalse();
    }

    private record FakeAtlas(Map<String, TextureRegion> regions) implements TextureAtlas {
        @Override
        public TextureRegion region(String name) {
            TextureRegion region = regions.get(name);
            if (region == null) {
                throw new IllegalArgumentException(name);
            }
            return region;
        }

        @Override
        public @Nullable TextureRegion find(String name) {
            return regions.get(name);
        }

        @Override
        public List<TextureRegion> regions(String prefix) {
            Map<String, TextureRegion> sorted = new LinkedHashMap<>();
            regions.keySet().stream()
                    .filter(n -> n.startsWith(prefix))
                    .sorted()
                    .forEach(n -> sorted.put(n, regions.get(n)));
            return new ArrayList<>(sorted.values());
        }

        @Override
        public Set<String> names() {
            return regions.keySet();
        }

        @Override
        public List<Texture> pages() {
            return List.of();
        }
    }
}
