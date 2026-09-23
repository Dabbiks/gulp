package dev.gulp.backend.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.graphics.BlendMode;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.FrameBuffer;
import dev.gulp.api.graphics.Material;
import dev.gulp.api.graphics.Mesh2D;
import dev.gulp.api.graphics.NinePatch;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.Shader;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureFilter;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.math.Polygon;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.render.AspectMode;
import dev.gulp.api.render.Draw;
import dev.gulp.api.render.PreRenderEvent;
import dev.gulp.api.render.RenderLayerEvent;
import dev.gulp.api.render.StretchMode;
import dev.gulp.core.GulpEngine;
import dev.gulp.platform.DecodedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Renders scenes in a real OpenGL window and compares screenshots with reference images in
 * {@code src/test/resources/visual}. Run with {@code ./gradlew :gulp-backend-desktop:visualTest}; add
 * {@code -Pgulp.updateReferences} to rewrite the references after checking the images in {@code build/visual}.
 */
@Tag("visual")
class VisualTest {

    private static final int WIDTH = 256;
    private static final int HEIGHT = 160;

    /** A channel differing by more than this counts as a different pixel. */
    private static final int CHANNEL_TOLERANCE = 40;

    /** Share of different pixels allowed; drivers rasterise anti-aliased edges slightly differently. */
    private static final double PIXEL_TOLERANCE = 0.01;

    /** Scene: draws into the world layer (camera at the origin) and the UI layer. */
    private record Scene(
            String name, Runnable setup, BiConsumer<Draw, Resources> world, BiConsumer<Draw, Resources> ui) {}

    private static final class Resources {
        Texture checker;
        TextureRegion arrow;
        TextureRegion rotatedArrow;
        NinePatch panel;
        FrameBuffer buffer;
        Shader invert;
        Mesh2D mesh;
    }

    private final class VisualGame extends Game {
        final List<Scene> scenes;
        final Map<String, Pixmap> shots = new LinkedHashMap<>();
        final Resources resources = new Resources();
        int current = -1;
        boolean pending;

        VisualGame(List<Scene> scenes) {
            this.scenes = scenes;
        }

        @Override
        public String id() {
            return "visual";
        }

        @Override
        public void configure(GameSettings settings) {
            settings.title("Gulp visual test")
                    .windowSize(WIDTH, HEIGHT)
                    .resizable(false)
                    .vsync(false)
                    .clearColor(Color.rgb(0x1d2b53))
                    .baseResolution(WIDTH, HEIGHT);
        }

        @Override
        public void onStart() {
            createResources(this, resources);
            on(PreRenderEvent.class, e -> {
                if (pending) {
                    return;
                }
                current++;
                if (current == scenes.size()) {
                    engine().stop();
                    return;
                }
                Scene scene = scenes.get(current);
                resetDisplay(this);
                scene.setup().run();
                pending = true;
                display().screenshot().thenSync(image -> {
                    shots.put(scene.name(), image);
                    pending = false;
                });
            });
            on(RenderLayerEvent.class, e -> {
                if (current < 0 || current >= scenes.size()) {
                    return;
                }
                Scene scene = scenes.get(current);
                if (e.layer().name().equals("entities")) {
                    scene.world().accept(e.draw(), resources);
                } else if (e.layer().name().equals("ui")) {
                    scene.ui().accept(e.draw(), resources);
                }
            });
        }
    }

    private static void resetDisplay(Game game) {
        var display = game.display();
        display.setStretchMode(StretchMode.CANVAS);
        display.setAspectMode(AspectMode.KEEP);
        display.setBaseResolution(WIDTH, HEIGHT);
        display.setIntegerScaling(false);
        display.setPixelSnap(false);
        display.setLetterboxColor(Color.BLACK);
        display.camera().setPosition(Vec2.ZERO);
        display.camera().setZoom(1f);
        display.camera().setRotation(0f);
    }

