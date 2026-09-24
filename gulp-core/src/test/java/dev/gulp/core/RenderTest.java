package dev.gulp.core;

import static dev.gulp.core.Fixtures.started;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.GameSettings;
import dev.gulp.api.graphics.BlendMode;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.FrameBuffer;
import dev.gulp.api.graphics.Material;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.Shader;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.math.Polygon;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.render.AspectMode;
import dev.gulp.api.render.Display;
import dev.gulp.api.render.Draw;
import dev.gulp.api.render.PostRenderEvent;
import dev.gulp.api.render.PreRenderEvent;
import dev.gulp.api.render.RenderLayerEvent;
import dev.gulp.api.render.StretchMode;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RenderTest {

    private @Nullable HeadlessRunner runner;

    @AfterEach
    void tearDown() {
        if (runner != null) {
            runner.stop();
        }
    }

    private static final class DrawingGame extends TestGame {
        Consumer<Draw> world = d -> {};
        Consumer<Draw> screen = d -> {};

        @Override
        public void configure(GameSettings settings) {
            settings.baseResolution(320, 180);
        }

        @Override
        public void onStart() {
            on(RenderLayerEvent.class, e -> {
                if (e.layer().name().equals("entities")) {
                    world.accept(e.draw());
                } else if (e.layer().name().equals("ui")) {
                    screen.accept(e.draw());
                }
            });
        }
    }

    private HeadlessRunner run(DrawingGame game) {
        runner = started(game);
        return runner;
    }

    @Test
    void spritesSharingATextureBatchIntoOneDrawCall() {
        DrawingGame game = new DrawingGame();
        HeadlessRunner r = run(game);
        Texture texture = game.graphics().texture(new Pixmap(8, 8));
        texture.upload(new Pixmap(16, 16));
        assertThat(texture.width()).isEqualTo(16);
        TextureRegion region = texture.region();
        game.world = d -> {
            for (int i = 0; i < 20_000; i++) {
                d.image(region, i % 100, i / 100f);
            }
        };
        long before = r.backend().gl().drawCallCount();
        r.step(1);
        // 20 000 quads need two batches of 16 384.
        assertThat(r.backend().gl().drawCallCount() - before).isEqualTo(2);
        assertThat(game.display().stats().drawCalls()).isEqualTo(2);
        assertThat(game.display().stats().vertices()).isEqualTo(80_000);
    }

    @Test
    void stateChangesSplitBatches() {
        DrawingGame game = new DrawingGame();
        HeadlessRunner r = run(game);
        Texture a = game.graphics().texture(new Pixmap(4, 4));
        Texture b = game.graphics().texture(new Pixmap(4, 4));
        game.world = d -> {
            d.image(a.region(), 0, 0).image(b.region(), 0, 0).image(b.region(), 1, 1);
            d.material(Material.DEFAULT.withBlend(BlendMode.ADD)).image(b.region(), 0, 0);
        };
        r.step(1);
        assertThat(game.display().stats().drawCalls()).isEqualTo(3);
        assertThat(game.display().stats().textureBinds()).isEqualTo(2);
    }

    @Test
    void shapesAndTransformsDraw() {
        DrawingGame game = new DrawingGame();
        HeadlessRunner r = run(game);
        game.screen = d -> {
            d.push().translate(10, 10).rotate(45).scale(2, 2).color(Color.RED).alpha(0.5f);
            d.rect(0, 0, 10, 10)
                    .rectOutline(new Rect(0, 0, 20, 20), 2)
                    .roundedRect(new Rect(0, 0, 30, 20), 5)
                    .gradientRect(new Rect(0, 0, 5, 5), Color.WHITE, Color.BLACK)
                    .circle(50, 50, 10)
                    .circleOutline(50, 50, 10, 2)
                    .ellipse(0, 0, 5, 3)
                    .arc(0, 0, 10, 0, 270)
                    .line(0, 0, 10, 10, 1)
                    .polyline(List.of(Vec2.ZERO, new Vec2(5, 0), new Vec2(5, 5)), 1, true)
                    .polygon(new Polygon(List.of(
                            new Vec2(0, 0), new Vec2(10, 0), new Vec2(5, 3), new Vec2(10, 10), new Vec2(0, 10))))
                    .triangle(0, 0, 1, 0, 0, 1);
            assertThat(d.color()).isEqualTo(Color.RED);
            assertThat(d.pixelSize()).isLessThan(1f);
            d.pop();
            assertThat(d.color()).isEqualTo(Color.WHITE);
            assertThatThrownBy(d::pop).isInstanceOf(IllegalStateException.class);
        };
        r.step(1);
        assertThat(game.display().stats().drawCalls()).isEqualTo(1);
        assertThat(game.display().stats().vertices()).isGreaterThan(200);
    }

    @Test
    void clipsNestAndRestore() {
        DrawingGame game = new DrawingGame();
        HeadlessRunner r = run(game);
        List<Boolean> ran = new ArrayList<>();
        game.screen = d -> d.clip(new Rect(0, 0, 100, 100), () -> {
            d.rect(0, 0, 10, 10);
            d.clip(new Rect(50, 50, 100, 100), () -> {
                d.rect(0, 0, 10, 10);
                ran.add(true);
            });
        });
        long before = r.backend().gl().drawCallCount();
        r.step(1);
        assertThat(ran).containsExactly(true);
        assertThat(r.backend().gl().drawCallCount() - before).isEqualTo(2);
    }

    @Test
    void frameBuffersAndShaders() {
        DrawingGame game = new DrawingGame();
        HeadlessRunner r = run(game);
        FrameBuffer buffer = game.graphics().frameBuffer(64, 32);
        assertThat(buffer.region().isFlipY()).isTrue();
        assertThat(buffer.hasStencil()).isFalse();
        Shader shader = game.graphics()
                .shader(
                        "#include \"gulp:common.glsl\"\nvoid main() { fragColor = texture(u_texture, v_texCoord) * v_color; }");
        shader.set("u_time", 1f);
        game.graphics().include("my:noise", "float n() { return 0.0; }");
        assertThatThrownBy(() -> game.graphics().include("gulp:x", "")).isInstanceOf(IllegalArgumentException.class);
        game.screen = d -> {
            d.into(buffer, inner -> inner.rect(0, 0, 10, 10));
            d.material(Material.DEFAULT.withShader(shader)).image(buffer.region(), 0, 0);
        };
        r.step(1);
        assertThat(game.display().stats().drawCalls()).isEqualTo(2);
        buffer.dispose();
        assertThat(game.graphics().white().texture().width()).isEqualTo(1);
    }

    @Test
    void displaySizeIsKnownBeforeTheFirstFrame() {
        runner = HeadlessRunner.start(new DrawingGame());
        assertThat(runner.engine().display().width()).isEqualTo(320f);
        assertThat(runner.engine().display().camera().bounds().width()).isEqualTo(20f);
    }

    @Test
    void displayLayoutFollowsWindowAndSettings() {
        DrawingGame game = new DrawingGame();
        HeadlessRunner r = run(game);
        Display display = game.display();
        assertThat(display.width()).isEqualTo(320f);
        assertThat(display.height()).isEqualTo(180f);
        assertThat(display.stretchMode()).isEqualTo(StretchMode.CANVAS);
        assertThat(display.layers())
                .extracting(l -> l.name())
                .containsExactly("background", "tiles", "entities", "foreground", "effects", "ui", "overlay");
        assertThatThrownBy(() -> display.addLayer("ui", 5, true)).isInstanceOf(IllegalArgumentException.class);
        assertThat(display.addLayer("sky", -100, false))
                .isSameAs(display.layers().get(0));
        assertThat(display.layer("missing")).isNull();

        r.backend().window().simulateResize(1000, 1000, 1f);
        r.step(1);
        assertThat(display.viewport().width()).isEqualTo(1000f);
        assertThat(display.viewport().y()).isEqualTo(218f);
        assertThat(display.framebufferWidth()).isEqualTo(1000);

        display.setAspectMode(AspectMode.EXPAND);
        display.setStretchMode(StretchMode.VIEWPORT);
        display.setIntegerScaling(true);
        display.setPixelSnap(true);
        display.setLetterboxColor(Color.GRAY);
        display.setBaseResolution(100, 100);
        r.step(2);
        assertThat(display.width()).isEqualTo(100f);
        assertThat(display.isPixelSnap()).isTrue();
        assertThat(display.isIntegerScaling()).isTrue();
        assertThat(display.letterboxColor()).isEqualTo(Color.GRAY);
        assertThat(display.baseWidth()).isEqualTo(100);
        assertThatThrownBy(() -> display.setBaseResolution(0, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void displayChangesInPreRenderApplyToTheSameFrame() {
        DrawingGame game = new DrawingGame();
        HeadlessRunner r = run(game);
        List<Float> widths = new ArrayList<>();
        game.on(PreRenderEvent.class, e -> game.display().setBaseResolution(100, 50));
        game.screen = d -> widths.add(game.display().width());
        r.step(1);
        assertThat(widths).containsExactly(100f);
    }

    @Test
    void renderEventsAndScreenshots() {
        DrawingGame game = new DrawingGame();
        HeadlessRunner r = run(game);
        List<String> events = new ArrayList<>();
        game.on(PreRenderEvent.class, e -> events.add("pre"));
        game.on(PostRenderEvent.class, e -> events.add("post"));
        AtomicReference<Pixmap> shot = new AtomicReference<>();
        game.display().screenshot().thenSync(shot::set);
        r.step(1);
        assertThat(events).containsExactly("pre", "post");
        r.step(1);
        assertThat(shot.get()).isNotNull();
        assertThat(shot.get().width()).isEqualTo(r.backend().window().framebufferWidth());
        assertThat(game.display().fps()).isGreaterThanOrEqualTo(0f);
    }

    @Test
    void decodeGoesThroughThePlatform() {
        DrawingGame game = new DrawingGame();
        HeadlessRunner r = run(game);
        AtomicReference<Pixmap> image = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        game.graphics().decode(new byte[] {1, 2, 3}).thenSync(image::set);
        game.graphics().decode(new byte[0]).onFailure(error::set);
        r.step(2);
        assertThat(image.get().width()).isEqualTo(1);
        assertThat(error.get()).isInstanceOf(IllegalArgumentException.class);
        assertThat(game.graphics().maxTextureSize()).isPositive();
    }
}
