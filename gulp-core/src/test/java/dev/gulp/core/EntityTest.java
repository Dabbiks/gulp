package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.PauseMode;
import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityClickEvent;
import dev.gulp.api.entity.EntityHoverEnterEvent;
import dev.gulp.api.entity.EntityHoverExitEvent;
import dev.gulp.api.entity.EntityRemoveEvent;
import dev.gulp.api.entity.EntityScreenEnterEvent;
import dev.gulp.api.entity.EntityScreenExitEvent;
import dev.gulp.api.entity.EntitySpawnEvent;
import dev.gulp.api.entity.EntityTeleportEvent;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.entity.component.Follow;
import dev.gulp.api.entity.component.Interactable;
import dev.gulp.api.entity.component.Lifetime;
import dev.gulp.api.entity.component.SpriteComponent;
import dev.gulp.api.entity.component.WorldText;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.input.SystemCursor;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.registry.Key;
import dev.gulp.api.world.Location;
import dev.gulp.api.world.World;
import dev.gulp.api.world.WorldSettings;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EntityTest {

    static final List<String> LOG = new ArrayList<>();

    /** Logs its lifecycle; ticks early. */
    static final class Probe extends Component {
        final String name;
        int ticks;

        Probe(String name) {
            this.name = name;
        }

        @Override
        protected void onAttach() {
            LOG.add(name + " attach");
        }

        @Override
        protected void onSpawn() {
            LOG.add(name + " spawn " + entity().isSpawned());
        }

        @Override
        protected void onTick() {
            ticks++;
            if (ticks == 1) {
                LOG.add(name + " tick " + world().ticks());
            }
        }

        @Override
        protected void onRemove() {
            LOG.add(name + " remove");
        }

        @Override
        public int tickOrder() {
            return -5;
        }
    }

    /** Moves right every tick; ticks after the probe. */
    static final class Walker extends Component {
        @Override
        protected void onTick() {
            entity().setPosition(entity().x() + 0.5f, entity().y());
        }
    }

    /** Throws every tick; errors must not stop other components. */
    static final class Broken extends Component {
        @Override
        protected void onTick() {
            throw new IllegalStateException("broken on purpose");
        }
    }

    static final EntityType BLOB = EntityType.builder(Key.of("test", "blob"))
            .size(1f, 1f)
            .component(() -> new Probe("blob"))
            .tags("creature")
            .build();
    static final EntityType BIG_BLOB = EntityType.builder(Key.of("test", "big_blob"))
            .parent(BLOB)
            .size(2f, 2f)
            .component(Walker::new)
            .tags("big")
            .layer("foreground")
            .zIndex(3)
            .persistent(false)
            .pauseMode(PauseMode.ALWAYS)
            .build();
    static final EntityType ROCK = EntityType.builder(Key.of("test", "rock")).build();

    private @Nullable HeadlessRunner runner;

    @AfterEach
    void tearDown() {
        LOG.clear();
        if (runner != null) {
            runner.stop();
        }
    }

    private TestGame start() {
        TestGame game = new TestGame();
        runner = Fixtures.started(game);
        return game;
    }

    private World world(TestGame game) {
        World world = game.worlds().create("arena", WorldSettings.DEFAULT);
        game.worlds().switchTo("arena");
        runner.step(1);
        return world;
    }

    @Test
    void lifecycleTickOrderAndRemoval() {
        TestGame game = start();
        World world = world(game);
        List<String> events = new ArrayList<>();
        game.on(
                EntitySpawnEvent.class,
                e -> events.add("spawn " + e.entity().type().key().path()));
        game.on(
                EntityRemoveEvent.class,
                e -> events.add("remove " + e.entity().type().key().path()));
        Entity blob = world.spawn(BLOB, 1, 2, e -> e.setName("first"));
        assertThat(LOG).containsExactly("blob attach", "blob spawn true");
        assertThat(blob.isSpawned()).isTrue();
        assertThat(world.entity("first")).isSameAs(blob);
        assertThat(world.entity(blob.id())).isSameAs(blob);
        assertThat(blob.tags().has("creature")).isTrue();
        assertThat(blob.toString()).contains("test:blob", "first");
        runner.step(1);
        assertThat(LOG).contains("blob tick " + world.ticks());
        assertThat(blob.get(Probe.class).ticks).isEqualTo(1);

        Entity big = world.spawn(BIG_BLOB, 0, 0);
        assertThat(big.size()).isEqualTo(new Vec2(2, 2));
        assertThat(big.layer()).isEqualTo("foreground");
        assertThat(big.zIndex()).isEqualTo(3);
        assertThat(big.isPersistent()).isFalse();
        assertThat(big.pauseMode()).isEqualTo(PauseMode.ALWAYS);
        assertThat(big.tags().all()).containsExactly("creature", "big");
        assertThat(big.components()).hasSize(2);
        runner.step(2);
        assertThat(big.x()).isEqualTo(1f);

        game.engine().pause();
        runner.step(2);
        assertThat(big.x()).as("ALWAYS entities tick while paused").isEqualTo(2f);
        assertThat(blob.get(Probe.class).ticks).isEqualTo(3);
        game.engine().resume();

        blob.remove();
        assertThat(blob.isRemoved()).isTrue();
        assertThat(blob.isSpawned()).isFalse();
        assertThat(LOG).contains("blob remove");
        assertThat(world.entity("first")).isNull();
        assertThat(world.entityCount()).isEqualTo(1);
        blob.remove();
        assertThat(events).containsExactly("spawn blob", "spawn big_blob", "remove blob");
    }

    @Test
    void cancelledSpawnsAndBrokenComponents() {
        TestGame game = start();
        World world = world(game);
        game.on(EntitySpawnEvent.class, e -> e.setCancelled(e.entity().type().equals(ROCK)));
        Entity rock = world.spawn(ROCK, 0, 0);
        assertThat(rock.isRemoved()).isTrue();
        assertThat(world.entityCount()).isZero();
        Entity blob = world.spawn(BLOB, 0, 0);
        blob.add(new Broken());
        runner.step(3);
        assertThat(blob.get(Probe.class).ticks).isEqualTo(3);
        assertThatThrownBy(() -> blob.add(new Broken())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> blob.get(Walker.class)).isInstanceOf(IllegalStateException.class);
        assertThat(blob.find(Walker.class)).isEmpty();
        assertThat(blob.find(Component.class)).isPresent();
        assertThat(blob.has(Broken.class)).isTrue();
        assertThat(blob.remove(Broken.class)).isTrue();
        assertThat(blob.remove(Broken.class)).isFalse();
        Probe detached = new Probe("loose");
        assertThatThrownBy(detached::entity).isInstanceOf(IllegalStateException.class);
        assertThat(detached.isEnabled()).isTrue();
        detached.setEnabled(false);
        blob.remove(Probe.class);
        blob.add(detached);
        runner.step(2);
        assertThat(detached.ticks).isZero();
        Entity named = world.spawn(BLOB, 0, 0, e -> e.setName("x"));
        assertThatThrownBy(() -> world.spawn(BLOB, 0, 0, e -> e.setName("x")))
                .isInstanceOf(IllegalArgumentException.class);
        named.setName(null);
        assertThat(world.entity("x")).isNull();
    }

    @Test
    void hierarchyTransformsAndTeleports() {
        TestGame game = start();
        World world = world(game);
        World other = game.worlds().create("other", WorldSettings.DEFAULT);
        Entity parent = world.spawn(ROCK, 0, 0);
        Entity child = world.spawn(ROCK, 5, 5);
        Entity grandchild = world.spawn(ROCK, 9, 9);
        parent.attach(child, new Vec2(1, 0));
        child.attach(grandchild, new Vec2(0, 1));
        assertThat(child.position()).isEqualTo(new Vec2(1, 0));
        assertThat(grandchild.position()).isEqualTo(new Vec2(1, 1));
        assertThat(child.parent()).isSameAs(parent);
        assertThat(parent.children()).containsExactly(child);
        assertThatThrownBy(() -> grandchild.attach(parent, Vec2.ZERO)).isInstanceOf(IllegalArgumentException.class);
        parent.setPosition(10, 0);
        assertThat(grandchild.x()).isEqualTo(11f);
        parent.setRotation(45).setScale(2, 3).setSize(4, 5).setTint(Color.RED).setVisible(false);
        parent.setFlipX(true).setFlipY(true).setLayer("effects").setZIndex(7).setPersistent(false);
        assertThat(parent.rotation()).isEqualTo(45f);
        assertThat(parent.scale()).isEqualTo(new Vec2(2, 3));
        assertThat(parent.bounds()).isEqualTo(new Rect(8, -2.5f, 4, 5));
        assertThat(parent.isVisible()).isFalse();
        assertThat(parent.isFlipX() && parent.isFlipY()).isTrue();
        assertThat(parent.tint()).isEqualTo(Color.RED);
        assertThatThrownBy(() -> parent.setSize(-1, 1)).isInstanceOf(IllegalArgumentException.class);

        List<String> teleports = new ArrayList<>();
        parent.on(EntityTeleportEvent.class, e -> teleports.add(e.from().x() + "->" + e.to().x()));
        parent.teleport(20, 0);
        assertThat(grandchild.x()).isEqualTo(21f);
        game.on(EntityTeleportEvent.class, e -> e.setCancelled(e.to().x() > 100));
        parent.teleport(200, 0);
        assertThat(parent.x()).isEqualTo(20f);
        assertThat(teleports).containsExactly("10.0->20.0", "20.0->200.0");

        child.detach();
        assertThat(parent.children()).isEmpty();
        parent.attach(child, Vec2.ZERO);
        parent.setDetachOnRemove(true);
        assertThat(parent.isDetachOnRemove()).isTrue();
        parent.remove();
        runner.step(1);
        assertThat(child.isRemoved()).isFalse();
        assertThat(child.parent()).isNull();

        child.teleport(new Location(other, 3, 4));
        assertThat(child.world()).isSameAs(other);
        assertThat(other.entities()).containsExactly(child);
        assertThat(world.entities()).containsExactly(grandchild);
        assertThatThrownBy(() -> world.spawn(ROCK, other.location(0, 0), e -> {}))
                .isInstanceOf(IllegalArgumentException.class);
        Entity dropped = world.spawn(ROCK, 0, 0);
        dropped.attach(world.spawn(ROCK, 0, 0), Vec2.ZERO);
        dropped.remove();
        assertThat(world.entityCount()).isEqualTo(1);
    }

    @Test
    void queriesUseTheGridTagsAndStores() {
        TestGame game = start();
        World world = world(game);
        for (int i = 0; i < 20; i++) {
            Entity e = world.spawn(i % 2 == 0 ? BLOB : ROCK, i, 0);
            if (i % 5 == 0) {
                e.tags().add("marked");
            }
        }
        assertThat(world.query().tag("creature").count()).isEqualTo(10);
        assertThat(world.query().with(Probe.class).count()).isEqualTo(10);
        assertThat(world.query().with(Component.class).count()).isEqualTo(10);
        assertThat(world.query().without(Probe.class).count()).isEqualTo(10);
        assertThat(world.query().withoutTag("creature").type(ROCK).count()).isEqualTo(10);
        assertThat(world.query().tag("marked").list()).extracting(Entity::x).containsExactly(0f, 5f, 10f, 15f);
        assertThat(world.query().near(new Vec2(10, 0), 2.1f).count()).isEqualTo(5);
        assertThat(world.entitiesNear(new Vec2(10, 0), 2.1f)).hasSize(5);
        assertThat(world.entitiesIn(new Rect(0, -1, 3.4f, 2))).hasSize(4);
        assertThat(world.query()
                        .in(new Rect(-100, -100, 1000, 1000))
                        .in(new Rect(0, -1, 1, 1))
                        .count())
                .isEqualTo(2);
        assertThat(world.query().filter(e -> e.x() > 17).count()).isEqualTo(2);
        assertThat(world.query().tag("nothing").first()).isNull();
        assertThat(world.query()
                        .tag("creature")
                        .sortedByDistance(new Vec2(9, 0))
                        .get(0)
                        .x())
                .isIn(8f, 10f);
        List<Entity> visited = new ArrayList<>();
        world.query().with(Probe.class).tag("marked").forEach(visited::add);
        assertThat(visited).hasSize(2);
        Entity far = world.spawn(ROCK, 1000, 1000);
        far.setPosition(3, 0.2f);
        far.tags().add("moved");
        assertThat(world.query().near(new Vec2(3, 0), 0.5f).tag("moved").first())
                .isSameAs(far);
        far.tags().remove("moved");
        assertThat(world.query().tag("moved").count()).isZero();
        assertThat(world.entities()).hasSize(21);
    }

    @Test
    void entitySchedulersAndHandlersEndWithTheEntity() {
        TestGame game = start();
        World world = world(game);
        Entity blob = world.spawn(BLOB, 0, 0);
        int[] runs = {0};
        blob.scheduler().every(1, () -> runs[0]++);
        List<String> removed = new ArrayList<>();
        blob.on(EntityRemoveEvent.class, e -> removed.add("mine"));
        runner.step(3);
        assertThat(runs[0]).isEqualTo(3);
        blob.remove();
        runner.step(3);
        assertThat(runs[0]).isEqualTo(3);
        assertThat(removed).containsExactly("mine");
        assertThat(blob.data()).isNotNull();
    }

    @Test
    void builtInComponents() {
        TestGame game = start();
        World world = world(game);
        Entity leader = world.spawn(ROCK, 0, 0);
        Entity pet = world.spawn(
                ROCK, 10, 0, e -> e.add(new Follow(leader).speed(60f).stopDistance(1f)));
        runner.step(2);
        assertThat(pet.x()).isEqualTo(8f);
        Follow follow = pet.get(Follow.class).offset(0, 0).removeWithTarget(true);
        assertThat(follow.target()).isSameAs(leader);
        runner.step(20);
        assertThat(pet.x()).isEqualTo(1f);
        leader.remove();
        runner.step(2);
        assertThat(pet.isRemoved()).isTrue();

        Entity spark = world.spawn(ROCK, 0, 0, e -> e.add(new Lifetime(Duration.ofMillis(100))));
        Lifetime lifetime = spark.get(Lifetime.class);
        assertThat(lifetime.remaining()).isEqualTo(6);
        runner.step(3);
        assertThat(lifetime.progress()).isEqualTo(0.5f);
        lifetime.restart();
        runner.step(6);
        assertThat(spark.isRemoved()).isTrue();
        assertThatThrownBy(() -> new Lifetime(0)).isInstanceOf(IllegalArgumentException.class);

        Entity sign = world.spawn(ROCK, 0, 0);
        WorldText text = sign.add(
                new WorldText("Hello").style(dev.gulp.api.text.TextStyle.of(10)).offset(0, -1));
        text.text("Bye");
        assertThat(text.text().plain()).isEqualTo("Bye");
        assertThat(text.offset()).isEqualTo(new Vec2(0, -1));
        assertThat(text.style().size()).isEqualTo(10f);

        SpriteComponent sprite =
                new SpriteComponent(game.graphics().texture(new Pixmap(16, 16)).region());
        Entity painted = world.spawn(ROCK, 0, 0, e -> e.add(sprite));
        sprite.setSize(2, 2).setAnchor(0.5f, 1f).setOffset(0, 0.5f).setTint(Color.YELLOW);
        assertThat(sprite.size()).isEqualTo(new Vec2(2, 2));
        assertThat(sprite.anchor()).isEqualTo(new Vec2(0.5f, 1f));
        assertThat(sprite.offset()).isEqualTo(new Vec2(0, 0.5f));
        assertThat(sprite.tint()).isEqualTo(Color.YELLOW);
        painted.setFlipX(true);
        runner.step(1);
        assertThat(game.display().stats().drawCalls()).isPositive();
    }

    @Test
    void pointerHoverClickAndScreenEvents() {
        TestGame game = start();
        World world = world(game);
        List<String> events = new ArrayList<>();
        game.on(EntityHoverEnterEvent.class, e -> events.add("enter"));
        game.on(EntityHoverExitEvent.class, e -> events.add("exit"));
        game.on(EntityClickEvent.class, e -> events.add("click " + e.button()));
        game.on(EntityScreenEnterEvent.class, e -> events.add("screen+"));
        game.on(EntityScreenExitEvent.class, e -> events.add("screen-"));
        Interactable interactable = new Interactable().cursor(SystemCursor.HAND);
        Entity button = world.spawn(ROCK, 0, 0, e -> e.setSize(4, 4).add(interactable));
        runner.step(1);
        assertThat(button.isOnScreen()).isTrue();
        Vec2 center = world.camera().worldToScreen(Vec2.ZERO);
        runner.backend().input().inject(l -> l.mouseMoved(center.x(), center.y(), 1, 1));
        runner.backend().input().inject(l -> l.mouseButton(0, true, 0));
        runner.step(2);
        assertThat(interactable.isHovered()).isTrue();
        assertThat(runner.backend().window().cursorShape()).isEqualTo(1);
        runner.backend().input().inject(l -> l.mouseMoved(1, 1, 1, 1));
        runner.step(1);
        assertThat(interactable.isHovered()).isFalse();
        interactable.area(new Rect(-50, -50, 100, 100));
        assertThat(interactable.area()).isNotNull();
        runner.step(1);
        button.setPosition(1000, 1000);
        runner.step(1);
        assertThat(button.isOnScreen()).isFalse();
        assertThat(events).containsExactly("screen+", "enter", "click LEFT", "exit", "enter", "screen-", "exit");
    }
}
