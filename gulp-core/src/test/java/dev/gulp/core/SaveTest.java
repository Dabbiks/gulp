package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.data.DataType;
import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.ComponentInfo;
import dev.gulp.api.entity.ComponentType;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.entity.Save;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.save.GameLoadEvent;
import dev.gulp.api.save.GameSaveEvent;
import dev.gulp.api.save.SaveMetadata;
import dev.gulp.api.save.SaveSlot;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.api.world.TileType;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.BaseModule;
import dev.gulp.core.Fixtures.TestGame;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SaveTest {

    /** A component with saved fields of several kinds. */
    @ComponentInfo(key = "test:purse")
    static final class Purse extends Component {
        @Save
        int coins;

        @Save
        String owner = "";

        @Save
        Vec2 home = Vec2.ZERO;

        @Save
        @Nullable String note;

        @Save
        List<String> items = List.of();

        int notSaved;
    }

    /** A component that is never saved. */
    @ComponentInfo(key = "test:scratch", persistent = false)
    static final class Scratch extends Component {
        @Save
        int value;
    }

    static final Key COINS = Key.of("test", "coins");
    static final EntityType HOLDER = EntityType.builder(Key.of("test", "holder"))
            .component(Purse::new)
            .component(Scratch::new)
            .build();
    static final EntityType GHOST =
            EntityType.builder(Key.of("test", "ghost")).persistent(false).build();
    static final TileType STONE = TileType.builder(Key.of("test", "stone")).build();
    static final TileType GRASS = TileType.builder(Key.of("test", "grass")).build();

    private @Nullable HeadlessRunner runner;
    private BaseModule module = new BaseModule();

    @AfterEach
    void tearDown() {
        if (runner != null) {
            runner.stop();
            runner = null;
        }
    }

    private TestGame start(int version) {
        TestGame game = new TestGame();
        module = new BaseModule();
        game.modules(module);
        Runnable previous = game.onLoad;
        game.onLoad = () -> {
            previous.run();
            game.registries().register(Registries.ENTITY_TYPE, HOLDER);
            game.registries().register(Registries.ENTITY_TYPE, GHOST);
            game.registries().register(Registries.TILE_TYPE, STONE);
            game.registries().register(Registries.TILE_TYPE, GRASS);
        };
        var configure = game.configure;
        game.configure = s -> {
            configure.accept(s);
            s.saveVersion(version);
        };
        runner = Fixtures.started(game);
        return game;
    }

    private <T> T await(Promise<T> promise) {
        List<T> result = new ArrayList<>();
        List<Throwable> failure = new ArrayList<>();
        promise.thenSync(result::add).onFailure(failure::add);
        for (int i = 0; i < 20 && result.isEmpty() && failure.isEmpty(); i++) {
            runner.step(1);
        }
        if (!failure.isEmpty()) {
            throw new AssertionError("Promise failed", failure.getFirst());
        }
        assertThat(result).as("promise completed").hasSize(1);
        return result.getFirst();
    }

    private Throwable failure(Promise<?> promise) {
        List<Throwable> failure = new ArrayList<>();
        promise.onFailure(failure::add);
        for (int i = 0; i < 20 && failure.isEmpty(); i++) {
            runner.step(1);
        }
        assertThat(failure).as("promise failed").hasSize(1);
        return failure.getFirst();
    }

    @Test
    void componentTypesComeFromTheProcessor() {
        TestGame game = start(1);
        ComponentType purse = game.registries().get(Registries.COMPONENT_TYPE).get(Key.of("test", "purse"));
        assertThat(purse).isNotNull();
        assertThat(purse.type()).isEqualTo(Purse.class);
        assertThat(purse.persistent()).isTrue();
        ComponentType scratch = game.registries().get(Registries.COMPONENT_TYPE).get(Key.of("test", "scratch"));
        assertThat(scratch.persistent()).isFalse();
    }

    @Test
    void aSlotRestoresWorldsEntitiesTilesAndData() {
        TestGame game = start(1);
        World world = game.worlds().create("arena", WorldSettings.DEFAULT.persistent(true));
        game.worlds().create("scratchpad", WorldSettings.DEFAULT);
        game.worlds().switchTo("arena");
        runner.step(1);

        Entity holder = world.spawn(HOLDER, 3, 4, e -> e.setName("banker"));
        holder.setRotation(45);
        holder.tags().add("rich");
        holder.data().set(COINS, DataType.INT, 7);
        Purse purse = holder.get(Purse.class);
        purse.coins = 12;
        purse.owner = "Ala";
        purse.home = new Vec2(1.5f, -2f);
        purse.note = null;
        purse.items = List.of("key", "map");
        purse.notSaved = 5;
        holder.get(Scratch.class).value = 9;
        UUID id = holder.id();
        world.spawn(GHOST, 0, 0);
        world.tileMap().setTile("ground", 1, 2, STONE);
        world.tileMap().setTile("ground", -40, 1100, GRASS);
        world.tileMap().state("ground", 1, 2).data().set(COINS, DataType.INT, 3);
        world.data().set(COINS, DataType.INT, 100);
        game.modules().data(module).set(COINS, DataType.INT, 55);
        game.on(GameSaveEvent.class, e -> e.data().set(COINS, DataType.INT, 1000));
        List<Integer> loaded = new ArrayList<>();
        game.on(GameLoadEvent.class, e -> {
            loaded.add(e.data().getOrDefault(COINS, DataType.INT, -1));
            loaded.add(e.savedVersion());
        });

        SaveSlot slot = game.saves().slot("slot1");
        Promise<Void> saving = slot.save("First save");
        assertThat(game.saves().isBusy()).isTrue();
        assertThat(failure(slot.save())).hasMessageContaining("already running");
        await(saving);
        assertThat(game.saves().isBusy()).isFalse();
        assertThat(await(slot.exists())).isTrue();
        assertThat(await(game.saves().slot("other").exists())).isFalse();
        assertThat(runner.backend().files().userData("saves/slot1/regions/arena/r.0.0.bin"))
                .isNotNull();
        assertThat(runner.backend().files().userData("saves/slot1/regions/arena/r.-1.1.bin"))
                .isNotNull();

        SaveMetadata meta = await(slot.metadata());
        assertThat(meta.slot()).isEqualTo("slot1");
        assertThat(meta.title()).isEqualTo("First save");
        assertThat(meta.version()).isEqualTo(1);
        assertThat(meta.thumbnail()).isNull();
        assertThat(meta.playTicks()).isPositive();
        assertThat(await(game.saves().list())).extracting(SaveMetadata::slot).containsExactly("slot1");

        // Change everything, then load.
        purse.coins = 0;
        holder.remove();
        world.spawn(HOLDER, 9, 9);
        world.tileMap().setTile("ground", 1, 2, null);
        world.data().set(COINS, DataType.INT, 0);
        game.modules().data(module).set(COINS, DataType.INT, 0);
        game.worlds().switchTo("scratchpad");
        runner.step(40);

        await(slot.load());
        World restored = game.worlds().get("arena");
        assertThat(game.worlds().active()).isSameAs(restored);
        assertThat(restored.entityCount()).as("only the saved holder").isEqualTo(1);
        Entity back = restored.entity("banker");
        assertThat(back).isNotNull();
        assertThat(back.id()).isEqualTo(id);
        assertThat(back.x()).isEqualTo(3f);
        assertThat(back.y()).isEqualTo(4f);
        assertThat(back.rotation()).isEqualTo(45f);
        assertThat(back.tags().has("rich")).isTrue();
        assertThat(back.data().get(COINS, DataType.INT)).isEqualTo(7);
        Purse again = back.get(Purse.class);
        assertThat(again.coins).isEqualTo(12);
        assertThat(again.owner).isEqualTo("Ala");
        assertThat(again.home).isEqualTo(new Vec2(1.5f, -2f));
        assertThat(again.note).isNull();
        assertThat(again.items).containsExactly("key", "map");
        assertThat(again.notSaved).isZero();
        assertThat(back.get(Scratch.class).value).as("not persistent").isZero();
        assertThat(restored.tileMap().tile("ground", 1, 2)).isEqualTo(STONE);
        assertThat(restored.tileMap().tile("ground", -40, 1100)).isEqualTo(GRASS);
        assertThat(restored.tileMap().state("ground", 1, 2).data().get(COINS, DataType.INT))
                .isEqualTo(3);
        assertThat(restored.data().get(COINS, DataType.INT)).isEqualTo(100);
        assertThat(game.modules().data(module).get(COINS, DataType.INT)).isEqualTo(55);
        assertThat(loaded).containsExactly(1000, 1);

        await(slot.delete());
        assertThat(await(slot.exists())).isFalse();
        assertThat(failure(slot.load())).isNotNull();
        assertThat(game.saves().isBusy()).isFalse();
    }

    @Test
    void exportsMoveBetweenGamesAndMigrationsUpgradeThem() {
        TestGame first = start(1);
        first.on(GameSaveEvent.class, e -> e.data().set(COINS, DataType.INT, 5));
        await(first.saves().slot("slot1").save());
        byte[] exported = await(first.saves().slot("slot1").exportData());
        await(first.saves().slot("slot1").exportFile());
        assertThat(runner.backend().files().offered("slot1.gulpsave")).isEqualTo(exported);
        assertThat(failure(first.saves().slot("empty").exportData())).hasMessageContaining("no save");
        assertThatThrownBy(() -> first.saves().migration(1, d -> {})).isInstanceOf(IllegalArgumentException.class);
        runner.stop();

        TestGame second = start(3);
        AtomicInteger steps = new AtomicInteger();
        second.saves().migration(1, data -> {
            steps.incrementAndGet();
            data.set(COINS, DataType.INT, data.getOrDefault(COINS, DataType.INT, 0) * 10);
        });
        second.saves().migration(2, data -> {
            steps.incrementAndGet();
            data.set(COINS, DataType.INT, data.getOrDefault(COINS, DataType.INT, 0) + 1);
        });
        assertThat(second.saves().version()).isEqualTo(3);
        List<Integer> coins = new ArrayList<>();
        second.on(GameLoadEvent.class, e -> coins.add(e.data().getOrDefault(COINS, DataType.INT, 0)));
        runner.backend().files().putPickable("imported.gulpsave", exported);
        await(second.saves().slot("imported").importFile());
        assertThat(failure(second.saves().slot("imported").importFile())).isNotNull();
        await(second.saves().slot("imported").load());
        assertThat(steps).hasValue(2);
        assertThat(coins).containsExactly(51);
        assertThat(failure(second.saves().slot("bad").importData(new byte[] {1, 2, 3})))
                .isInstanceOf(IllegalArgumentException.class);

        // A newer save cannot be loaded by an older game.
        await(second.saves().slot("imported").save());
        byte[] newer = await(second.saves().slot("imported").exportData());
        runner.stop();
        TestGame third = start(1);
        await(third.saves().slot("newer").importData(newer));
        assertThat(failure(third.saves().slot("newer").load())).hasMessageContaining("newer version");
        assertThatThrownBy(() -> third.saves().slot("Bad Name")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void autosaveWritesTheSlotRegularly() {
        TestGame game = start(1);
        game.saves().autosave("auto", Duration.ofSeconds(1));
        assertThat(game.saves().autosaveSlot()).isEqualTo("auto");
        runner.step(80);
        assertThat(await(game.saves().slot("auto").exists())).isTrue();
        game.saves().stopAutosave();
        assertThat(game.saves().autosaveSlot()).isNull();
        await(game.saves().slot("auto").delete());
        runner.step(80);
        assertThat(await(game.saves().slot("auto").exists())).isFalse();
    }
}
