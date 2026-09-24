package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.gulp.api.ai.BehaviorTree;
import dev.gulp.api.ai.BehaviorTree.Status;
import dev.gulp.api.ai.StateChangeEvent;
import dev.gulp.api.ai.StateMachine;
import dev.gulp.api.ai.Steering;
import dev.gulp.api.audio.Sound;
import dev.gulp.api.entity.DamageType;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.entity.EntityDamageEvent;
import dev.gulp.api.entity.EntityDeathEvent;
import dev.gulp.api.entity.EntityHealEvent;
import dev.gulp.api.entity.EntityType;
import dev.gulp.api.entity.component.Health;
import dev.gulp.api.entity.component.SoundEmitter;
import dev.gulp.api.event.Subscription;
import dev.gulp.api.math.BSpline;
import dev.gulp.api.math.GridPos;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.nav.FlowField;
import dev.gulp.api.nav.Graph;
import dev.gulp.api.nav.NavAgent;
import dev.gulp.api.nav.NavGrid;
import dev.gulp.api.nav.NavPathFailedEvent;
import dev.gulp.api.nav.NavTargetReachedEvent;
import dev.gulp.api.nav.Path;
import dev.gulp.api.nav.PathEndEvent;
import dev.gulp.api.nav.PathFinder;
import dev.gulp.api.nav.PathFollower;
import dev.gulp.api.nav.PathOptions;
import dev.gulp.api.physics.Mover;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.world.MapObject;
import dev.gulp.api.world.TileMap;
import dev.gulp.api.world.TileType;
import dev.gulp.api.world.World;
import dev.gulp.core.Fixtures.TestGame;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Navigation, AI and the health and sound components. */
class NavAiTest extends PhysicsFixture {

    static final TileType MUD = TileType.builder(Key.of("test", "mud"))
            .tileSet(SHEET, 3)
            .navCost(50f)
            .build();
    static final TileType FENCE = TileType.builder(Key.of("test", "fence"))
            .tileSet(SHEET, 4)
            .passable(false)
            .build();

    static final EntityType WALKER = EntityType.builder(Key.of("test", "walker"))
            .size(0.6f, 0.6f)
            .component(() -> new Mover().topDown(true))
            .component(NavAgent::new)
            .build();
    static final EntityType GHOST = EntityType.builder(Key.of("test", "ghost"))
            .size(0.6f, 0.6f)
            .component(NavAgent::new)
            .build();

    /** A closed room from (0, 0) to (10, 20) with one gap in its right wall at y = 15. */
    static void maze(TileMap map) {
        map.fill("walls", 0, 0, 11, 1, SOLID);
        map.fill("walls", 0, 20, 11, 1, SOLID);
        map.fill("walls", 0, 0, 1, 21, SOLID);
        map.fill("walls", 10, 0, 1, 15, SOLID);
        map.fill("walls", 10, 16, 1, 5, SOLID);
    }

