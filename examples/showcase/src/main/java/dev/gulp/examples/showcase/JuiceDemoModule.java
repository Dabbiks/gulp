package dev.gulp.examples.showcase;

import dev.gulp.api.anim.Animator;
import dev.gulp.api.anim.Props;
import dev.gulp.api.anim.Tween;
import dev.gulp.api.anim.Tweens;
import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.asset.AssetReloadEvent;
import dev.gulp.api.asset.AssetType;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.entity.component.LightSource;
import dev.gulp.api.entity.component.Occluder;
import dev.gulp.api.entity.component.ParticleEmitter;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.event.lifecycle.TickStartEvent;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.input.KeyboardKey;
import dev.gulp.api.input.Keys;
import dev.gulp.api.math.Ease;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleInfo;
import dev.gulp.api.particle.ParticleEffect;
import dev.gulp.api.particle.ParticleInstance;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.render.Bloom;
import dev.gulp.api.render.ChromaticAberration;
import dev.gulp.api.render.Crt;
import dev.gulp.api.render.Draw;
import dev.gulp.api.render.Light;
import dev.gulp.api.render.Pixelate;
import dev.gulp.api.render.PostEffect;
import dev.gulp.api.render.RenderLayerEvent;
import dev.gulp.api.render.Vignette;
import dev.gulp.api.text.TextStyle;
import dev.gulp.api.world.TileMap;
import dev.gulp.api.world.TileShape;
import dev.gulp.api.world.TileType;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Stage 8 demo, screen 2: a small room with a walking Aseprite hero, torches with fire particles and flickering
 * lights, crates casting shadows, a light following the mouse and post-processing. Space hits: sparks, flash, punch
 * and camera shake. Particle JSON files in {@code particles/} reload while the game runs.
 */
@ModuleInfo(id = "juice")
final class JuiceDemoModule extends GameModule {

    static final int SCREEN = 2;

    private static final AssetKey<ParticleEffect> FIRE = GameAssets.Particles.FIRE;
    private static final AssetKey<ParticleEffect> SPARKS = GameAssets.Particles.SPARKS;
    private static final AssetKey<ParticleEffect> DUST = GameAssets.Particles.DUST;

    private TileType block;
    private TileType backwall;
    private InputAction hit;
    private InputAction bloomToggle;
    private InputAction vignetteToggle;
    private InputAction crtToggle;
    private InputAction pixelToggle;
    private InputAction lightsToggle;

    private @Nullable World world;
    private @Nullable Entity hero;
    private final Light mouseLight = Light.point(Color.rgb(0xfff1d6), 7f).setIntensity(0.9f);
    private final Bloom bloom = new Bloom().threshold(0.55f).intensity(1f).radius(3f);
    private final Vignette vignette = new Vignette().intensity(0.7f);
    private final Crt crt = new Crt();
    private final Pixelate pixelate = new Pixelate().size(3f);
    private final ChromaticAberration aberration = new ChromaticAberration().amount(0f);
    private final List<ParticleEmitter> fires = new ArrayList<>();
    private @Nullable ParticleInstance dust;
    private int shown = -1;
    private float lastX;
    private boolean moving;

    @Override
    public void onLoad() {
        var registries = registries();
        block = registries.register(
                Registries.TILE_TYPE,
                TileType.builder(key("juice_block"))
                        .region(GameAssets.Sprites.BLOCK)
                        .shape(TileShape.FULL)
                        .build());
        backwall = registries.register(
                Registries.TILE_TYPE,
                TileType.builder(key("juice_backwall"))
                        .region(GameAssets.Sprites.BACKWALL)
                        .build());
        hit = action("juice_hit", Keys.SPACE);
        bloomToggle = action("juice_bloom", Keys.B);
        vignetteToggle = action("juice_vignette", Keys.V);
        crtToggle = action("juice_crt", Keys.C);
        pixelToggle = action("juice_pixelate", Keys.P);
        lightsToggle = action("juice_lights", Keys.L);
        var startup = assets().startup();
        startup.add(GameAssets.Animations.HERO);
        startup.add(FIRE);
        startup.add(SPARKS);
        startup.add(DUST);
        startup.add(GameAssets.Particles.EMBER);
        startup.add(GameAssets.Sprites.CRATE);
        startup.add(GameAssets.Sprites.TORCH);
        startup.add(GameAssets.Sprites.SPARK);
    }

