package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.component.LightSource;
import dev.gulp.api.entity.component.Occluder;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Materials;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.physics.Shape;
import dev.gulp.api.registry.Key;
import dev.gulp.api.render.Bloom;
import dev.gulp.api.render.Blur;
import dev.gulp.api.render.ChromaticAberration;
import dev.gulp.api.render.ColorGrade;
import dev.gulp.api.render.Crt;
import dev.gulp.api.render.CustomEffect;
import dev.gulp.api.render.Light;
import dev.gulp.api.render.Lighting;
import dev.gulp.api.render.Pixelate;
import dev.gulp.api.render.PostEffects;
import dev.gulp.api.render.StretchMode;
import dev.gulp.api.render.Vignette;
import dev.gulp.api.world.TileSet;
import dev.gulp.api.world.TileShape;
import dev.gulp.api.world.TileType;
import dev.gulp.api.world.World;
import dev.gulp.core.Fixtures.TestGame;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class LightingPostTest extends JuiceFixture {

    static final TileType ROCK = TileType.builder(Key.of("test", "rock"))
            .tileSet(TileSet.of(AssetKey.texture("test:textures/sheet"), 16, 16), 0)
            .shape(TileShape.FULL)
            .build();

    private TestGame game() {
        return start(b -> b.files().putAsset("test/textures/sheet.png", "png".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void lightsWithShadowsDrawIntoTheLightMap() {
        TestGame game = game();
        World world = world(game);
        Lighting lighting = world.lighting();
        assertThat(lighting.isEnabled()).isFalse();
        step(1);
        int unlit = game.display().stats().vertices();

        world.tileMap().fill("ground", -6, 3, 12, 1, ROCK);
        world.tileMap().fill("ground", 2, -2, 1, 3, ROCK);
        lighting.ambient(Color.rgb(0x202030));
        assertThat(lighting.isEnabled()).isTrue();
        assertThat(lighting.ambient()).isEqualTo(Color.rgb(0x202030));
        Light torch = lighting.add(Light.point(Color.rgb(0xffb347), 6f).setPosition(Vec2.ZERO));
        lighting.add(torch);
        assertThat(lighting.lights()).containsExactly(torch);
        lighting.add(Light.spot(Color.WHITE, 5f, 90f, 40f).setPosition(new Vec2(-2f, 0f)));
        lighting.add(Light.directional(Color.BLUE, 45f).setIntensity(0.2f));
        lighting.add(Light.point(Color.RED, 3f).setPosition(new Vec2(500f, 500f)));
        lighting.add(Light.point(Color.RED, 3f).setEnabled(false));
        lighting.add(Light.point(Color.GREEN, 3f).setShadows(false).setFalloff(2f));

        Entity pillar = world.spawn(THING, -1f, -1f);
        pillar.add(new Occluder());
        pillar.setRotation(30f);
        world.spawn(THING, 1f, -1f).add(new Occluder(Shape.circle(0.4f)).offset(0f, 0.1f));
        world.spawn(THING, 0f, 1.5f).add(new Occluder(Shape.box(2f, 0.2f)));
        world.spawn(THING, -1f, 1f).add(new Occluder(Shape.capsule(0.3f, 1f)));
        world.spawn(THING, 1f, 1f).add(new Occluder(Shape.segment(Vec2.ZERO, new Vec2(1f, 0f))));
        world.spawn(THING, -3f, 0f)
                .add(new Occluder(Shape.polygon(new Vec2(0f, 0f), new Vec2(1f, 0f), new Vec2(0f, 1f))));
        world.spawn(THING, 3f, 0f)
                .add(new Occluder(Shape.chain(List.of(new Vec2(0f, 0f), new Vec2(1f, 1f), new Vec2(2f, 0f)), false)));
        Entity far = world.spawn(THING, 100f, 100f);
        far.add(new Occluder());
        Entity lamp = world.spawn(THING, 0f, -2f);
        LightSource source = lamp.add(new LightSource(Light.point(Color.WHITE, 4f)).offset(0f, -0.5f));
        step(2);
        assertThat(source.light().position()).isEqualTo(new Vec2(0f, -2.5f));
        assertThat(source.offset()).isEqualTo(new Vec2(0f, -0.5f));
        assertThat(lighting.lights()).contains(source.light());
        assertThat(game.display().stats().vertices()).isGreaterThan(unlit + 3 * 720);

        lighting.setTileShadows(false);
        assertThat(lighting.tileShadows()).isFalse();
        step(1);
        lamp.remove();
        step(2);
        assertThat(lighting.lights()).doesNotContain(source.light());
        assertThat(lighting.remove(torch)).isTrue();
        lighting.disable();
        assertThat(lighting.isEnabled()).isFalse();
        step(1);
    }

    @Test
    void postEffectChainsRunForTheWorldAndTheDisplay() {
        TestGame game = game();
        World world = world(game);
        Entity entity = sprite(game, world);
        entity.get(SpriteComponent.class).setMaterial(Materials.OUTLINE).setEffect(Color.RED);
        Entity other = sprite(game, world);
        other.get(SpriteComponent.class).setMaterial(Materials.DISSOLVE).setEffect(Color.WHITE.withAlpha(0.5f));
        sprite(game, world).get(SpriteComponent.class).setMaterial(Materials.GRAYSCALE);
        sprite(game, world).get(SpriteComponent.class).setMaterial(Materials.TINT);
        sprite(game, world).get(SpriteComponent.class).setMaterial(Materials.FLASH);
        step(2);
        int plainCalls = game.display().stats().drawCalls();

        PostEffects chain = world.postEffects();
        Bloom bloom = chain.add(new Bloom().threshold(0.5f).intensity(1.5f).radius(2f));
        chain.add(bloom);
        chain.add(new Vignette().intensity(0.6f).radius(0.7f).softness(0.3f).color(Color.BLACK));
        chain.add(new ColorGrade(game.graphics().texture(new Pixmap(256, 16))).intensity(0.5f));
        chain.add(new ColorGrade(game.graphics().texture(new Pixmap(256, 16))).lut(null));
        chain.add(new Blur().radius(3f));
        Pixelate disabled = chain.add(new Pixelate().size(3f));
        disabled.setEnabled(false);
        assertThat(chain.effects()).hasSize(6);
        step(2);
        assertThat(game.display().stats().drawCalls()).isGreaterThan(plainCalls + 8);

        PostEffects screen = game.display().postEffects();
        screen.add(new ChromaticAberration().amount(3f));
        screen.add(new Crt().curvature(0.2f).scanlines(0.4f));
        screen.add(new Pixelate().size(2f));
        screen.add(new CustomEffect(game.graphics().shader("""
                #include "gulp:common.glsl"
                uniform float u_time;
                void main() { fragColor = texture(u_texture, v_texCoord) * v_color; }
                """)));
        step(2);
        game.display().setStretchMode(StretchMode.VIEWPORT);
        step(2);
        world.lighting().ambient(Color.GRAY);
        step(2);

        assertThat(chain.remove(bloom)).isTrue();
        chain.clear();
        screen.clear();
        assertThat(screen.effects()).isEmpty();
        step(1);
        assertThat(runner.isRunning()).isTrue();
    }
}