    @Test
    void gridPathsGoAroundWallsAndAvoidCosts() {
        TestGame game = start(g -> {});
        World world = world(game);
        maze(world.tileMap());
        NavGrid grid = world.navGrid();
        assertThat(grid.isPassable(10, 5)).isFalse();
        assertThat(grid.isPassable(10, 15)).isTrue();
        assertThat(grid.cellAt(new Vec2(3.7f, -0.2f))).isEqualTo(new GridPos(3, -1));
        assertThat(grid.center(new GridPos(3, 4))).isEqualTo(new Vec2(3.5f, 4.5f));
        Path path = grid.findPath(new Vec2(5.5f, 5.5f), new Vec2(15.5f, 5.5f));
        assertThat(path).isNotNull();
        assertThat(path.start()).isEqualTo(new Vec2(5.5f, 5.5f));
        assertThat(path.end()).isEqualTo(new Vec2(15.5f, 5.5f));
        assertThat(path.points()).anyMatch(p -> p.y() > 14.5f && p.y() < 16.5f);
        for (int i = 0; i + 1 < path.points().size(); i++) {
            assertThat(grid.hasLineOfSight(path.points().get(i), path.points().get(i + 1)))
                    .as("smoothed legs are clear")
                    .isTrue();
        }
        Path raw = grid.findPath(new Vec2(5.5f, 5.5f), new Vec2(15.5f, 5.5f), PathOptions.DEFAULT.withSmooth(false));
        assertThat(raw.points().size()).isGreaterThan(path.points().size());
        Path fourWay = grid.findPath(
                new Vec2(5.5f, 5.5f),
                new Vec2(15.5f, 5.5f),
                PathOptions.DEFAULT.withDiagonal(false).withSmooth(false));
        for (int i = 1; i + 2 < fourWay.points().size(); i++) {
            Vec2 a = fourWay.points().get(i);
            Vec2 b = fourWay.points().get(i + 1);
            assertThat(a.x() == b.x() || a.y() == b.y()).as("4-way moves only").isTrue();
        }
        Path jump = grid.findPath(
                new Vec2(5.5f, 5.5f),
                new Vec2(15.5f, 5.5f),
                PathOptions.DEFAULT.withAlgorithm(PathOptions.Algorithm.JUMP_POINT));
        assertThat(jump).isNotNull();
        assertThat(jump.length()).as("as short as A*").isCloseTo(path.length(), within(1f));
        assertThat(grid.findPath(new Vec2(5.5f, 5.5f), new Vec2(10.5f, 5.5f)))
                .as("goal in a wall")
                .isNull();
        assertThat(grid.findPath(Vec2.ZERO, new Vec2(500f, 0f), PathOptions.DEFAULT.withMaxNodes(10)))
                .as("node limit")
                .isNull();
        // Mud costs more than the detour around it.
        world.tileMap().fill("ground", 20, 0, 1, 10, MUD);
        world.tileMap().setTile("ground", 22, 0, FENCE);
        assertThat(grid.cost(20, 5)).isEqualTo(50f);
        assertThat(grid.isPassable(22, 0)).isFalse();
        Path around = grid.findPath(
                new Vec2(18.5f, 5.5f),
                new Vec2(22.5f, 5.5f),
                PathOptions.DEFAULT.withAlgorithm(PathOptions.Algorithm.A_STAR).withSmooth(false));
        assertThat(around.points()).noneMatch(p -> p.x() > 20f && p.x() < 21f && p.y() < 9.5f && p.y() > 1f);
        assertThat(PathFinder.find(grid, new Vec2(5.5f, 5.5f), new Vec2(15.5f, 5.5f)))
                .isNotNull();
    }

    @Test
    void blocksRequestsAndFlowFields() {
        TestGame game = start(g -> {});
        World world = world(game);
        maze(world.tileMap());
        NavGrid grid = world.navGrid();
        long revision = grid.revision();
        Subscription gate = grid.block(new Rect(10f, 15f, 1f, 1f), game);
        assertThat(grid.revision()).isNotEqualTo(revision);
        assertThat(grid.isPassable(10, 15)).isFalse();
        assertThat(grid.findPath(new Vec2(5.5f, 5.5f), new Vec2(15.5f, 5.5f)))
                .as("gate closed")
                .isNull();
        List<String> results = new ArrayList<>();
        grid.requestPath(new Vec2(5.5f, 5.5f), new Vec2(15.5f, 5.5f), PathOptions.DEFAULT)
                .thenSync(p -> results.add("path"))
                .onFailure(e -> results.add("failed"));
        runner.step(3);
        assertThat(results).containsExactly("failed");
        gate.cancel();
        assertThat(gate.isActive()).isFalse();
        assertThat(grid.isPassable(10, 15)).isTrue();
        grid.requestPath(new Vec2(5.5f, 5.5f), new Vec2(15.5f, 5.5f), PathOptions.DEFAULT)
                .thenSync(p -> results.add("path " + p.points().size()));
        runner.step(3);
        assertThat(results.get(1)).startsWith("path ");
        FlowField field = grid.flowField(new Vec2(15.5f, 5.5f), 20);
        assertThat(field.target()).isEqualTo(new Vec2(15.5f, 5.5f));
        assertThat(field.isReachable(new Vec2(5.5f, 5.5f))).isTrue();
        assertThat(field.distance(new Vec2(15.5f, 5.5f))).isZero();
        assertThat(field.distance(new Vec2(10.5f, 5.5f))).isEqualTo(Float.POSITIVE_INFINITY);
        assertThat(field.distance(new Vec2(100f, 100f))).isEqualTo(Float.POSITIVE_INFINITY);
        assertThat(field.direction(new Vec2(100f, 100f))).isEqualTo(Vec2.ZERO);
        // Following the field from the far side leads through the gap.
        Vec2 walker = new Vec2(5.5f, 5.5f);
        for (int i = 0; i < 400 && walker.distanceTo(field.target()) > 0.3f; i++) {
            walker = walker.add(field.direction(walker).scale(0.1f));
        }
        assertThat(walker.distanceTo(field.target())).isLessThan(0.3f);
        // Blocks of a disabled owner are dropped.
        grid.block(new Rect(0f, 0f, 2f, 2f), game);
        assertThat(grid.isPassable(1, 1)).isFalse();
    }