    private InputAction action(String name, KeyboardKey key) {
        return registries()
                .register(
                        Registries.INPUT_ACTION,
                        InputAction.builder(key(name)).bind(key).build());
    }

    @Override
    public void onEnable() {
        // The other screens draw without a world; an empty one with the display's camera keeps them as they were.
        World plain = worlds().create("plain", WorldSettings.DEFAULT);
        plain.camera().setPosition(display().camera().position());
        plain.camera().setZoom(display().camera().zoom());
        World room = worlds().create("juice", WorldSettings.DEFAULT);
        world = room;
        build(room);
        on(TickStartEvent.class, e -> tick());
        on(RenderLayerEvent.class, e -> {
            if (e.layer().name().equals("overlay") && ShowcaseGame.screen() == SCREEN) {
                drawHelp(e.draw());
            }
        });
        on(AssetReloadEvent.class, e -> {
            if (e.key().type() == AssetType.PARTICLES) {
                restartEffects();
            }
        });
        showScreen();
    }

    private void build(World room) {
        room.camera().setPosition(new Vec2(0f, 1.5f));
        room.camera().setZoom(3f);
        TileMap map = room.tileMap();
        // A brick wall behind the room gives the lights something to fall on; it has no collision, so no shadows.
        map.addLayer("backdrop", -10).setCollision(false);
        map.fill("backdrop", -10, -3, 21, 8, backwall);
        map.fill("ground", -11, 5, 23, 2, block);
        map.fill("ground", -11, -3, 1, 8, block);
        map.fill("ground", 11, -3, 1, 8, block);
        map.fill("ground", -3, 2, 3, 1, block);

        room.lighting().ambient(Color.rgb(0x241f3a));
        room.lighting().add(mouseLight);
        room.postEffects().add(bloom);
        room.postEffects().add(vignette);
        room.postEffects().add(aberration);
        room.postEffects().add(pixelate).setEnabled(false);
        room.postEffects().add(crt).setEnabled(false);

        torch(room, -7f);
        torch(room, 6f);
        crate(room, -4.5f, 4.5f);
        crate(room, 2.5f, 4.5f);
        crate(room, -1.5f, 1.5f);

        EntityType heroType = EntityType.builder(key("juice_hero")).size(1f, 1f).build();
        Entity walker = room.spawn(heroType, -5f, 4.5f);
        TextureRegion first =
                assets().get(GameAssets.Animations.HERO).getOrThrow("idle").frame(0);
        walker.add(new SpriteComponent(first));
        walker.add(new LightSource(
                Light.point(Color.rgb(0xffb0b0), 3f).setShadows(false).setIntensity(0.6f)));
        Animator animator = walker.add(new Animator(GameAssets.Animations.HERO));
        lastX = walker.position().x();
        animator.auto().when(() -> moving, "run").otherwise("idle");
        Tweens.sequence(
                        Tweens.to(walker, Props.X, 4f, 3f).ease(Ease.IN_OUT_SINE),
                        Tweens.wait(1.2f),
                        Tweens.to(walker, Props.X, -5f, 3f).ease(Ease.IN_OUT_SINE),
                        Tweens.wait(1.2f))
                .repeat(-1)
                .owner(this)
                .start();
        hero = walker;
        dust = room.spawnParticles(DUST, new Vec2(0f, 1f));
    }

    private void torch(World room, float x) {
        EntityType type = EntityType.builder(key("juice_torch")).size(0.5f, 1f).build();
        Entity torch = room.spawn(type, x, 4.5f);
        torch.add(new SpriteComponent(GameAssets.Sprites.TORCH));
        LightSource light =
                torch.add(new LightSource(Light.point(Color.rgb(0xffa040), 6f).setFalloff(1.2f)).offset(0f, -0.6f));
        fires.add(torch.add(new ParticleEmitter(FIRE).offset(0f, -0.45f)));
        // Flicker: a gentle random wobble of the intensity, forever.
        float seed = x * 13.7f;
        Tweens.custom(light.light(), 1f, t -> {
                    double time = System.nanoTime() / 1e9 + seed;
                    float wobble = (float) (Math.sin(time * 11.0) * 0.08 + Math.sin(time * 23.0) * 0.05);
                    light.light().setIntensity(1.2f + wobble);
                })
                .repeat(-1)
                .owner(this)
                .start();
    }

