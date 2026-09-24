package dev.gulp.core;

import static dev.gulp.core.Fixtures.started;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.entity.EntityType;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Keyed;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.registry.Registry;
import dev.gulp.api.registry.RegistryKey;
import dev.gulp.api.service.ServicePriority;
import dev.gulp.api.service.ServiceRegisterEvent;
import dev.gulp.api.service.ServiceUnregisterEvent;
import dev.gulp.api.service.Services;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RegistriesAndServicesTest {

    record Item(Key key, int price) implements Keyed {}

    record Enemy(Key key) implements dev.gulp.api.registry.Keyed {}

    private HeadlessRunner runner;

    @AfterEach
    void stop() {
        if (runner != null) {
            runner.stop();
        }
    }

    @Test
    void registriesAcceptValuesOnlyDuringLoad() {
        TestGame game = new TestGame();
        List<Registry<Item>> created = new ArrayList<>();
        game.onLoad = () -> {
            Registry<Item> items = game.registries().create(game.key("items"), Item.class);
            items.register(game.key("sword"), new Item(game.key("sword"), 100));
            items.register(new Item(game.key("shield"), 50));
            game.registries()
                    .register(
                            Registries.ENTITY_TYPE,
                            EntityType.builder(game.key("slime")).build());
            created.add(items);
        };
        runner = started(game);
        Registries registries = runner.engine().registries();
        Registry<Item> items = created.getFirst();

        assertThat(registries.isFrozen()).isTrue();
        assertThat(items.isFrozen()).isTrue();
        assertThat(items.keys()).containsExactly(Key.parse("test:sword"), Key.parse("test:shield"));
        assertThat(items.values()).extracting(Item::price).containsExactly(100, 50);
        assertThat(items.stream().mapToInt(Item::price).sum()).isEqualTo(150);
        assertThat(items).hasSize(2);
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(Key.parse("test:sword")).price()).isEqualTo(100);
        assertThat(items.get(Key.parse("test:axe"))).isNull();
        assertThat(items.contains(Key.parse("test:shield"))).isTrue();
        assertThat(items.getOrThrow(Key.parse("test:shield")).price()).isEqualTo(50);
        assertThatThrownBy(() -> items.getOrThrow(Key.parse("test:axe")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("test:axe")
                .hasMessageContaining("test:items");
        assertThat(items.registryKey().type()).isEqualTo(Item.class);
        assertThat(items.toString()).contains("test:items");
        assertThat(registries.get(Registries.ENTITY_TYPE).get(Key.parse("test:slime")))
                .isNotNull();
        assertThat(registries.get(Key.parse("test:items"))).isSameAs(items);
        assertThat(registries.get(Key.parse("test:nothing"))).isNull();
        assertThat(registries.all()).hasSize(11);

        assertThatThrownBy(() -> items.register(new Item(Key.parse("test:late"), 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frozen");
        assertThatThrownBy(() -> registries.create(Key.parse("test:late"), Item.class))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registryRejectsBadRegistrations() {
        TestGame game = new TestGame();
        List<Throwable> errors = new ArrayList<>();
        game.onLoad = () -> {
            Registries registries = game.registries();
            Registry<Item> items = registries.create(game.key("items"), Item.class);
            items.register(new Item(game.key("sword"), 1));
            Runnable[] attempts = {
                () -> items.register(new Item(game.key("sword"), 2)),
                () -> items.register(game.key("other"), new Item(game.key("sword2"), 2)),
                () -> items.register(new Item(Key.of("gulp", "x"), 2)),
                () -> registries.create(game.key("items"), Item.class),
                () -> registries.create(Key.of("gulp", "mine"), Item.class),
                () -> registries.get(RegistryKey.of(Key.parse("test:missing"), Item.class)),
                () -> registries.get(RegistryKey.of(Key.parse("test:items"), Enemy.class)),
                () -> castAndRegister(registries.get(Registries.ENTITY_TYPE))
            };
            for (Runnable attempt : attempts) {
                try {
                    attempt.run();
                } catch (IllegalArgumentException e) {
                    errors.add(e);
                }
            }
        };
        runner = started(game);

        assertThat(errors).hasSize(8);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void castAndRegister(Registry<EntityType> registry) {
        ((Registry) registry).register(new Item(Key.parse("test:not_an_entity"), 0));
    }

    interface Economy {
        String name();
    }

    @Test
    void servicesPickTheHighestPriorityAndFireEvents() {
        TestGame game = new TestGame();
        runner = started(game);
        Services services = runner.engine().services();
        List<String> events = new ArrayList<>();
        game.on(ServiceRegisterEvent.class, e -> events.add("+" + ((Economy) e.provider()).name()));
        game.on(
                ServiceUnregisterEvent.class,
                e -> events.add("-" + ((Economy) e.provider()).name() + "@"
                        + e.owner().id() + ":" + e.type().getSimpleName()));
        Economy basic = () -> "basic";
        Economy premium = () -> "premium";
        Economy fallback = () -> "fallback";

        assertThat(services.get(Economy.class)).isNull();
        assertThat(services.isProvided(Economy.class)).isFalse();
        assertThat(services.getAll(Economy.class)).isEmpty();
        assertThatThrownBy(() -> services.getOrThrow(Economy.class)).isInstanceOf(IllegalStateException.class);

        services.register(Economy.class, basic, game, ServicePriority.NORMAL);
        services.register(Economy.class, premium, game, ServicePriority.HIGH);
        services.register(Economy.class, fallback, game, ServicePriority.NORMAL);

        assertThat(services.getOrThrow(Economy.class)).isSameAs(premium);
        assertThat(services.getAll(Economy.class)).containsExactly(premium, basic, fallback);
        assertThat(services.unregister(Economy.class, premium)).isTrue();
        assertThat(services.unregister(Economy.class, premium)).isFalse();
        assertThat(services.unregister(Runnable.class, () -> {})).isFalse();
        assertThat(services.get(Economy.class)).isSameAs(basic);
        assertThat(events).containsExactly("+basic", "+premium", "+fallback", "-premium@test:Economy");

        @SuppressWarnings({"unchecked", "rawtypes"})
        Class<Object> raw = (Class) Economy.class;
        assertThatThrownBy(() -> services.register(raw, "not an economy", game, ServicePriority.NORMAL))
                .isInstanceOf(IllegalArgumentException.class);

        runner.stop();
        runner = null;
        assertThat(events).contains("-basic@test:Economy", "-fallback@test:Economy");
    }
}