    @Test
    void agentsWalkAroundWallsAndReportArrival() {
        TestGame game = start(g -> {});
        World world = world(game);
        maze(world.tileMap());
        List<String> events = new ArrayList<>();
        game.on(NavTargetReachedEvent.class, e -> events.add("reached " + e.destination()));
        game.on(NavPathFailedEvent.class, e -> events.add("failed " + e.destination()));
        Entity walker = world.spawn(WALKER, 5.5f, 5.5f);
        NavAgent agent =
                walker.get(NavAgent.class).speed(6f).arrivalDistance(0.2f).repathInterval(15);
        agent.moveTo(new Vec2(15.5f, 5.5f));
        assertThat(agent.isMoving()).isTrue();
        assertThat(agent.target()).isEqualTo(new Vec2(15.5f, 5.5f));
        runner.step(300);
        assertThat(walker.position().distanceTo(new Vec2(15.5f, 5.5f))).isLessThan(0.3f);
        assertThat(events).contains("reached (15.5, 5.5)");
        assertThat(agent.isMoving()).isFalse();
        agent.moveTo(new Vec2(10.5f, 3.5f));
        runner.step(5);
        assertThat(events).anyMatch(s -> s.startsWith("failed"));
        assertThat(agent.target()).isNull();
        // Chasing another entity, with separation between agents.
        Entity ghost = world.spawn(GHOST, 13.5f, 3.5f);
        NavAgent chaser = ghost.get(NavAgent.class).avoidance(1f).options(PathOptions.DEFAULT);
        assertThat(chaser.speed()).isEqualTo(3f);
        chaser.follow(walker);
        runner.step(120);
        assertThat(ghost.position().distanceTo(walker.position())).isLessThan(1.2f);
        assertThat(chaser.path()).isNotNull();
        assertThat(chaser.velocity()).isNotNull();
        walker.remove();
        runner.step(2);
        assertThat(chaser.target()).isNull();
        chaser.stop();
        assertThat(chaser.isMoving()).isFalse();
    }