    private void crate(World room, float x, float y) {
        EntityType type = EntityType.builder(key("juice_crate")).size(1f, 1f).build();
        Entity crate = room.spawn(type, x, y);
        crate.add(new SpriteComponent(GameAssets.Sprites.CRATE));
        crate.add(new Occluder());
    }

    private void restartEffects() {
        for (ParticleEmitter fire : fires) {
            fire.play();
        }
        World room = world;
        ParticleInstance old = dust;
        if (room != null && old != null) {
            old.stop();
            dust = room.spawnParticles(DUST, new Vec2(0f, 1f));
        }
        logger().info("Particle effects reloaded");
    }

    private void showScreen() {
        int screen = ShowcaseGame.screen();
        if (screen == shown) {
            return;
        }
        shown = screen;
        worlds().switchTo(screen == SCREEN ? "juice" : "plain");
        if (screen == SCREEN) {
            logger().info("Juice screen: Space hits, B/V/C/P toggle post effects, L toggles lights");
        }
    }

    private void tick() {
        showScreen();
        World room = world;
        Entity walker = hero;
        if (room == null || walker == null || ShowcaseGame.screen() != SCREEN) {
            return;
        }
        // Tweens move the hero every frame; compare with the last tick to tell walking from standing.
        float x = walker.position().x();
        moving = Math.abs(x - lastX) > 0.001f;
        if (moving) {
            walker.setFlipX(x < lastX);
        }
        lastX = x;
        mouseLight.setPosition(input().mouseWorld(room.camera()));
        if (input().justPressed(hit)) {
            punch(room, walker);
        }
        toggle(bloomToggle, bloom);
        toggle(vignetteToggle, vignette);
        toggle(crtToggle, crt);
        toggle(pixelToggle, pixelate);
        if (input().justPressed(lightsToggle)) {
            if (room.lighting().isEnabled()) {
                room.lighting().disable();
            } else {
                room.lighting().ambient(Color.rgb(0x241f3a));
            }
        }
    }

    private void toggle(InputAction action, PostEffect effect) {
        if (input().justPressed(action)) {
            effect.setEnabled(!effect.isEnabled());
        }
    }

    private void punch(World room, Entity walker) {
        room.spawnParticles(SPARKS, walker.position().add(0f, -0.2f));
        Tweens.flash(walker, Color.WHITE, 0.18f).start();
        Tweens.punchScale(walker, 0.35f, 0.25f).start();
        Tweens.shake(room.camera(), 0.45f, 0.3f).start();
        Tween glow = Tweens.fromTo(bloom, Bloom.INTENSITY, 2.6f, 1f, 0.45f).ease(Ease.OUT_CUBIC);
        Tweens.parallel(glow, Tweens.fromTo(aberration, ChromaticAberration.AMOUNT, 4f, 0f, 0.3f))
                .start();
    }

    private void drawHelp(Draw draw) {
        TextStyle style = TextStyle.of(16).color(Color.rgb(0xfff1e8)).shadow(1, 1, Color.rgba(0x000000aa));
        TextStyle dim = TextStyle.of(14).color(Color.rgb(0xc2c3c7)).shadow(1, 1, Color.rgba(0x000000aa));
        draw.text("Juice: tweens, frame animation, particles, 2D light, post-processing", 16, 16, style);
        draw.text(
                "Space hit   B bloom   V vignette   C CRT   P pixelate   L lights   mouse moves a light", 16, 40, dim);
        World room = world;
        if (room != null) {
            draw.text(
                    room.particles().count() + " particles, "
                            + room.lighting().lights().size() + " lights, "
                            + Math.round(display().fps()) + " FPS",
                    16,
                    62,
                    dim);
        }
    }
}
