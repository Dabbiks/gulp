package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.entity.component.WorldText;
import dev.gulp.api.event.Subscription;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.math.Ease;
import dev.gulp.api.math.GridPos;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.registry.Key;
import dev.gulp.api.render.Camera;
import dev.gulp.api.ui.Transitions;
import dev.gulp.api.world.Chunk;
import dev.gulp.api.world.ChunkLoadEvent;
import dev.gulp.api.world.ChunkUnloadEvent;
import dev.gulp.api.world.Terrain;
import dev.gulp.api.world.TileChangeEvent;
import dev.gulp.api.world.TileLayer;
import dev.gulp.api.world.TileMap;
import dev.gulp.api.world.TileOrientation;
import dev.gulp.api.world.TileSet;
import dev.gulp.api.world.TileShape;
import dev.gulp.api.world.TileType;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldLoadEvent;
import dev.gulp.api.world.WorldSettings;
import dev.gulp.api.world.WorldSource;
import dev.gulp.api.world.WorldSwitchEvent;
import dev.gulp.api.world.WorldUnloadEvent;
import dev.gulp.backend.headless.HeadlessBackend;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorldTest {

    static final TileSet SHEET = TileSet.of(AssetKey.texture("test:textures/sheet"), 16, 16);
    static final TileType GRASS =
            TileType.builder(Key.of("test", "grass")).tileSet(SHEET, 0).build();
    static final TileType WALL = TileType.builder(Key.of("test", "wall"))
            .tileSet(SHEET, 1)
            .shape(TileShape.FULL)
            .friction(0.5f)
            .property("sound", "stone")
            .build();
    static final TileType WATER = TileType.builder(Key.of("test", "water"))
            .animation(SHEET, 0.1f, 2, 3)
            .build();
    static final EntityType TREE = EntityType.builder(Key.of("test", "tree"))
            .component(() -> new SpriteComponent(AssetKey.region("test:textures/sheet")))
            .build();

    private @Nullable HeadlessRunner runner;

    @AfterEach
    void tearDown() {
        if (runner != null) {
            runner.stop();
        }
    }

    private static void put(HeadlessBackend backend, String path, String text) {
        backend.files().putAsset(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private TestGame start(Consumer<HeadlessBackend> prepare) {
        TestGame game = new TestGame();
        runner = HeadlessRunner.start(game, b -> {
            b.files().useClasspathAssets(true);
            put(b, "test/textures/sheet.png", "png");
            put(b, "test/textures/sky.png", "png");
            prepare.accept(b);
        });
        for (int i = 0; i < 20 && !runner.engine().isRunning(); i++) {
            runner.step(1);
        }
        return game;
    }

    private World active(TestGame game, WorldSettings settings) {
        World world = game.worlds().create("main", settings);
        game.worlds().switchTo("main");
        runner.step(1);
        return world;
    }

    @Test
    void tilesLayersStatesAndEvents() {
        TestGame game = start(b -> {});
        World world = active(game, WorldSettings.DEFAULT);
        TileMap map = world.tileMap();
        List<String> changes = new ArrayList<>();
        game.on(TileChangeEvent.class, e -> {
            changes.add(e.x() + "," + e.y() + ":"
                    + (e.to() == null ? "empty" : e.to().key().path()));
            e.setCancelled(e.from() == WALL);
        });
        assertThat(map.setTile("ground", 1, 2, GRASS)).isTrue();
        assertThat(map.tile("ground", 1, 2)).isEqualTo(GRASS);
        assertThat(map.tile("ground", 0, 0)).isNull();
        assertThat(map.tile("missing", 0, 0)).isNull();
        map.fill("walls", -40, -3, 3, 2, WALL);
        assertThat(map.setTile("walls", -40, -3, GRASS)).as("cancelled").isFalse();
        assertThat(map.tile("walls", -39, -2)).isEqualTo(WALL);
        TileLayer ground = map.layer("ground");
        assertThat(ground.get(1, 2)).isEqualTo(GRASS);
        assertThat(ground.set(1, 2, null)).isTrue();
        assertThat(map.layers()).extracting(TileLayer::name).containsExactly("ground", "walls");
        assertThat(map.findLayer("nope")).isNull();
        assertThatThrownBy(() -> map.layer("nope")).isInstanceOf(IllegalArgumentException.class);
        TileLayer top = map.addLayer("top", -5);
        assertThat(map.addLayer("top", 99)).isSameAs(top);
        assertThat(map.layers().get(0)).isSameAs(top);
        top.setVisible(false);
        top.setCollision(false);
        top.setParallax(new Vec2(0.5f, 1));
        top.setTint(Color.RED);
        top.setRenderLayer("foreground");
        assertThat(top.isVisible() || top.isCollision()).isFalse();
        assertThat(top.parallax()).isEqualTo(new Vec2(0.5f, 1));
        assertThat(top.tint()).isEqualTo(Color.RED);
        assertThat(top.renderLayer()).isEqualTo("foreground");
        assertThat(top.map()).isSameAs(map);
        assertThat(top.zOrder()).isEqualTo(-5);
        assertThat(top.toString()).contains("top");
        assertThat(map.bounds()).isEqualTo(new Rect(-40, -3, 42, 6));

        var state = map.state("walls", -40, -3);
        state.data().set(Key.of("test", "hp"), dev.gulp.api.data.DataType.INT, 5);
        assertThat(map.findState("walls", -40, -3)).isSameAs(state);
        assertThat(state.x() + state.y()).isEqualTo(-43);
        assertThat(state.layer().name()).isEqualTo("walls");
        map.clear("walls");
        assertThat(map.tile("walls", -39, -2))
                .as("walls are protected by the listener")
                .isEqualTo(WALL);
        assertThat(map.findState("nope", 0, 0)).isNull();
        map.clear("nope");
        assertThat(changes).contains("1,2:grass", "-40,-3:wall", "-40,-3:grass", "1,2:empty");
        assertThat(WALL.properties()).containsEntry("sound", "stone");
        assertThat(WALL.friction()).isEqualTo(0.5f);
    }

    @Test
    void terrainsPickEdgesAndTilesTickAndInteract() {
        TestGame game = start(b -> {});
        World world = active(game, WorldSettings.DEFAULT);
        TileMap map = world.tileMap();
        List<TileType> sixteen = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            sixteen.add(TileType.builder(Key.of("test", "sand_" + i))
                    .tileSet(SHEET, i)
                    .build());
        }
        Terrain sand = Terrain.sixteen(Key.of("test", "sand"), sixteen);
        map.paintTerrain("ground", 0, 0, 3, 3, sand);
        assertThat(map.tile("ground", 1, 1)).isEqualTo(sixteen.get(15));
        assertThat(map.tile("ground", 0, 0)).isEqualTo(sixteen.get(2 | 4));
        map.setTerrain("ground", 1, 1, null);
        assertThat(map.tile("ground", 1, 0)).isEqualTo(sixteen.get(2 | 8));

        List<TileType> blobTiles = new ArrayList<>();
        for (int i = 0; i < 47; i++) {
            blobTiles.add(TileType.builder(Key.of("test", "rock_" + i))
                    .tileSet(SHEET, i)
                    .build());
        }
        Terrain rock = Terrain.blob(Key.of("test", "rock"), blobTiles);
        map.setTerrain("cave", 10, 10, rock);
        assertThat(map.tile("cave", 10, 10)).isEqualTo(blobTiles.get(Terrain.blobIndex(0)));
        map.setTerrain("cave", 11, 10, rock);
        assertThat(map.tile("cave", 10, 10)).isEqualTo(blobTiles.get(Terrain.blobIndex(4)));

        int[] grown = {0};
        int[] pulses = {0};
        List<String> touched = new ArrayList<>();
        TileType seed = TileType.builder(Key.of("test", "seed"))
                .tickRandomly(1f, (w, layer, x, y) -> {
                    grown[0]++;
                    layer.set(x, y, GRASS);
                })
                .build();
        TileType pulse = TileType.builder(Key.of("test", "pulse"))
                .tickEvery(2, (w, layer, x, y) -> pulses[0]++)
                .onInteract((who, layer, x, y) -> touched.add(layer.name() + x))
                .stateful(true)
                .build();
        map.setTile("plants", 0, 0, seed);
        map.setTile("plants", 5, 5, pulse);
        assertThat(map.findState("plants", 5, 5)).isNotNull();
        long before = world.ticks();
        runner.step(8);
        long ran = world.ticks() - before;
        assertThat(grown[0]).isEqualTo(1);
        assertThat(map.tile("plants", 0, 0)).isEqualTo(GRASS);
        assertThat(pulses[0]).isBetween((int) (ran / 2), (int) (ran / 2 + 1));
        assertThat(map.interact("plants", 5, 5, null)).isTrue();
        assertThat(map.interact("plants", 0, 0, null)).isFalse();
        assertThat(map.interact("none", 0, 0, null)).isFalse();
        assertThat(touched).containsExactly("plants5");
    }

    @Test
    void orientationsConvertBothWays() {
        TestGame game = start(b -> {});
        for (TileOrientation orientation : TileOrientation.values()) {
            World world = game.worlds().create("w_" + orientation, WorldSettings.DEFAULT.orientation(orientation));
            TileMap map = world.tileMap();
            assertThat(map.orientation()).isEqualTo(orientation);
            for (int y = -3; y <= 3; y++) {
                for (int x = -3; x <= 3; x++) {
                    Vec2 center = map.tileToWorld(x, y);
                    assertThat(map.worldToTile(center))
                            .as(orientation + " " + x + "," + y)
                            .isEqualTo(new GridPos(x, y));
                }
            }
        }
    }

    @Test
    void generatedChunksStreamAroundTheCamera() {
        TestGame game = start(b -> {});
        List<String> events = new ArrayList<>();
        game.on(ChunkLoadEvent.class, e -> events.add("load " + e.generated()));
        game.on(ChunkUnloadEvent.class, e -> events.add("unload"));
        game.worlds()
                .load(
                        "gen",
                        WorldSource.generator((data, cx, cy, rng) -> {
                                    for (int y = 0; y < Chunk.SIZE; y++) {
                                        for (int x = 0; x < Chunk.SIZE; x++) {
                                            data.setTile("ground", x, y, (x + y) % 2 == 0 ? GRASS : WATER);
                                        }
                                    }
                                    if (cx == 0 && cy == 0) {
                                        data.spawn(TREE, 1.5f, 1.5f);
                                        assertThat(data.tile("ground", 0, 0)).isEqualTo(GRASS);
                                        assertThat(data.tile("none", 0, 0)).isNull();
                                        assertThatThrownBy(() -> data.setTile("ground", 40, 0, GRASS))
                                                .isInstanceOf(IndexOutOfBoundsException.class);
                                    }
                                    if (cx == 5) {
                                        throw new IllegalStateException("broken chunk");
                                    }
                                })
                                .settings(WorldSettings.DEFAULT.seed(7)));
        game.worlds().switchTo("gen");
        runner.step(10);
        World world = game.worlds().active();
        assertThat(world.name()).isEqualTo("gen");
        assertThat(world.loadedChunks()).isNotEmpty();
        Chunk origin = world.chunk(0, 0);
        assertThat(origin).isNotNull();
        assertThat(origin.isLoaded()).isTrue();
        assertThat(origin.isModified()).isFalse();
        assertThat(origin.world()).isSameAs(world);
        assertThat(origin.x() + origin.y()).isZero();
        assertThat(world.tileMap().tile("ground", 0, 0)).isEqualTo(GRASS);
        assertThat(world.query().type(TREE).count()).isEqualTo(1);
        world.tileMap().setTile("ground", 0, 0, WALL);
        assertThat(origin.isModified()).isTrue();

        Subscription ticket = world.keepLoaded(new Rect(200, 0, 4, 4), game);
        world.camera().setPosition(new Vec2(5000, 5000));
        runner.step(160);
        assertThat(world.chunk(0, 0)).isNull();
        assertThat(world.chunk(6, 0)).isNotNull();
        assertThat(world.query().type(TREE).count()).isZero();
        assertThat(ticket.isActive()).isTrue();
        ticket.cancel();
        world.camera().setPosition(Vec2.ZERO);
        runner.step(10);
        assertThat(world.tileMap().tile("ground", 0, 0))
                .as("modified chunks come back")
                .isEqualTo(WALL);
        assertThat(events).contains("load true", "unload", "load false");
        assertThat(world.chunk(0, 0).toString()).contains("modified");
    }

    @Test
    void worldsLoadSwitchWithTransitionsAndUnload() {
        TestGame game = start(b -> {});
        List<String> events = new ArrayList<>();
        game.on(WorldLoadEvent.class, e -> events.add("load " + e.world().name()));
        game.on(
                WorldSwitchEvent.class,
                e -> events.add("switch " + (e.from() == null ? "-" : e.from().name()) + ">" + e.to().name()));
        game.on(WorldUnloadEvent.class, e -> events.add("unload " + e.world().name()));
        World first = game.worlds().create("first", WorldSettings.DEFAULT);
        assertThatThrownBy(() -> game.worlds().create("first", WorldSettings.DEFAULT))
                .isInstanceOf(IllegalArgumentException.class);
        game.worlds().register("second", WorldSource.empty().onLoad(w -> events.add("onLoad " + w.name())));
        game.worlds().switchTo("first");
        runner.step(1);
        assertThat(first.isActive()).isTrue();
        assertThat(game.display().camera()).isSameAs(first.camera());
        List<World> switched = new ArrayList<>();
        game.worlds().switchTo("second", Transitions.fade(0.1f)).thenSync(switched::add);
        assertThat(game.worlds().isTransitioning()).isTrue();
        assertThat(game.worlds().switchTo("first", Transitions.fade(1f)).isFailed())
                .isTrue();
        runner.step(4);
        assertThat(game.worlds().active()).isSameAs(first);
        runner.step(4);
        assertThat(game.worlds().active().name()).isEqualTo("second");
        assertThat(switched).hasSize(1);
        runner.step(10);
        assertThat(game.worlds().isTransitioning()).isFalse();
        for (var transition : List.of(
                Transitions.slide(Vec2.LEFT, 0.05f),
                Transitions.circleWipe(0.05f),
                Transitions.pixelate(0.05f),
                Transitions.fadeColor(Color.WHITE, 0f))) {
            String target = game.worlds().active() == first ? "second" : "first";
            game.worlds().switchTo(target, transition);
            runner.step(12);
            assertThat(game.worlds().active().name()).isEqualTo(target);
            assertThat(transition.toString()).contains("gulp:");
        }
        assertThatThrownBy(() -> Transitions.fade(-1f)).isInstanceOf(IllegalArgumentException.class);
        boolean[] ghostFailed = {false};
        game.worlds().switchTo("ghost").onFailure(e -> ghostFailed[0] = true);
        runner.step(1);
        assertThat(ghostFailed[0]).isTrue();
        game.worlds().switchTo("first");
        runner.step(1);
        assertThatThrownBy(() -> game.worlds().unload("first")).isInstanceOf(IllegalStateException.class);
        game.worlds().unload("second");
        game.worlds().unload("nothing");
        assertThat(game.worlds().get("second")).isNull();
        assertThat(game.worlds().all()).containsExactly(first);
        assertThat(game.worlds().load("first", WorldSource.empty()).isDone()).isTrue();
        assertThat(events)
                .containsSubsequence(
                        "load first",
                        "switch ->first",
                        "onLoad second",
                        "load second",
                        "switch first>second",
                        "unload second");
        assertThat(game.registries()
                        .get(dev.gulp.api.registry.Registries.TRANSITION)
                        .keys())
                .extracting(Key::path)
                .contains("fade", "slide", "circle_wipe", "pixelate");
    }

    @Test
    void camerasFollowShakeAnimateAndSplitTheScreen() {
        TestGame game = start(b -> {});
        World world = active(game, WorldSettings.DEFAULT);
        Entity target = world.spawn(TREE, 10, 0);
        Camera camera = world.camera()
                .follow(target)
                .smoothing(0f)
                .deadZone(2f, 2f)
                .lookAhead(0f)
                .offset(Vec2.ZERO)
                .limits(null);
        assertThat(camera.target()).isSameAs(target);
        assertThat(camera.position()).isEqualTo(new Vec2(10, 0));
        target.setPosition(10.5f, 0);
        runner.step(2);
        assertThat(camera.position().x()).as("inside the dead zone").isEqualTo(10f);
        target.setPosition(20f, 0);
        runner.step(2);
        assertThat(camera.position().x()).isEqualTo(19f);
        camera.smoothing(0.5f);
        target.setPosition(30f, 0);
        runner.step(2);
        assertThat(camera.position().x()).isBetween(19.5f, 28f);
        camera.limits(new Rect(0, 0, 25, 10));
        runner.step(2);
        assertThat(camera.position().x()).isLessThanOrEqualTo(25f);
        camera.limits(null).follow(null);
        camera.shake(0.8f, 0.2f);
        camera.shake(1f, 0.2f);
        runner.step(2);
        assertThat(((dev.gulp.core.graphics.CameraImpl) camera).trauma()).isBetween(0f, 1f);
        runner.step(30);
        assertThat(((dev.gulp.core.graphics.CameraImpl) camera).trauma()).isZero();
        camera.zoomTo(2f, 0.1f, Ease.OUT_QUAD);
        camera.panTo(new Vec2(1, 1), 0.1f, Ease.IN_OUT_SINE);
        runner.step(10);
        assertThat(camera.zoom()).isEqualTo(2f);
        assertThat(camera.position().x()).isCloseTo(1f, within(1e-4f));
        camera.zoomTo(3f, 0f, Ease.LINEAR);
        camera.panTo(Vec2.ZERO, 0f, Ease.LINEAR);
        assertThat(camera.zoom()).isEqualTo(3f);
        assertThatThrownBy(() -> camera.zoomTo(0f, 1f, Ease.LINEAR)).isInstanceOf(IllegalArgumentException.class);
        Camera minimap = world.addCamera(new Rect(0.75f, 0f, 0.25f, 0.25f));
        assertThat(world.cameras()).hasSize(2);
        assertThat(minimap.viewport()).isEqualTo(new Rect(0.75f, 0f, 0.25f, 0.25f));
        assertThatThrownBy(() -> minimap.setViewport(new Rect(0, 0, 0, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        world.spawn(TREE, 0, 0, e -> e.add(new WorldText("Tree")));
        world.parallax()
                .layer(AssetKey.texture("test:textures/sky"), 0.2f)
                .repeatX()
                .repeatY()
                .autoScroll(1, 0)
                .offset(1, 2)
                .scale(2f)
                .tint(Color.WHITE);
        world.parallax()
                .layer(AssetKey.texture("test:textures/sky"), 0.5f)
                .renderLayer("foreground")
                .factor(0.5f, 0f);
        world.tileMap().fill("ground", -5, -5, 10, 10, GRASS);
        world.tileMap().setTile("ground", 0, 0, WATER);
        world.addLayer("custom", 250);
        assertThat(world.addLayer("custom", 1)).isSameAs(world.layers().get(3));
        runner.step(3);
        assertThat(game.display().stats().drawCalls()).isGreaterThan(2);
        assertThat(world.parallax().layers()).hasSize(2);
        world.parallax().layers().get(1).remove();
        world.parallax().clear();
        world.removeCamera(minimap);
        assertThatThrownBy(() -> world.removeCamera(camera)).isInstanceOf(IllegalArgumentException.class);
        assertThat(world.rng()).isNotNull();
        assertThat(world.data()).isNotNull();
        assertThat(world.location(1, 2).toVec2()).isEqualTo(new Vec2(1, 2));
        assertThat(world.playSound(
                                Vec2.ZERO,
                                dev.gulp.api.audio.Sound.builder(Key.of("test", "s"))
                                        .file(AssetKey.audio("test:sounds/none"))
                                        .build())
                        .isPlaying())
                .isFalse();
        assertThat(world.toString()).contains("main");
    }

    static final String TILED = """
            {"orientation": "orthogonal", "tilewidth": 16, "tileheight": 16, "width": 3, "height": 2,
             "tilesets": [
               {"firstgid": 1, "name": "Sheet", "image": "../textures/sheet.png", "tilewidth": 16, "tileheight": 16,
                "tilecount": 4, "columns": 2,
                "tiles": [{"id": 1, "type": "Wall", "properties": [{"name": "shape", "value": "full"}]},
                          {"id": 2, "animation": [{"tileid": 2, "duration": 100}, {"tileid": 3, "duration": 100}]}]},
               {"firstgid": 5, "source": "extra.tsj"}],
             "layers": [
               {"type": "tilelayer", "name": "Ground", "width": 3, "height": 2, "data": [1, 2, 3, 2147483649, 5, 0],
                "properties": [{"name": "renderLayer", "value": "tiles"}, {"name": "collision", "value": "true"}]},
               {"type": "group", "name": "Group", "layers": [
                 {"type": "tilelayer", "name": "Encoded", "width": 2, "height": 1, "encoding": "base64",
                  "data": "AQAAAAIAAAA="}]},
               {"type": "tilelayer", "name": "Infinite", "chunks": [
                 {"x": 16, "y": 0, "width": 2, "height": 1, "data": [2, 1]}]},
               {"type": "objectgroup", "name": "Objects", "objects": [
                 {"name": "hero", "type": "Tree", "x": 16, "y": 16, "width": 16, "height": 16,
                  "properties": [{"name": "hp", "value": 5}]},
                 {"name": "", "class": "Door", "x": 32, "y": 32, "width": 16, "height": 16, "gid": 1},
                 {"name": "nothing", "type": "Unknown", "x": 0, "y": 0}]},
               {"type": "imagelayer", "name": "Sky", "image": "../textures/sky.png", "parallaxx": 0.3,
                "repeatx": true, "repeaty": true},
               {"type": "mystery", "name": "Odd"}]}
            """;

    static final String TILESET = """
            {"name": "Extra", "image": "../textures/sheet.png", "tilewidth": 16, "tileheight": 16, "tilecount": 1}
            """;

    @Test
    void tiledMapsLoadTilesObjectsAndImages() {
        TestGame game = start(b -> {
            put(b, "test/maps/level.tmj", TILED);
            put(b, "test/maps/extra.tsj", TILESET);
        });
        List<String> spawned = new ArrayList<>();
        World[] loaded = new World[1];
        game.worlds()
                .load(
                        "tiled",
                        WorldSource.tiled(AssetKey.text("test:maps/level"))
                                .spawn("Tree", TREE)
                                .tile("Wall", WALL)
                                .spawner((world, object) -> {
                                    spawned.add(
                                            object.type() + " " + object.layer() + " " + object.x() + "," + object.y());
                                    return object.type().equals("Door")
                                            ? world.spawn(TREE, object.x(), object.y())
                                            : null;
                                }))
                .thenSync(world -> loaded[0] = world);
        runner.step(10);
        World world = loaded[0];
        assertThat(world).isNotNull();
        TileMap map = world.tileMap();
        assertThat(map.tileWidth()).isEqualTo(16);
        assertThat(map.tile("Ground", 1, 0).shape()).isEqualTo(TileShape.FULL);
        assertThat(map.tile("Ground", 1, 0).friction()).isEqualTo(0.5f);
        assertThat(map.tile("Ground", 2, 0).frameCount()).isEqualTo(2);
        assertThat(map.tile("Ground", 0, 1)).as("flipped copy of tile 1").isEqualTo(map.tile("Ground", 0, 0));
        assertThat(map.tile("Ground", 1, 1)).isNotNull();
        assertThat(map.tile("Encoded", 0, 0)).isEqualTo(map.tile("Ground", 0, 0));
        assertThat(map.tile("Infinite", 17, 0)).isEqualTo(map.tile("Ground", 0, 0));
        Entity hero = world.entity("hero");
        assertThat(hero).isNotNull();
        assertThat(hero.position()).isEqualTo(new Vec2(1.5f, 1.5f));
        assertThat(hero.data().get(Key.of("test", "hp"), dev.gulp.api.data.DataType.STRING))
                .isEqualTo("5");
        assertThat(spawned).containsExactly("Door Objects 2.5,1.5", "Unknown Objects 0.0,0.0");
        assertThat(world.parallax().layers()).hasSize(1);
        game.worlds().switchTo("tiled");
        runner.step(2);
        assertThat(game.display().stats().drawCalls()).isPositive();
    }

    static final String TMX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!-- Written like Tiled 1.11 does. -->
            <map version="1.10" orientation="orthogonal" width="2" height="1" tilewidth="16" tileheight="16" infinite="0">
             <properties><property name="music" value="calm"/></properties>
             <tileset firstgid="1" name="Sheet" tilewidth="16" tileheight="16" tilecount="4" columns="2">
              <image source="../textures/sheet.png" width="32" height="32"/>
              <tile id="1" type="Wall"><properties><property name="shape" value="full"/></properties></tile>
              <tile id="2"><animation><frame tileid="2" duration="100"/><frame tileid="3" duration="100"/></animation></tile>
             </tileset>
             <tileset firstgid="5" source="extra.tsx"/>
             <layer id="1" name="Csv" width="2" height="1">
              <properties><property name="renderLayer" value="tiles"/></properties>
              <data encoding="csv">
            3,2147483649
            </data>
             </layer>
             <layer id="2" name="Xml" width="2" height="1"><data><tile gid="5"/><tile/></data></layer>
             <group name="Packed" opacity="0.5">
              <layer name="Zlib" width="2" height="1"><data encoding="base64" compression="zlib">
               eJxjZGBgYGJgYAAAABgABA==
              </data></layer>
              <layer name="Gzip" width="2" height="1" visible="0"><data encoding="base64" compression="gzip">H4sIAAAAAAAC/2NkYGBgYmBgAAB8F4EDCAAAAA==</data></layer>
              <layer name="Zstd" width="2" height="1"><data encoding="base64" compression="zstd">KLUv/SAIQQAAAQAAAAIAAAA=</data></layer>
             </group>
             <layer name="Infinite" width="2" height="1">
              <data encoding="csv"><chunk x="16" y="0" width="2" height="1">2,1</chunk></data>
             </layer>
             <objectgroup name="Objects">
              <object id="1" name="hero" type="Tree" x="16" y="16" width="16" height="16">
               <properties>
                <property name="hp" type="int" value="5"/>
                <property name="note">Tom &amp; Jerry
            <![CDATA[<b>two</b>]]></property>
               </properties>
              </object>
              <object id="2" class="Door" x="32" y="32" width="16" height="16" gid="1"/>
              <object id="3" name="spot" type="Unknown" x="8" y="8"><point/></object>
             </objectgroup>
             <imagelayer name="Sky" parallaxx="0.3" repeatx="1"><image source="../textures/sky.png" width="8" height="8"/></imagelayer>
            </map>
            """;

    static final String TSX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tileset version="1.10" name="Extra" tilewidth="16" tileheight="16" tilecount="1" columns="1">
             <image source="../textures/sheet.png" width="16" height="16"/>
            </tileset>
            """;

    @Test
    void tiledXmlMapsLoadEveryEncodingAndCompression() {
        TestGame game = start(b -> {
            put(b, "test/maps/level.tmx", TMX);
            put(b, "test/maps/extra.tsx", TSX);
        });
        List<String> spawned = new ArrayList<>();
        World[] loaded = new World[1];
        game.worlds()
                .load(
                        "tmx",
                        WorldSource.tiled(AssetKey.text("test:maps/level"))
                                .spawn("Tree", TREE)
                                .tile("Wall", WALL)
                                .spawner((world, object) -> {
                                    spawned.add(object.type() + " " + object.x() + "," + object.y());
                                    return null;
                                }))
                .thenSync(world -> loaded[0] = world);
        runner.step(10);
        World world = loaded[0];
        assertThat(world).isNotNull();
        TileMap map = world.tileMap();
        TileType first = map.tile("Zlib", 0, 0);
        TileType wall = map.tile("Zlib", 1, 0);
        assertThat(first).isNotNull();
        assertThat(wall.shape()).isEqualTo(TileShape.FULL);
        assertThat(map.tile("Gzip", 0, 0)).isEqualTo(first);
        assertThat(map.tile("Gzip", 1, 0)).isEqualTo(wall);
        assertThat(map.findLayer("Gzip").isVisible()).isFalse();
        assertThat(map.findLayer("Zlib").tint().a()).isEqualTo(0.5f);
        assertThat(map.tile("Zstd", 0, 0)).isEqualTo(first);
        assertThat(map.tile("Zstd", 1, 0)).isEqualTo(wall);
        assertThat(map.tile("Csv", 0, 0).frameCount()).isEqualTo(2);
        assertThat(map.tile("Csv", 1, 0)).as("flipped tile 1").isEqualTo(first);
        assertThat(map.tile("Xml", 0, 0)).as("tile from the external .tsx").isNotNull();
        assertThat(map.tile("Xml", 1, 0)).isNull();
        assertThat(map.tile("Infinite", 17, 0)).isEqualTo(first);
        Entity hero = world.entity("hero");
        assertThat(hero).isNotNull();
        assertThat(hero.position()).isEqualTo(new Vec2(1.5f, 1.5f));
        assertThat(hero.data().get(Key.of("test", "note"), dev.gulp.api.data.DataType.STRING))
                .isEqualTo("Tom & Jerry\n<b>two</b>");
        assertThat(spawned).containsExactly("Door 2.5,1.5", "Unknown 0.5,0.5");
        assertThat(world.parallax().layers()).hasSize(1);
    }

    static final String LDTK = """
            {"defaultGridSize": 8,
             "defs": {
               "tilesets": [{"uid": 1, "relPath": "../textures/sheet.png", "tileGridSize": 8, "padding": 0, "spacing": 0},
                            {"uid": 2, "relPath": null}],
               "layers": [{"uid": 10, "intGridValues": [{"value": 1, "identifier": "wall"}, {"value": 2, "identifier": ""}]}]},
             "levels": [
               {"identifier": "Level_0", "worldX": 0, "worldY": 0, "layerInstances": [
                 {"__identifier": "Entities", "__type": "Entities", "__gridSize": 8, "__cWid": 4, "entityInstances": [
                   {"__identifier": "Tree", "px": [12, 16], "width": 8, "height": 16, "__pivot": [0.5, 1],
                    "fieldInstances": [{"__identifier": "Name", "__value": "oak"}, {"__identifier": "hp", "__value": 3}]}]},
                 {"__identifier": "Collisions", "__type": "IntGrid", "__gridSize": 8, "__cWid": 2, "layerDefUid": 10,
                  "intGridCsv": [1, 0, 2, 1], "__tilesetDefUid": 1,
                  "autoLayerTiles": [{"px": [0, 0], "t": 1, "f": 0}, {"px": [0, 0], "t": 2, "f": 1},
                                     {"px": [8, 8], "t": 3, "f": 3}]},
                 {"__identifier": "Decor", "__type": "Tiles", "__gridSize": 8, "__cWid": 2, "__tilesetDefUid": 1,
                  "__opacity": 0.5, "gridTiles": [{"px": [8, 0], "t": 0, "f": 0}]},
                 {"__identifier": "Unknown", "__type": "Tiles", "__gridSize": 8, "__cWid": 2, "__tilesetDefUid": 7,
                  "gridTiles": [{"px": [0, 0], "t": 0}]}]},
               {"identifier": "Level_1", "worldX": 32, "worldY": 0, "externalRelPath": "level_1.ldtkl"}]}
            """;

    static final String LDTK_LEVEL = """
            {"identifier": "Level_1", "worldX": 32, "worldY": 0, "layerInstances": [
              {"__identifier": "Decor", "__type": "Tiles", "__gridSize": 8, "__cWid": 1, "__tilesetDefUid": 1,
               "gridTiles": [{"px": [0, 0], "t": 5}]}]}
            """;

    @Test
    void ldtkProjectsLoadLevelsLayersAndEntities() {
        TestGame game = start(b -> {
            put(b, "test/maps/game.ldtk", LDTK);
            put(b, "test/maps/level_1.ldtkl", LDTK_LEVEL);
        });
        World[] all = new World[2];
        game.worlds()
                .load(
                        "all",
                        WorldSource.ldtk(AssetKey.text("test:maps/game"))
                                .spawn("Tree", TREE)
                                .tile("wall", WALL))
                .thenSync(w -> all[0] = w);
        game.worlds()
                .load("one", WorldSource.ldtk(AssetKey.text("test:maps/game"), "Level_0"))
                .thenSync(w -> all[1] = w);
        boolean[] missingFailed = {false};
        game.worlds()
                .load("missing", WorldSource.ldtk(AssetKey.text("test:maps/game"), "Nope"))
                .onFailure(e -> missingFailed[0] = true);
        runner.step(10);
        World world = all[0];
        assertThat(world).isNotNull();
        TileMap map = world.tileMap();
        assertThat(map.tileWidth()).isEqualTo(8);
        assertThat(map.tile("Collisions.grid", 0, 0)).isEqualTo(WALL);
        assertThat(map.tile("Collisions.grid", 0, 1).shape()).isEqualTo(TileShape.FULL);
        assertThat(map.tile("Collisions.grid", 1, 0)).isNull();
        assertThat(map.findLayer("Collisions.grid").isVisible()).isFalse();
        assertThat(map.tile("Collisions", 0, 0)).isNotNull();
        assertThat(map.tile("Collisions#2", 0, 0)).as("stacked auto tile").isNotNull();
        assertThat(map.tile("Decor", 1, 0)).isNotNull();
        assertThat(map.findLayer("Decor").tint().a()).isEqualTo(0.5f);
        assertThat(map.tile("Decor", 4, 0))
                .as("external level placed at worldX / grid")
                .isNotNull();
        Entity oak = world.entity("oak");
        assertThat(oak).isNotNull();
        assertThat(oak.position()).isEqualTo(new Vec2(1.5f, 1f));
        assertThat(all[1].tileMap().tile("Decor", 4, 0)).isNull();
        assertThat(missingFailed[0]).isTrue();
        assertThat(world.entitiesNear(new Vec2(1.5f, 1f), 1f)).hasSize(1);
    }

    @Test
    void mapObjectsCarryTheirPoints() {
        TestGame game = start(b -> {
            put(b, "test/maps/rails.tmj", """
                    {"tilewidth": 16, "tileheight": 16, "tilesets": [], "layers": [
                      {"type": "objectgroup", "name": "Paths", "objects": [
                        {"name": "rail", "type": "Rail", "x": 16, "y": 0, "polyline": [{"x": 0, "y": 0}, {"x": 32, "y": 16}]},
                        {"name": "loop", "type": "Rail", "x": 0, "y": 0, "polygon": [{"x": 0, "y": 0}, {"x": 16, "y": 0},
                         {"x": 16, "y": 16}]}]}]}
                    """);
            put(b, "test/maps/rails_xml.tmx", """
                    <map tilewidth="16" tileheight="16"><objectgroup name="Paths">
                     <object id="1" name="rail" type="Rail" x="16" y="0"><polyline points="0,0 32,16"/></object>
                    </objectgroup></map>
                    """);
            put(b, "test/maps/route.ldtk", """
                    {"defaultGridSize": 16, "defs": {"tilesets": [], "layers": []}, "levels": [
                      {"identifier": "Level_0", "worldX": 0, "worldY": 0, "layerInstances": [
                        {"__identifier": "Entities", "__type": "Entities", "__gridSize": 16, "__cWid": 8, "entityInstances": [
                          {"__identifier": "Guard", "px": [8, 8], "width": 16, "height": 16, "__pivot": [0.5, 0.5],
                           "fieldInstances": [{"__identifier": "route", "__value": [{"cx": 1, "cy": 2}, {"cx": 3, "cy": 2}]},
                                              {"__identifier": "home", "__value": {"cx": 0, "cy": 0}}]}]}]}]}
                    """);
        });
        List<dev.gulp.api.world.MapObject> objects = new ArrayList<>();
        for (String map : List.of("test:maps/rails", "test:maps/rails_xml")) {
            game.worlds().load(map, WorldSource.tiled(AssetKey.text(map)).spawner((world, object) -> {
                objects.add(object);
                return null;
            }));
        }
        game.worlds()
                .load(
                        "route",
                        WorldSource.ldtk(AssetKey.text("test:maps/route")).spawner((world, object) -> {
                            objects.add(object);
                            return null;
                        }));
        runner.step(10);
        assertThat(objects).hasSize(4);
        assertThat(objects.get(0).points()).containsExactly(new Vec2(1f, 0f), new Vec2(3f, 1f));
        assertThat(objects.get(1).points()).as("polygons close").hasSize(4).endsWith(new Vec2(0f, 0f));
        assertThat(objects.get(2).points()).containsExactly(new Vec2(1f, 0f), new Vec2(3f, 1f));
        assertThat(objects.get(3).points())
                .containsExactly(new Vec2(1.5f, 2.5f), new Vec2(3.5f, 2.5f), new Vec2(0.5f, 0.5f));
    }

    @Test
    void mapFilesMustHaveLowerCaseNames() {
        TestGame game =
                start(b -> put(b, "test/maps/bad.tmj", "{\"tilesets\": [{\"firstgid\": 1, \"source\": \"Bad.tsj\"}]}"));
        boolean[] failed = {false};
        game.worlds()
                .load("bad", WorldSource.tiled(AssetKey.text("test:maps/bad")))
                .onFailure(e -> failed[0] = true);
        runner.step(5);
        assertThat(failed[0]).isTrue();
    }
}