    @Test
    void pathsAndFollowers() {
        Path path = Path.of(new Vec2(0, 0), new Vec2(4, 0), new Vec2(4, 3));
        assertThat(path.length()).isEqualTo(7f);
        assertThat(path.pointAt(2f)).isEqualTo(new Vec2(2, 0));
        assertThat(path.pointAt(5.5f)).isEqualTo(new Vec2(4, 1.5f));
        assertThat(path.pointAt(-1f)).isEqualTo(Vec2.ZERO);
        assertThat(path.pointAt(99f)).isEqualTo(new Vec2(4, 3));
        assertThat(path.directionAt(1f)).isEqualTo(new Vec2(1, 0));
        assertThat(path.directionAt(5f)).isEqualTo(new Vec2(0, 1));
        assertThat(path.project(new Vec2(3f, 2f))).isCloseTo(6f, within(1e-4f));
        assertThat(path.reversed().start()).isEqualTo(new Vec2(4, 3));
        assertThat(Path.EMPTY.isEmpty()).isTrue();
        assertThat(Path.EMPTY.length()).isZero();
        assertThat(Path.EMPTY.directionAt(1f)).isEqualTo(Vec2.ZERO);
        assertThatThrownBy(() -> Path.EMPTY.start()).isInstanceOf(IllegalStateException.class);
        assertThat(Path.of(List.of(Vec2.ONE)).project(Vec2.ZERO)).isZero();
        assertThat(path).isEqualTo(Path.of(List.of(new Vec2(0, 0), new Vec2(4, 0), new Vec2(4, 3))));
        assertThat(path.hashCode()).isEqualTo(path.reversed().reversed().hashCode());
        assertThat(path.toString()).contains("Path");
        Path curve = Path.fromCurve(BSpline.of(List.of(Vec2.ZERO, new Vec2(1, 2), new Vec2(3, 2), new Vec2(4, 0))), 16);
        assertThat(curve.points()).hasSize(17);
        MapObject rect = new MapObject("r", "Rail", "Objects", 2f, 2f, 2f, 2f, Map.of());
        assertThat(Path.fromObject(rect).length()).isEqualTo(8f);
        MapObject line = new MapObject("l", "Rail", "Objects", 0f, 0f, 0f, 0f, Map.of(), List.of(Vec2.ZERO, Vec2.ONE));
        assertThat(Path.fromObject(line).points()).hasSize(2);

        TestGame game = start(g -> {});
        World world = world(game);
        List<Boolean> ends = new ArrayList<>();
        game.on(PathEndEvent.class, e -> ends.add(e.isFinished()));
        EntityType lift =
                EntityType.builder(Key.of("test", "lift")).size(1f, 1f).build();
        Entity once = world.spawn(
                lift, 0f, 0f, e -> e.add(new PathFollower(path).speed(7f).rotate(true)));
        Entity pingPong = world.spawn(
                lift, 0f, 0f, e -> e.add(new PathFollower(path).speed(14f).pingPong(true)));
        Entity loop = world.spawn(
                lift, 0f, 0f, e -> e.add(new PathFollower(path).speed(14f).loop(true)));
        runner.step(90);
        PathFollower first = once.get(PathFollower.class);
        assertThat(first.isRunning()).isFalse();
        assertThat(once.position()).isEqualTo(new Vec2(4, 3));
        assertThat(once.rotation()).isCloseTo(90f, within(0.1f));
        assertThat(first.distance()).isEqualTo(7f);
        assertThat(ends).contains(true, false);
        assertThat(pingPong.get(PathFollower.class).isRunning()).isTrue();
        assertThat(loop.get(PathFollower.class).speed()).isEqualTo(14f);
        first.setDistance(3f);
        assertThat(first.isRunning()).isTrue();
        first.path(path.reversed());
        assertThat(first.path().start()).isEqualTo(new Vec2(4, 3));
    }

    @Test
    void graphSearch() {
        // A small road network: A-B 4, A-C 1, C-B 1, B-D 1.
        Map<String, Map<String, Float>> roads = Map.of(
                "A", Map.of("B", 4f, "C", 1f),
                "B", Map.of("D", 1f),
                "C", Map.of("B", 1f),
                "D", Map.of());
        Graph<String> graph = new Graph<>() {
            @Override
            public void neighbours(String node, Edges<String> out) {
                roads.get(node).forEach(out::add);
            }

            @Override
            public float estimate(String from, String to) {
                return 0f;
            }
        };
        assertThat(PathFinder.find(graph, "A", "D")).containsExactly("A", "C", "B", "D");
        assertThat(PathFinder.find(graph, "D", "A")).isNull();
        assertThat(PathFinder.find(graph, "A", "D", 1)).isNull();
        Graph<String> negative = new Graph<>() {
            @Override
            public void neighbours(String node, Edges<String> out) {
                out.add("X", -1f);
            }

            @Override
            public float estimate(String from, String to) {
                return 0f;
            }
        };
        assertThatThrownBy(() -> PathFinder.find(negative, "A", "X")).isInstanceOf(IllegalArgumentException.class);
    }

    enum Mode {
        IDLE,
        ALERT,
        FLEE
    }