    private static void createResources(Game game, Resources r) {
        Pixmap checker = new Pixmap(8, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                checker.setPixel(x, y, (x + y) % 2 == 0 ? Color.WHITE : Color.GRAY);
            }
        }
        r.checker = game.graphics().texture(checker, TextureFilter.NEAREST);

        // An arrow pointing right, 8x4, red tip at the right; the same arrow stored rotated 90 degrees clockwise.
        Pixmap arrow = new Pixmap(8, 4);
        arrow.fillRect(0, 1, 6, 2, Color.YELLOW);
        arrow.fillRect(6, 0, 2, 4, Color.RED);
        Pixmap rotated = new Pixmap(4, 8);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 8; x++) {
                rotated.setPixel(3 - y, x, arrow.getPixel(x, y));
            }
        }
        r.arrow = game.graphics().texture(arrow, TextureFilter.NEAREST).region();
        Texture rotatedTexture = game.graphics().texture(rotated, TextureFilter.NEAREST);
        r.rotatedArrow = new TextureRegion(rotatedTexture, 0, 0, 4, 8, false, false, true, 0, 0, 8, 4);

        Pixmap panel = new Pixmap(12, 12);
        panel.fill(Color.rgb(0x29adff));
        panel.drawRect(0, 0, 12, 12, Color.WHITE);
        panel.fillRect(1, 1, 3, 3, Color.RED);
        panel.fillRect(8, 8, 3, 3, Color.GREEN);
        r.panel = new NinePatch(
                game.graphics().texture(panel, TextureFilter.NEAREST).region(), 4, 4, 4, 4);

        r.buffer = game.graphics().frameBuffer(64, 32);
        r.invert = game.graphics().shader("""
                        #include "gulp:common.glsl"
                        void main() {
                            vec4 c = texture(u_texture, v_texCoord) * v_color;
                            fragColor = vec4(c.a - c.rgb, c.a);
                        }
                        """);

        r.mesh = new Mesh2D();
        int a = r.mesh.vertex(0, 0, 0, 0, Color.RED);
        int b = r.mesh.vertex(40, 0, 0, 0, Color.GREEN);
        int c = r.mesh.vertex(20, 30, 0, 0, Color.BLUE);
        r.mesh.triangle(a, b, c);
    }

    private static List<Scene> scenes() {
        List<Scene> scenes = new ArrayList<>();
        scenes.add(new Scene("shapes", () -> {}, (d, r) -> {}, (d, r) -> {
            d.color(Color.RED).rect(8, 8, 40, 24);
            d.color(Color.GREEN).rectOutline(new Rect(56, 8, 40, 24), 3);
            d.color(Color.ORANGE).roundedRect(new Rect(104, 8, 40, 24), 8);
            d.color(Color.WHITE).gradientRect(new Rect(152, 8, 40, 24), Color.MAGENTA, Color.CYAN);
            d.color(Color.YELLOW).circle(220, 20, 12);
            d.color(Color.CYAN).circleOutline(28, 64, 14, 3);
            d.color(Color.WHITE).ellipse(76, 64, 18, 9);
            d.color(Color.GREEN).arc(124, 64, 14, -90, 270);
            d.color(Color.WHITE).line(152, 50, 196, 78, 2);
            d.color(Color.MAGENTA).triangle(208, 78, 248, 78, 228, 48);
            d.color(Color.ORANGE)
                    .polygon(new Polygon(List.of(
                            new Vec2(8, 100),
                            new Vec2(48, 100),
                            new Vec2(28, 116),
                            new Vec2(48, 140),
                            new Vec2(8, 140))));
            d.color(Color.WHITE).polyline(List.of(new Vec2(60, 140), new Vec2(80, 100), new Vec2(100, 140)), 3, false);
            // Half-transparent overlap: premultiplied blending.
            d.color(Color.RED).rect(120, 100, 40, 40);
            d.color(Color.BLUE).alpha(0.5f).rect(140, 110, 40, 40);
            d.alpha(1f).color(Color.WHITE).mesh(r.mesh, null);
            d.push().translate(200, 110).mesh(r.mesh, null).pop();
        }));
        scenes.add(new Scene("images", () -> {}, (d, r) -> {}, (d, r) -> {
            d.image(r.checker.region(), 8, 8, 32, 32);
            d.image(r.arrow, 48, 8, 32, 16);
            d.image(r.arrow.flipX(), 48, 28, 32, 16);
            d.image(r.arrow.flipY(), 88, 8, 32, 16);
            d.image(r.rotatedArrow, 88, 28, 32, 16);
            d.image(r.arrow, 140, 24, 32, 16, 16, 8, 90);
            d.image(r.arrow, 184, 8);
            d.ninePatch(r.panel, new Rect(8, 56, 100, 40));
            d.tiled(r.checker.region(), new Rect(120, 56, 60, 36));
            d.color(Color.CYAN).image(r.checker.region(), 192, 56, 24, 24);
            d.color(Color.WHITE).alpha(0.5f).image(r.checker.region(), 204, 68, 24, 24);
            d.alpha(1f).material(Material.DEFAULT.withBlend(BlendMode.ADD));
            d.color(Color.RED).circle(40, 128, 16);
            d.color(Color.GREEN).circle(56, 128, 16);
            d.material(Material.DEFAULT).color(Color.WHITE);
            d.material(Material.DEFAULT.withShader(r.invert)).image(r.checker.region(), 90, 108, 40, 40);
            d.material(Material.DEFAULT);
        }));
        scenes.add(new Scene("transforms", () -> {}, (d, r) -> {}, (d, r) -> {
            d.push().translate(40, 40).rotate(30).scale(2, 1);
            d.color(Color.GREEN).rect(-10, -10, 20, 20);
            d.pop();
            d.clip(new Rect(80, 10, 60, 60), () -> {
                d.color(Color.RED).circle(110, 40, 40);
                d.clip(new Rect(100, 30, 60, 60), () -> d.color(Color.YELLOW).circle(110, 40, 40));
            });
            d.into(r.buffer, inner -> {
                inner.color(Color.ORANGE).rect(0, 0, 32, 16);
                inner.color(Color.BLUE).circle(48, 16, 12);
            });
            d.color(Color.WHITE).image(r.buffer.region(), 160, 10, 64, 32);
            d.image(r.buffer.region(), 160, 50, 32, 16);
        }));
        scenes.add(new Scene(
                "camera",
                () -> {},
                (d, r) -> {
                    // World units: 16 pixels each, camera at the origin in the middle of the screen.
                    d.color(Color.GRAY).rect(-8, -5, 16, 10);
                    d.color(Color.RED).rect(0, 0, 1, 1);
                    d.color(Color.GREEN).rect(2, -3, 2, 1);
                    d.color(Color.WHITE).image(r.arrow, -4, 2);
                    d.color(Color.YELLOW).circle(-5, -2, 1);
                },
                (d, r) -> d.color(Color.WHITE).rectOutline(new Rect(0, 0, WIDTH, HEIGHT), 1)));
        scenes.add(new Scene(
                "camera-zoom",
                () -> {},
                (d, r) -> {
                    d.color(Color.RED).rect(0, 0, 1, 1);
                    d.color(Color.GREEN).rect(2, -3, 2, 1);
                    d.color(Color.YELLOW).circle(-5, -2, 1);
                },
                (d, r) -> {}));
        scenes.add(new Scene("viewport", () -> {}, (d, r) -> {}, (d, r) -> {
            // Low base resolution, scaled up with nearest filtering and letterbox bars.
            d.color(Color.RED).circle(20, 20, 12);
            d.color(Color.GREEN).line(0, 0, 64, 40, 1);
            d.color(Color.WHITE).image(r.arrow, 40, 4);
        }));
        return scenes;
    }

    @Test
    void scenesMatchReferences() throws IOException {
        List<Scene> scenes = scenes();
        // Per-scene display setup needs the game, so wire it here.
        VisualGame[] holder = new VisualGame[1];
        List<Scene> wired = new ArrayList<>();
        for (Scene scene : scenes) {
            Runnable setup =
                    switch (scene.name()) {
                        case "camera-zoom" ->
                            () -> {
                                holder[0].display().camera().setPosition(new Vec2(1, -1));
                                holder[0].display().camera().setZoom(2f);
                                holder[0].display().camera().setRotation(20f);
                            };
                        case "viewport" ->
                            () -> {
                                holder[0].display().setBaseResolution(64, 48);
                                holder[0].display().setStretchMode(StretchMode.VIEWPORT);
                                holder[0].display().setIntegerScaling(true);
                                holder[0].display().setLetterboxColor(Color.rgb(0x5f574f));
                            };
                        default -> scene.setup();
                    };
            wired.add(new Scene(scene.name(), setup, scene.world(), scene.ui()));
        }
        VisualGame game = new VisualGame(wired);
        holder[0] = game;

        GameSettings settings = GulpEngine.configure(game);
        DesktopBackend backend = DesktopBackend.create(game.id(), GulpEngine.windowConfig(settings));
        @Nullable Throwable failure;
        try {
            GulpEngine engine = new GulpEngine(game, settings, backend);
            engine.start();
            failure = engine.failure();
        } finally {
            backend.dispose();
        }
        if (failure != null) {
            fail("The visual game failed", failure);
        }
        assertThat(game.shots.keySet())
                .containsExactlyElementsOf(wired.stream().map(Scene::name).toList());

        Path references = Path.of(System.getProperty("gulp.visual.references", "src/test/resources/visual"));
        Path output = Path.of(System.getProperty("gulp.visual.output", "build/visual"));
        boolean update = Boolean.getBoolean("gulp.visual.update");
        Files.createDirectories(output);
        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, Pixmap> shot : game.shots.entrySet()) {
            Pixmap actual = normalise(shot.getValue());
            Files.write(output.resolve(shot.getKey() + ".png"), actual.encodePng());
            Path reference = references.resolve(shot.getKey() + ".png");
            if (update || !Files.exists(reference)) {
                Files.createDirectories(references);
                Files.write(reference, actual.encodePng());
                if (!update) {
                    mismatches.add(shot.getKey() + ": no reference; created " + reference + ", check it");
                }
                continue;
            }
            Pixmap expected = read(reference);
            Pixmap diff = new Pixmap(WIDTH, HEIGHT);
            int different = 0;
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    boolean same = close(actual.getPixel(x, y), expected.getPixel(x, y));
                    diff.setPixel(x, y, same ? Color.BLACK : Color.RED);
                    if (!same) {
                        different++;
                    }
                }
            }
            double share = different / (double) (WIDTH * HEIGHT);
            if (share > PIXEL_TOLERANCE) {
                Files.write(output.resolve(shot.getKey() + "-diff.png"), diff.encodePng());
                mismatches.add(String.format("%s: %.2f%% pixels differ (see %s)", shot.getKey(), share * 100, output));
            }
        }
        assertThat(mismatches).isEmpty();
    }

    /** Scales a HiDPI screenshot down to the reference size. */
    private static Pixmap normalise(Pixmap shot) {
        return shot.width() == WIDTH && shot.height() == HEIGHT ? shot : shot.scaled(WIDTH, HEIGHT);
    }

    private static Pixmap read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
        buffer.put(bytes).flip();
        DecodedImage image = DesktopDecoders.decode(buffer);
        byte[] rgba = new byte[image.width() * image.height() * 4];
        image.pixels().get(rgba);
        return Pixmap.fromRgba(image.width(), image.height(), rgba);
    }

    private static boolean close(int a, int b) {
        for (int shift = 8; shift < 32; shift += 8) {
            if (Math.abs(((a >>> shift) & 0xff) - ((b >>> shift) & 0xff)) > CHANNEL_TOLERANCE) {
                return false;
            }
        }
        return true;
    }
}
