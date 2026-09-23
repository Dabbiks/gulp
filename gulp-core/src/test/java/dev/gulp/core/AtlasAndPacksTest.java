package dev.gulp.core;

import static dev.gulp.core.Fixtures.started;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.asset.AssetReloadEvent;
import dev.gulp.api.asset.ResourcePack;
import dev.gulp.api.asset.ResourcePackChangeEvent;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureAtlas;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.backend.headless.HeadlessBackend;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import dev.gulp.core.asset.AtlasBuilder;
import dev.gulp.core.graphics.RectPacker;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AtlasAndPacksTest {

    private static final String HASH_ATLAS = """
            {"frames": {"hero.png": {"frame": {"x": 0, "y": 0, "w": 4, "h": 2}, "rotated": true,
              "spriteSourceSize": {"x": 1, "y": 1, "w": 4, "h": 2}, "sourceSize": {"w": 6, "h": 4}}},
             "meta": {"image": "packed_0.png"}}
            """;

    private static final String ARRAY_ATLAS = """
            {"frames": [{"filename": "run_10.png", "frame": {"x": 0, "y": 0, "w": 1, "h": 1}},
                        {"filename": "run_2.png", "frame": {"x": 0, "y": 0, "w": 1, "h": 1}}],
             "meta": {"image": "array.png"}}
            """;

    private @Nullable HeadlessRunner runner;

    @AfterEach
    void tearDown() {
        if (runner != null) {
            runner.stop();
        }
    }

    private static void files(HeadlessBackend backend) {
        put(backend, "t/sprites/hero/idle_0.png", "png");
        put(backend, "t/sprites/hero/idle_1.png", "png");
        put(backend, "t/packed.json", HASH_ATLAS);
        put(backend, "t/packed_0.png", "png");
        put(backend, "t/array.json", ARRAY_ATLAS);
        put(backend, "t/array.png", "png");
        put(backend, "t/data/hello.txt", "base");
        put(backend, "t/textures/logo.png", "png");
        put(
                backend,
                "assets.manifest.json",
                "{\"files\":[{\"path\":\"t/sprites/hero/idle_0.png\"},{\"path\":\"t/sprites/hero/idle_1.png\"},"
                        + "{\"path\":\"t/packed.json\"},{\"path\":\"t/packed_0.png\"},{\"path\":\"t/array.json\"},"
                        + "{\"path\":\"t/array.png\"},{\"path\":\"t/data/hello.txt\"},{\"path\":\"t/textures/logo.png\"}]}");
        backend.files()
                .putResourcePack(
                        "better",
                        "Better texts",
                        Map.of("t/data/hello.txt", "from pack".getBytes(StandardCharsets.UTF_8)));
    }

    private static void put(HeadlessBackend backend, String path, String content) {
        backend.files().putAsset(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private <T> T loaded(TestGame game, AssetKey<T> key) {
        game.assets().load(key);
        for (int i = 0; i < 10 && !game.assets().isLoaded(key); i++) {
            runner.step(1);
        }
        return game.assets().get(key);
    }

    @Test
    void folderAtlasesPackAtLoadTimeAndRegionKeysResolve() {
        TestGame game = new TestGame();
        runner = started(game, AtlasAndPacksTest::files);
        TextureRegion idle = loaded(game, AssetKey.region("t:sprites/hero/idle_1"));
        assertThat(idle.width()).isEqualTo(1);
        TextureAtlas atlas = game.assets().get(AssetKey.atlas("t:sprites"));
        assertThat(atlas.names()).containsExactly("hero/idle_0", "hero/idle_1");
        assertThat(atlas.regions("hero/idle_")).hasSize(2);
        assertThat(atlas.pages()).hasSize(1);
        assertThat(atlas.find("nothing")).isNull();
        assertThatThrownBy(() -> atlas.region("hero/idle")).hasMessageContaining("similar");
        assertThat(game.assets().region("t:sprites/hero/idle_0")).isNotNull();
        assertThatThrownBy(() -> game.assets().region("t:other/x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> game.assets().region("t:flat")).isInstanceOf(IllegalArgumentException.class);

        game.assets().unload(AssetKey.region("t:sprites/hero/idle_1"));
        assertThat(game.assets().isLoaded(AssetKey.atlas("t:sprites"))).isFalse();
    }

    @Test
    void texturePackerJsonLayouts() {
        TestGame game = new TestGame();
        runner = started(game, AtlasAndPacksTest::files);
        TextureAtlas packed = loaded(game, AssetKey.atlas("t:packed"));
        TextureRegion hero = packed.region("hero");
        assertThat(hero.isRotated()).isTrue();
        assertThat(hero.width()).isEqualTo(4);
        assertThat(hero.originalWidth()).isEqualTo(6);
        assertThat(hero.offsetX()).isEqualTo(1);

        TextureAtlas array = loaded(game, AssetKey.atlas("t:array"));
        assertThat(array.names()).containsExactly("run_2", "run_10");
    }

    @Test
    void resourcePacksOverrideAndReload() {
        TestGame game = new TestGame();
        runner = started(game, AtlasAndPacksTest::files);
        assertThat(game.assets().resourcePacks().available())
                .containsExactly(new ResourcePack("better", "Better texts"));
        assertThat(loaded(game, AssetKey.text("t:data/hello"))).isEqualTo("base");
        List<String> events = new ArrayList<>();
        game.on(
                ResourcePackChangeEvent.class,
                e -> events.add("packs " + e.enabled().size()));
        game.on(AssetReloadEvent.class, e -> events.add("reload " + e.key().key()));
        game.assets().resourcePacks().setEnabled(List.of("better", "unknown"));
        runner.step(3);
        assertThat(game.assets().get(AssetKey.text("t:data/hello"))).isEqualTo("from pack");
        assertThat(game.assets().resourcePacks().enabled())
                .extracting(ResourcePack::id)
                .containsExactly("better");
        assertThat(events).contains("reload t:data/hello", "packs 1");

        game.assets().resourcePacks().setEnabled(List.of());
        runner.step(3);
        assertThat(game.assets().get(AssetKey.text("t:data/hello"))).isEqualTo("base");
    }

    @Test
    void changedFilesReloadInPlace() {
        TestGame game = new TestGame();
        HeadlessRunner r = started(game, AtlasAndPacksTest::files);
        runner = r;
        Texture logo = loaded(game, AssetKey.texture("t:textures/logo"));
        TextureAtlas atlas = loaded(game, AssetKey.atlas("t:sprites"));
        loaded(game, AssetKey.text("t:data/hello"));
        List<String> reloaded = new ArrayList<>();
        game.on(AssetReloadEvent.class, e -> reloaded.add(e.key().key().toString()));

        r.backend().files().putAsset("t/data/hello.txt", "changed".getBytes(StandardCharsets.UTF_8));
        r.backend().files().simulateAssetChange("t/data/hello.txt");
        r.backend().files().simulateAssetChange("t/textures/logo.png");
        r.backend().files().putAsset("t/sprites/hero/idle_2.png", "png".getBytes(StandardCharsets.UTF_8));
        r.backend().files().simulateAssetChange("t/sprites/hero/idle_2.png");
        r.step(6);
        assertThat(game.assets().get(AssetKey.text("t:data/hello"))).isEqualTo("changed");
        assertThat(game.assets().get(AssetKey.texture("t:textures/logo"))).isSameAs(logo);
        assertThat(game.assets().get(AssetKey.atlas("t:sprites"))).isSameAs(atlas);
        assertThat(reloaded).contains("t:data/hello", "t:textures/logo", "t:sprites");
        assertThat(r.backend().files().isWatched()).isTrue();
    }

    @Test
    void packerFillsPagesAndTrimsImages() {
        RectPacker packer = new RectPacker(64, 64, 1);
        RectPacker.Placement first = packer.add(30, 30);
        RectPacker.Placement second = packer.add(30, 30);
        assertThat(first.page()).isZero();
        assertThat(second.x()).isGreaterThanOrEqualTo(32);
        packer.add(62, 62);
        assertThat(packer.pageCount()).isEqualTo(2);
        assertThat(packer.usedSize(0)[0]).isLessThanOrEqualTo(64);
        assertThatThrownBy(() -> packer.add(70, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RectPacker(0, 1, 0)).isInstanceOf(IllegalArgumentException.class);

        Pixmap sprite = new Pixmap(8, 8);
        sprite.fillRect(2, 3, 4, 2, dev.gulp.api.graphics.Color.RED);
        AtlasBuilder.Result result = AtlasBuilder.build(
                List.of(new AtlasBuilder.Input("a", sprite), new AtlasBuilder.Input("empty", new Pixmap(4, 4))),
                256,
                2);
        AtlasBuilder.Frame frame = result.frames().get(0);
        assertThat(frame.width()).isEqualTo(4);
        assertThat(frame.height()).isEqualTo(2);
        assertThat(frame.offsetX()).isEqualTo(2);
        assertThat(frame.offsetY()).isEqualTo(3);
        assertThat(result.frames().get(1).width()).isEqualTo(1);
        assertThat(result.pages().get(0).getPixel(frame.x() - 1, frame.y())).isEqualTo(sprite.getPixel(2, 3));
        assertThat(result.json("atlas").toString()).contains("atlas_0.png", "\"filename\"");
    }
}