    @Test
    void stateMachinesChangeStatesAndReport() {
        TestGame game = start(g -> {});
        World world = world(game);
        List<String> log = new ArrayList<>();
        boolean[] noise = {false};
        boolean[] hurt = {false};
        StateMachine<Mode> brain = new StateMachine<>(Mode.IDLE)
                .onEnter(Mode.IDLE, () -> log.add("enter idle"))
                .onTick(Mode.ALERT, () -> log.add("alert"))
                .onExit(Mode.ALERT, () -> log.add("exit alert"))
                .transition(Mode.IDLE, Mode.ALERT, () -> noise[0])
                .transition(Mode.ALERT, Mode.IDLE, () -> !noise[0])
                .anyTransition(Mode.FLEE, () -> hurt[0]);
        game.on(StateChangeEvent.class, e -> log.add(e.from() + ">" + e.to() + " " + (e.machine() == brain)));
        EntityType npc = EntityType.builder(Key.of("test", "npc")).build();
        Entity guard = world.spawn(npc, 0f, 0f, e -> e.add(brain));
        runner.step(2);
        assertThat(brain.state()).isEqualTo(Mode.IDLE);
        assertThat(brain.ticksInState()).isEqualTo(2);
        noise[0] = true;
        runner.step(2);
        assertThat(brain.is(Mode.ALERT)).isTrue();
        assertThat(brain.previousState()).isEqualTo(Mode.IDLE);
        hurt[0] = true;
        runner.step(1);
        assertThat(brain.state()).isEqualTo(Mode.FLEE);
        brain.changeState(Mode.IDLE);
        assertThat(log)
                .containsSubsequence(
                        "enter idle", "IDLE>ALERT true", "alert", "exit alert", "ALERT>FLEE true", "FLEE>IDLE true");
        // Also usable on its own.
        StateMachine<Mode> standalone = new StateMachine<>(Mode.IDLE).transition(Mode.IDLE, Mode.ALERT, () -> true);
        standalone.update();
        assertThat(standalone.state()).isEqualTo(Mode.ALERT);
        assertThat(guard.has(StateMachine.class)).isTrue();
    }

