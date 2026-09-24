package dev.gulp.api.ai;

import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * A behaviour tree ticked once per game tick. Leaves are conditions and actions that return {@link Status}; composites
 * ({@code sequence}, {@code selector}, {@code parallel}) combine them and decorators ({@code inverter}, {@code repeat},
 * {@code cooldown}, {@code timeout}, {@code untilSuccess}) change the next node added. The blackboard is the entity's
 * {@code data()}. When the root finishes, the tree starts over on the next tick.
 *
 * <pre>{@code
 * BehaviorTree brain = BehaviorTree.builder()
 *         .selector()
 *             .sequence()
 *                 .condition(e -> e.data().getOrDefault(ANGRY, DataType.BOOLEAN, false))
 *                 .timeout(120).action(this::chase)
 *             .end()
 *             .cooldown(60).action(this::wander)
 *         .end()
 *         .build();
 * }</pre>
 */
public final class BehaviorTree extends Component {

    /** Result of a node in one tick. */
    public enum Status {
        /** Still working; ticked again next tick. */
        RUNNING,
        /** Done successfully. */
        SUCCESS,
        /** Done unsuccessfully. */
        FAILURE
    }

    /** How a parallel node decides its result. */
    public enum Policy {
        /** Succeeds when all children succeed, fails as soon as one fails. */
        REQUIRE_ALL,
        /** Succeeds as soon as one child succeeds, fails when all fail. */
        REQUIRE_ONE
    }

    /** An action leaf. */
    @FunctionalInterface
    public interface Action {
        /**
         * Runs for one tick.
         *
         * @param entity the tree's entity
         * @return the status
         */
        Status run(Entity entity);
    }

    private final Node root;
    private Status status = Status.RUNNING;
    private long ticks;

    private BehaviorTree(Node root) {
        this.root = root;
    }

    /**
     * Starts building a tree.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the status of the root after the last tick.
     *
     * @return the status
     */
    public Status status() {
        return status;
    }

    /** Aborts running nodes and starts over on the next tick. */
    public void reset() {
        root.reset();
        status = Status.RUNNING;
    }

    /**
     * Ticks the tree once for an entity; called every tick when used as a component.
     *
     * @param entity the entity whose data is the blackboard
     * @return the status of the root
     */
    public Status update(Entity entity) {
        ticks++;
        status = root.tick(entity, ticks);
        if (status != Status.RUNNING) {
            root.reset();
        }
        return status;
    }

    @Override
    protected void onTick() {
        update(entity());
    }

    // ------------------------------------------------------------------ nodes

    private abstract static class Node {
        abstract Status tick(Entity entity, long now);

        void reset() {}
    }

    private static final class ActionNode extends Node {
        private final Action action;

        ActionNode(Action action) {
            this.action = action;
        }

        @Override
        Status tick(Entity entity, long now) {
            return action.run(entity);
        }
    }

    private abstract static class Composite extends Node {
        final List<Node> children = new ArrayList<>();
        int current;

        @Override
        void reset() {
            current = 0;
            for (Node child : children) {
                child.reset();
            }
        }
    }

    private static final class Sequence extends Composite {
        @Override
        Status tick(Entity entity, long now) {
            while (current < children.size()) {
                Status result = children.get(current).tick(entity, now);
                if (result == Status.RUNNING) {
                    return Status.RUNNING;
                }
                if (result == Status.FAILURE) {
                    reset();
                    return Status.FAILURE;
                }
                current++;
            }
            reset();
            return Status.SUCCESS;
        }
    }

    private static final class Selector extends Composite {
        @Override
        Status tick(Entity entity, long now) {
            while (current < children.size()) {
                Status result = children.get(current).tick(entity, now);
                if (result == Status.RUNNING) {
                    return Status.RUNNING;
                }
                if (result == Status.SUCCESS) {
                    reset();
                    return Status.SUCCESS;
                }
                current++;
            }
            reset();
            return Status.FAILURE;
        }
    }

