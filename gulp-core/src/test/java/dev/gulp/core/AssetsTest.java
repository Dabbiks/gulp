package dev.gulp.core;

import static dev.gulp.core.Fixtures.started;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.asset.AssetGroupLoadedEvent;
import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.asset.AssetLoadEvent;
import dev.gulp.api.asset.AssetLoadFailedEvent;
import dev.gulp.api.asset.AssetLoader;
import dev.gulp.api.asset.AssetType;
import dev.gulp.api.asset.Assets;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.backend.headless.HeadlessBackend;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AssetsTest {

    private static final AssetKey<Texture> PLAYER = AssetKey.texture("coins:sprites/player");
    private static final AssetKey<String> HELLO = AssetKey.text("coins:data/hello");

    private @Nullable HeadlessRunner runner;

    @AfterEach
    void tearDown() {
        if (runner != null) {
            runner.stop();
        }
    }

    private static void files(HeadlessBackend backend) {
        put(backend, "coins/sprites/player.png", "png");
        put(backend, "coins/sprites/ui/button.png", "png");
        put(backend, "coins/sprites/ui/frame.png", "png");
        put(backend, "coins/sprites/ui/notes.txt", "hi");
        put(backend, "coins/data/hello.txt", "Hello, Gulp!");
        put(backend, "coins/data/level.lvl", "3 x 2");
        put(backend, "coins/data/broken.lvl", "oops");
        put(backend, "assets.manifest.json", """
                {"files":[{"path":"coins/sprites/player.png","size":3},
                {"path":"coins/sprites/ui/button.png","size":3},{"path":"coins/sprites/ui/frame.png","size":3},
                {"path":"coins/sprites/ui/notes.txt","size":2},{"path":"coins/data/hello.txt","size":12},
                {"path":"coins/data/level.lvl","size":5},{"path":"coins/data/broken.lvl","size":4}]}
                """);
    }

    private static void put(HeadlessBackend backend, String path, String content) {
        backend.files().putAsset(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private HeadlessRunner run(TestGame game) {
        runner = started(game, AssetsTest::files);
        return runner;
    }

    private static <T> AtomicReference<T> capture(Promise<T> promise) {
        AtomicReference<T> value = new AtomicReference<>();
        promise.thenSync(value::set);
        return value;
    }

    @Test
    void startupGroupLoadsBeforeOnStart() {
        TestGame game = new TestGame();
        List<String> seen = new ArrayList<>();
        game.onLoad = () -> game.assets().startup().add(PLAYER).add(HELLO);
        game.onStart = () -> {
            seen.add(game.assets().get(HELLO));
            seen.add(game.assets().get(PLAYER).width() + "x"
                    + game.assets().get(PLAYER).height());
        };
        HeadlessRunner r = run(game);
        r.step(3);
        assertThat(seen).containsExactly("Hello, Gulp!", "1x1");
        assertThat(game.assets().startup().isLoaded()).isTrue();
        assertThat(game.assets().startup().progress()).isEqualTo(1f);
        assertThat(game.assets().progress()).isEqualTo(1f);
    }

    @Test
    void loadCountsReferencesAndUnloadDisposes() {
        TestGame game = new TestGame();
        HeadlessRunner r = run(game);
        Assets assets = game.assets();
        List<String> events = new ArrayList<>();
        game.on(AssetLoadEvent.class, e -> events.add("loaded " + e.key().key()));
        AtomicReference<Texture> first = capture(assets.load(PLAYER));
        assertThat(assets.isLoaded(PLAYER)).isFalse();
        assertThatThrownBy(() -> assets.get(PLAYER)).hasMessageContaining("still loading");
        r.step(3);
        AtomicReference<Texture> second = capture(assets.load(PLAYER));
        r.step(1);
        assertThat(first.get()).isSameAs(second.get()).isSameAs(assets.get(PLAYER));
        assertThat(events).containsExactly("loaded coins:sprites/player");

        assets.unload(PLAYER);
        assertThat(assets.isLoaded(PLAYER)).isTrue();
        assets.unload(PLAYER);
        assertThat(assets.isLoaded(PLAYER)).isFalse();
        assertThat(first.get().isDisposed()).isTrue();
        assertThatThrownBy(() -> assets.get(PLAYER)).hasMessageContaining("not loaded");
        assets.unload(PLAYER);
    }

    @Test
    void missingFilesAndLoadersFailWithEvents() {
        TestGame game = new TestGame();
        HeadlessRunner r = run(game);
        Assets assets = game.assets();
        List<Throwable> failures = new ArrayList<>();
        game.on(AssetLoadFailedEvent.class, e -> failures.add(e.error()));
        AtomicReference<Throwable> missing = new AtomicReference<>();
        AssetKey<Texture> nothing = AssetKey.texture("coins:sprites/nothing");
        assets.load(nothing).onFailure(missing::set);
        AssetType<String> noLoader = AssetType.of("unregistered", "lvl");
        assets.load(AssetKey.of(noLoader, "coins:data/level"));
        r.step(3);
        assertThat(missing.get()).hasMessageContaining("coins/sprites/nothing.png");
        assertThat(failures).hasSize(2);
        assertThat(failures.get(1)).hasMessageContaining("No loader");
        assertThatThrownBy(() -> assets.get(nothing)).hasMessageContaining("failed to load");
        assertThatThrownBy(() -> AssetType.of(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AssetType.of("x", ".png")).isInstanceOf(IllegalArgumentException.class);
    }

    record Level(int width, int height) {}

    @Test
    void customLoadersWithDependencies() {
        AssetType<Level> levelType = AssetType.of("level", "lvl");
        TestGame game = new TestGame();
        game.onLoad = () -> game.assets().registerLoader(levelType, new AssetLoader<>() {
            @Override
            public Promise<Level> load(dev.gulp.api.asset.AssetLoadContext context) {
                assertThat(context.path()).endsWith(".lvl");
                return context.dependency(PLAYER)
                        .flatMap(texture -> context.text().map(text -> {
                            String[] parts = text.split(" x ");
                            return new Level(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                        }));
            }
        });
        HeadlessRunner r = run(game);
        Assets assets = game.assets();
        AssetKey<Level> level = AssetKey.of(levelType, "coins:data/level");
        AtomicReference<Level> loaded = capture(assets.load(level));
        AtomicReference<Throwable> broken = new AtomicReference<>();
        assets.load(AssetKey.of(levelType, "coins:data/broken")).onFailure(broken::set);
        r.step(4);
        assertThat(loaded.get()).isEqualTo(new Level(3, 2));
        assertThat(assets.isLoaded(PLAYER)).isTrue();
        assertThat(broken.get()).isInstanceOf(NumberFormatException.class);

        assets.unload(level);
        assertThat(assets.isLoaded(PLAYER)).isFalse();
    }

    @Test
    void groupsExpandFoldersFromTheManifest() {
        TestGame game = new TestGame();
        HeadlessRunner r = run(game);
        Assets assets = game.assets();
        List<String> loadedGroups = new ArrayList<>();
        game.on(AssetGroupLoadedEvent.class, e -> loadedGroups.add(e.group()));
        assets.group("ui").add(HELLO).addFolder("coins:sprites/ui");
        assertThat(assets.group("ui").keys()).containsExactly(HELLO);
        assertThat(assets.group("ui").folders()).containsExactly("coins:sprites/ui");
        AtomicReference<Void> done = new AtomicReference<>();
        List<Boolean> finished = new ArrayList<>();
        assets.loadGroup("ui").thenSync(v -> finished.add(true));
        assertThat(assets.group("ui").progress()).isZero();
        r.step(4);
        assertThat(finished).containsExactly(true);
        assertThat(loadedGroups).containsExactly("ui");
        assertThat(assets.group("ui").isLoaded()).isTrue();
        assertThat(assets.get(AssetKey.texture("coins:sprites/ui/button")).width())
                .isEqualTo(1);
        assertThat(assets.get(AssetKey.text("coins:sprites/ui/notes"))).isEqualTo("hi");

        assets.unloadGroup("ui");
        assertThat(assets.isLoaded(HELLO)).isFalse();
        assertThat(assets.group("ui").isLoaded()).isFalse();
        assets.unloadGroup("unknown");
        assets.loadGroup("empty").thenSync(done::set);
        r.step(1);
        assertThat(loadedGroups).containsExactly("ui", "empty");
    }

    @Test
    void failedStartupAssetsStopTheGame() {
        TestGame game = new TestGame();
        game.onLoad = () -> game.assets().startup().add(AssetKey.pixmap("coins:sprites/missing"));
        HeadlessRunner r = HeadlessRunner.start(game, AssetsTest::files);
        assertThatThrownBy(() -> r.step(4))
                .hasMessageContaining("failed to start")
                .cause()
                .hasMessageContaining("startup assets");
        assertThat(r.engine().failure()).hasMessageContaining("startup assets");
    }

    @Test
    void customLoadingScreenDrawsWhileStartupLoads() {
        TestGame game = new TestGame();
        List<Float> progress = new ArrayList<>();
        game.onLoad = () -> {
            game.assets().startup().add(AssetKey.pixmap("coins:sprites/player"));
            game.assets().setLoadingScreen((draw, display, p) -> progress.add(p));
        };
        Consumer<HeadlessBackend> prepare = AssetsTest::files;
        runner = HeadlessRunner.start(game, prepare);
        runner.step(4);
        assertThat(progress).isNotEmpty().allMatch(p -> p >= 0f && p <= 1f);
        assertThat(runner.engine().isRunning()).isTrue();
        Pixmap image = game.assets().get(AssetKey.pixmap("coins:sprites/player"));
        assertThat(image.width()).isEqualTo(1);
    }
}