    @Test
    void behaviourTrees() {
        TestGame game = start(g -> {});
        World world = world(game);
        EntityType npc = EntityType.builder(Key.of("test", "npc")).build();
        Entity self = world.spawn(npc, 0f, 0f);
        List<String> log = new ArrayList<>();
        int[] counter = {0};
        BehaviorTree tree = BehaviorTree.builder()
                .selector()
                .sequence()
                .condition(e -> counter[0] >= 3)
                .action(e -> {
                    log.add("attack");
                    return Status.SUCCESS;
                })
                .end()
                .action(e -> {
                    counter[0]++;
                    log.add("wait " + counter[0]);
                    return counter[0] % 2 == 1 ? Status.RUNNING : Status.FAILURE;
                })
                .end()
                .build();
        assertThat(tree.update(self)).isEqualTo(Status.RUNNING);
        assertThat(tree.update(self)).isEqualTo(Status.FAILURE);
        assertThat(tree.update(self)).isEqualTo(Status.RUNNING);
        // Composites remember their running child: the wait finishes before the sequence is checked again.
        assertThat(tree.update(self)).isEqualTo(Status.FAILURE);
        assertThat(tree.update(self)).isEqualTo(Status.SUCCESS);
        assertThat(log).containsExactly("wait 1", "wait 2", "wait 3", "wait 4", "attack");
        tree.reset();
        assertThat(tree.status()).isEqualTo(Status.RUNNING);

        int[] runs = {0};
        BehaviorTree decorated = BehaviorTree.builder()
                .parallel(BehaviorTree.Policy.REQUIRE_ALL)
                .inverter()
                .condition(e -> false)
                .repeat(3)
                .action(e -> {
                    runs[0]++;
                    return Status.SUCCESS;
                })
                .end()
                .build();
        Status status = Status.RUNNING;
        for (int i = 0; i < 5 && status == Status.RUNNING; i++) {
            status = decorated.update(self);
        }
        assertThat(status).isEqualTo(Status.SUCCESS);
        assertThat(runs[0]).isEqualTo(3);

        int[] tries = {0};
        BehaviorTree retry = BehaviorTree.builder()
                .parallel(BehaviorTree.Policy.REQUIRE_ONE)
                .untilSuccess()
                .action(e -> ++tries[0] >= 3 ? Status.SUCCESS : Status.FAILURE)
                .timeout(2)
                .action(e -> Status.RUNNING)
                .end()
                .build();
        assertThat(retry.update(self)).isEqualTo(Status.RUNNING);
        assertThat(retry.update(self)).isEqualTo(Status.RUNNING);
        assertThat(retry.update(self)).isEqualTo(Status.SUCCESS);

        int[] fired = {0};
        BehaviorTree cooled = BehaviorTree.builder()
                .cooldown(3)
                .action(e -> {
                    fired[0]++;
                    return Status.SUCCESS;
                })
                .build();
        for (int i = 0; i < 8; i++) {
            cooled.update(self);
        }
        assertThat(fired[0]).as("ticks 1, 4 and 7").isEqualTo(3);
        BehaviorTree timedOut =
                BehaviorTree.builder().timeout(1).action(e -> Status.RUNNING).build();
        timedOut.update(self);
        assertThat(timedOut.update(self)).isEqualTo(Status.FAILURE);
        BehaviorTree allFail = BehaviorTree.builder()
                .parallel(BehaviorTree.Policy.REQUIRE_ONE)
                .action(e -> Status.FAILURE)
                .end()
                .build();
        assertThat(allFail.update(self)).isEqualTo(Status.FAILURE);
        BehaviorTree oneFails = BehaviorTree.builder()
                .parallel(BehaviorTree.Policy.REQUIRE_ALL)
                .action(e -> Status.FAILURE)
                .end()
                .build();
        assertThat(oneFails.update(self)).isEqualTo(Status.FAILURE);
        BehaviorTree repeatFails =
                BehaviorTree.builder().repeat(0).action(e -> Status.FAILURE).build();
        assertThat(repeatFails.update(self)).isEqualTo(Status.FAILURE);
        self.add(BehaviorTree.builder().action(e -> Status.SUCCESS).build());
        runner.step(1);
        assertThat(self.get(BehaviorTree.class).status()).isEqualTo(Status.SUCCESS);

        assertThatThrownBy(() -> BehaviorTree.builder().end()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BehaviorTree.builder().sequence().build()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> BehaviorTree.builder().build()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () -> BehaviorTree.builder().action(e -> Status.SUCCESS).action(e -> Status.SUCCESS))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void steeringBehaviours() {
        TestGame game = start(g -> {});
        World world = world(game);
        world.tileMap().fill("walls", 20, 0, 1, 20, SOLID);
        EntityType boid =
                EntityType.builder(Key.of("test", "boid")).size(0.4f, 0.4f).build();
        Entity target = world.spawn(boid, 10f, 5f);
        Entity seeker = world.spawn(
                boid,
                0f,
                5f,
                e -> e.add(new Steering().maxSpeed(4f).maxForce(40f).arrive(target::position, 1f, 2f)));
        runner.step(240);
        assertThat(seeker.position().distanceTo(target.position())).isLessThan(0.2f);
        Steering s = seeker.get(Steering.class);
        assertThat(s.maxSpeed()).isEqualTo(4f);
        assertThat(s.lastForce()).isNotNull();
        s.clear().flee(target::position, 1f, 3f);
        runner.step(60);
        assertThat(seeker.position().distanceTo(target.position())).isGreaterThan(2.5f);
        s.clear().seek(target, 1f).evade(target, 1f, 0.5f).pursue(target, 0.5f);
        runner.step(30);
        // A flock: wander, keep apart, align and stay together.
        List<Entity> flock = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            flock.add(world.spawn(
                    boid,
                    5f + i * 0.3f,
                    12f,
                    e -> e.add(new Steering()
                            .wander(0.3f)
                            .separation(1.5f, 1f)
                            .alignment(0.5f, 3f)
                            .cohesion(0.5f, 3f))));
        }
        runner.step(120);
        for (Entity bird : flock) {
            assertThat(bird.position().distanceTo(new Vec2(6f, 12f))).isLessThan(20f);
        }
        // Following a path and steering away from the wall ahead.
        Entity rider = world.spawn(
                boid,
                0f,
                0f,
                e -> e.add(new Steering()
                        .maxSpeed(5f)
                        .maxForce(50f)
                        .followPath(Path.of(Vec2.ZERO, new Vec2(8f, 0f), new Vec2(8f, 8f)), 1f, 1f)));
        runner.step(240);
        assertThat(rider.position().distanceTo(new Vec2(8f, 8f))).isLessThan(1.5f);
        Entity runner1 = world.spawn(
                boid,
                15f,
                10f,
                e -> e.add(new Steering()
                        .maxSpeed(4f)
                        .maxForce(40f)
                        .setVelocity(new Vec2(4f, 0f))
                        .seek(() -> new Vec2(30f, 10f), 1f)
                        .avoidObstacles(3f, 3f)));
        runner.step(60);
        assertThat(runner1.x()).as("did not pass the wall").isLessThan(20f);
        assertThat(runner1.get(Steering.class).velocity()).isNotNull();
    }