    private static final class Parallel extends Composite {
        private final Policy policy;
        private @Nullable Status[] results = new Status[0];

        Parallel(Policy policy) {
            this.policy = policy;
        }

        @Override
        Status tick(Entity entity, long now) {
            if (results.length != children.size()) {
                results = new Status[children.size()];
            }
            int succeeded = 0;
            int failed = 0;
            for (int i = 0; i < children.size(); i++) {
                Status result = results[i];
                if (result == null || result == Status.RUNNING) {
                    result = children.get(i).tick(entity, now);
                    results[i] = result;
                }
                if (result == Status.SUCCESS) {
                    succeeded++;
                } else if (result == Status.FAILURE) {
                    failed++;
                }
            }
            Status outcome = Status.RUNNING;
            if (policy == Policy.REQUIRE_ALL) {
                if (failed > 0) {
                    outcome = Status.FAILURE;
                } else if (succeeded == children.size()) {
                    outcome = Status.SUCCESS;
                }
            } else if (succeeded > 0) {
                outcome = Status.SUCCESS;
            } else if (failed == children.size()) {
                outcome = Status.FAILURE;
            }
            if (outcome != Status.RUNNING) {
                reset();
            }
            return outcome;
        }

        @Override
        void reset() {
            super.reset();
            java.util.Arrays.fill(results, null);
        }
    }

    private abstract static class Decorator extends Node {
        Node child = new ActionNode(e -> Status.SUCCESS);

        @Override
        void reset() {
            child.reset();
        }
    }

    private static final class Inverter extends Decorator {
        @Override
        Status tick(Entity entity, long now) {
            Status result = child.tick(entity, now);
            return result == Status.SUCCESS ? Status.FAILURE : result == Status.FAILURE ? Status.SUCCESS : result;
        }
    }

    private static final class Repeat extends Decorator {
        private final int times;
        private int done;

        Repeat(int times) {
            this.times = times;
        }

        @Override
        Status tick(Entity entity, long now) {
            Status result = child.tick(entity, now);
            if (result == Status.RUNNING) {
                return Status.RUNNING;
            }
            if (result == Status.FAILURE) {
                reset();
                return Status.FAILURE;
            }
            done++;
            if (times > 0 && done >= times) {
                reset();
                return Status.SUCCESS;
            }
            child.reset();
            return Status.RUNNING;
        }

        @Override
        void reset() {
            super.reset();
            done = 0;
        }
    }

    private static final class UntilSuccess extends Decorator {
        @Override
        Status tick(Entity entity, long now) {
            Status result = child.tick(entity, now);
            if (result == Status.SUCCESS) {
                return Status.SUCCESS;
            }
            if (result == Status.FAILURE) {
                child.reset();
            }
            return Status.RUNNING;
        }
    }

    private static final class Cooldown extends Decorator {
        private final int ticks;
        private long readyAt = Long.MIN_VALUE;

        Cooldown(int ticks) {
            this.ticks = ticks;
        }

        @Override
        Status tick(Entity entity, long now) {
            if (now < readyAt) {
                return Status.FAILURE;
            }
            Status result = child.tick(entity, now);
            if (result != Status.RUNNING) {
                readyAt = now + ticks;
            }
            return result;
        }
    }

    private static final class Timeout extends Decorator {
        private final int ticks;
        private long startedAt = -1;

        Timeout(int ticks) {
            this.ticks = ticks;
        }

        @Override
        Status tick(Entity entity, long now) {
            if (startedAt < 0) {
                startedAt = now;
            }
            if (now - startedAt >= ticks) {
                reset();
                return Status.FAILURE;
            }
            Status result = child.tick(entity, now);
            if (result != Status.RUNNING) {
                startedAt = -1;
            }
            return result;
        }

        @Override
        void reset() {
            super.reset();
            startedAt = -1;
        }
    }

    // ------------------------------------------------------------------ builder