    @Test
    void healthDamageHealAndDeath() {
        TestGame game =
                start(g -> g.registries().register(Registries.DAMAGE_TYPE, DamageType.of(Key.of("test", "fire"))));
        World world = world(game);
        DamageType fire = game.registries().get(Registries.DAMAGE_TYPE).getOrThrow(Key.of("test", "fire"));
        assertThat(game.registries().get(Registries.DAMAGE_TYPE).contains(DamageType.GENERIC.key()))
                .isTrue();
        EntityType slime = EntityType.builder(Key.of("test", "slime"))
                .component(() -> new Health(10f).invulnerableTicks(5))
                .build();
        Entity a = world.spawn(slime, 0f, 0f);
        Entity b = world.spawn(slime, 1f, 0f);
        Health health = a.get(Health.class);
        List<String> log = new ArrayList<>();
        game.on(EntityDamageEvent.class, e -> {
            log.add("damage " + e.amount() + " " + e.type().key().path() + " " + (e.source() == b));
            if (e.type().equals(fire)) {
                e.setAmount(e.amount() * 2);
            }
        });
        game.on(EntityHealEvent.class, e -> {
            log.add("heal " + e.amount());
            e.setAmount(e.amount() + 1);
        });
        game.on(EntityDeathEvent.class, e -> log.add("death " + e.type().key().path() + " " + (e.killer() == b)));
        assertThat(health.damage(3f, DamageType.GENERIC, b)).isEqualTo(3f);
        assertThat(health.current()).isEqualTo(7f);
        assertThat(health.isInvulnerable()).isTrue();
        assertThat(health.damage(3f, DamageType.GENERIC, b)).isZero();
        runner.step(6);
        assertThat(health.isInvulnerable()).isFalse();
        assertThat(health.heal(1f)).isEqualTo(2f);
        assertThat(health.current()).isEqualTo(9f);
        assertThat(health.damage(5f, fire, b))
                .as("doubled, capped to what is left")
                .isEqualTo(9f);
        assertThat(health.isDead()).isTrue();
        assertThat(health.damage(1f, fire, b)).isZero();
        assertThat(health.heal(5f)).isZero();
        assertThat(log).contains("damage 3.0 generic true", "heal 1.0", "death fire true");
        health.revive(4f).setMax(3f);
        assertThat(health.current()).isEqualTo(3f);
        assertThat(health.max()).isEqualTo(3f);
        health.setCurrent(99f).invulnerableTicks(0);
        assertThat(health.current()).isEqualTo(3f);
        assertThat(health.invulnerableTicks()).isZero();
        game.on(EntityDamageEvent.class, e -> e.setCancelled(true));
        assertThat(health.damage(1f, DamageType.GENERIC, null)).isZero();
        assertThat(health.damage(-1f, DamageType.GENERIC, null)).isZero();
        assertThatThrownBy(() -> new Health(0f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> health.setMax(-1f)).isInstanceOf(IllegalArgumentException.class);
        assertThat(DamageType.GENERIC.toString()).contains("generic");
        assertThat(fire).isEqualTo(DamageType.of(Key.of("test", "fire")));
        assertThat(fire.hashCode())
                .isEqualTo(DamageType.of(Key.of("test", "fire")).hashCode());
    }

    @Test
    void soundEmittersFollowTheirEntity() {
        TestGame game = start(g -> {});
        World world = world(game);
        Sound hum = Sound.builder(Key.of("test", "hum"))
                .file(dev.gulp.api.asset.AssetKey.audio("test:sounds/none"))
                .build();
        EntityType engine = EntityType.builder(Key.of("test", "engine"))
                .component(() -> new SoundEmitter(hum).loop(true).volume(0.5f))
                .build();
        Entity car = world.spawn(engine, 3f, 4f);
        SoundEmitter emitter = car.get(SoundEmitter.class);
        assertThat(emitter.sound()).isEqualTo(hum);
        assertThat(emitter.isLooping()).isTrue();
        runner.step(2);
        car.setPosition(8f, 4f);
        runner.step(2);
        emitter.volume(0.2f).sound(hum);
        emitter.play();
        emitter.stop();
        assertThat(emitter.isPlaying()).isFalse();
        EntityType quiet = EntityType.builder(Key.of("test", "quiet"))
                .component(() -> new SoundEmitter(hum).playOnSpawn(false))
                .build();
        Entity silent = world.spawn(quiet, 0f, 0f);
        assertThat(silent.get(SoundEmitter.class).isPlaying()).isFalse();
        car.remove();
        runner.step(1);
    }
}