    /**
     * Builds a tree top-down: composites are opened and closed with {@link #end()}, decorators apply to the next node.
     *
     * <pre>{@code
     * BehaviorTree.builder().sequence().action(a).inverter().condition(c).end().build();
     * }</pre>
     */
    public static final class Builder {
        private final Deque<Composite> open = new ArrayDeque<>();
        private final List<Decorator> pending = new ArrayList<>();
        private @Nullable Node root;

        private Builder() {}

        /**
         * Opens a sequence: runs children in order until one fails.
         *
         * @return this builder
         */
        public Builder sequence() {
            return openComposite(new Sequence());
        }

        /**
         * Opens a selector: runs children in order until one succeeds.
         *
         * @return this builder
         */
        public Builder selector() {
            return openComposite(new Selector());
        }

        /**
         * Opens a parallel node: ticks all children every tick.
         *
         * @param policy how the result is decided
         * @return this builder
         */
        public Builder parallel(Policy policy) {
            return openComposite(new Parallel(policy));
        }

        /**
         * Closes the innermost open composite.
         *
         * @return this builder
         */
        public Builder end() {
            if (open.isEmpty()) {
                throw new IllegalStateException("end() without an open sequence, selector or parallel");
            }
            open.pop();
            return this;
        }

        /**
         * Adds an action leaf.
         *
         * @param action the action
         * @return this builder
         */
        public Builder action(Action action) {
            return add(new ActionNode(action));
        }

        /**
         * Adds a condition leaf: success when the test passes, failure otherwise.
         *
         * @param test the test
         * @return this builder
         */
        public Builder condition(Predicate<Entity> test) {
            return add(new ActionNode(e -> test.test(e) ? Status.SUCCESS : Status.FAILURE));
        }

        /**
         * Swaps success and failure of the next node.
         *
         * @return this builder
         */
        public Builder inverter() {
            pending.add(new Inverter());
            return this;
        }

        /**
         * Repeats the next node while it succeeds.
         *
         * @param times how many successes to finish with, or {@code 0} forever
         * @return this builder
         */
        public Builder repeat(int times) {
            pending.add(new Repeat(times));
            return this;
        }

        /**
         * Makes the next node fail for a while after it finishes.
         *
         * @param ticks the cooldown
         * @return this builder
         */
        public Builder cooldown(int ticks) {
            pending.add(new Cooldown(ticks));
            return this;
        }

        /**
         * Fails the next node if it runs for too long.
         *
         * @param ticks the time limit
         * @return this builder
         */
        public Builder timeout(int ticks) {
            pending.add(new Timeout(ticks));
            return this;
        }

        /**
         * Runs the next node again after each failure until it succeeds.
         *
         * @return this builder
         */
        public Builder untilSuccess() {
            pending.add(new UntilSuccess());
            return this;
        }

        /**
         * Finishes the tree.
         *
         * @return the tree
         * @throws IllegalStateException if nothing was added or a composite is still open
         */
        public BehaviorTree build() {
            if (!open.isEmpty()) {
                throw new IllegalStateException(open.size() + " composite(s) not closed with end()");
            }
            Node top = root;
            if (top == null || !pending.isEmpty()) {
                throw new IllegalStateException("The tree needs a root node and no trailing decorators");
            }
            return new BehaviorTree(top);
        }

        private Builder openComposite(Composite composite) {
            add(composite);
            open.push(composite);
            return this;
        }

        private Builder add(Node node) {
            Node wrapped = node;
            for (int i = pending.size() - 1; i >= 0; i--) {
                Decorator decorator = pending.get(i);
                decorator.child = wrapped;
                wrapped = decorator;
            }
            pending.clear();
            Composite parent = open.peek();
            if (parent != null) {
                parent.children.add(wrapped);
            } else if (root == null) {
                root = wrapped;
            } else {
                throw new IllegalStateException("A tree has one root; wrap several nodes in a sequence or selector");
            }
            return this;
        }
    }
}
