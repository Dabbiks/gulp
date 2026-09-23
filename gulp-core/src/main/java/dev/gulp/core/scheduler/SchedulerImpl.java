package dev.gulp.core.scheduler;

import dev.gulp.api.Owner;
import dev.gulp.api.scheduler.Promise;
import dev.gulp.api.scheduler.Scheduler;
import dev.gulp.api.scheduler.Sequence;
import dev.gulp.api.scheduler.Task;
import dev.gulp.core.CoreContext;
import dev.gulp.core.MainQueue;
import dev.gulp.platform.PlatformExecutor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Task queues for game ticks and real ticks. Scheduling is thread-safe: new tasks wait in a pending list and join the
 * queues at the start of the next tick of their kind. Running a queue does not allocate.
 */
public final class SchedulerImpl {

    /** Consecutive failures after which a task is cancelled. */
    public static final int MAX_CONSECUTIVE_ERRORS = 3;

    private final CoreContext context;
    private final Owner defaultOwner;
    private final IntSupplier ticksPerSecond;
    private final PlatformExecutor executor;
    private final MainQueue mainQueue;

    private final List<TaskImpl> gameTasks = new ArrayList<>();
    private final List<TaskImpl> realTasks = new ArrayList<>();
    private final List<TaskImpl> pendingGame = new ArrayList<>();
    private final List<TaskImpl> pendingReal = new ArrayList<>();
    private final View rootView;

    /**
     * Creates the scheduler.
     *
     * @param context owner state and loggers
     * @param defaultOwner owner of tasks scheduled without {@link Scheduler#owner(Owner)}: the game
     * @param ticksPerSecond converts durations to ticks
     * @param executor runs {@link Scheduler#async(Supplier)} work
     * @param mainQueue brings async results back to the main thread
     */
    public SchedulerImpl(
            CoreContext context,
            Owner defaultOwner,
            IntSupplier ticksPerSecond,
            PlatformExecutor executor,
            MainQueue mainQueue) {
        this.context = context;
        this.defaultOwner = defaultOwner;
        this.ticksPerSecond = ticksPerSecond;
        this.executor = executor;
        this.mainQueue = mainQueue;
        this.rootView = new View(defaultOwner, false);
    }

    /**
     * Returns the public view, owned by the game.
     *
     * @return the scheduler
     */
    public Scheduler api() {
        return rootView;
    }

    /** Runs the game-time tasks due this tick. */
    public void tickGame() {
        runQueue(gameTasks, pendingGame);
    }

    /** Runs the real-time tasks due this tick. */
    public void tickRealtime() {
        runQueue(realTasks, pendingReal);
    }

    /**
     * Cancels every task of an owner; called when it is disabled.
     *
     * @param owner the owner
     */
    public void cancelAll(Owner owner) {
        synchronized (this) {
            cancelIn(pendingGame, owner);
            cancelIn(pendingReal, owner);
        }
        cancelIn(gameTasks, owner);
        cancelIn(realTasks, owner);
    }

    /**
     * Returns the number of live tasks of an owner, for diagnostics.
     *
     * @param owner the owner
     * @return the count
     */
    public synchronized int countOwnedBy(Owner owner) {
        int count = 0;
        for (List<TaskImpl> list : List.of(gameTasks, realTasks, pendingGame, pendingReal)) {
            for (TaskImpl task : list) {
                if (task.owner == owner && !task.isDone()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void cancelIn(List<TaskImpl> tasks, Owner owner) {
        for (TaskImpl task : tasks) {
            if (task.owner == owner) {
                task.cancel();
            }
        }
    }

    private void runQueue(List<TaskImpl> tasks, List<TaskImpl> pending) {
        synchronized (this) {
            if (!pending.isEmpty()) {
                tasks.addAll(pending);
                pending.clear();
            }
        }
        int kept = 0;
        int size = tasks.size();
        for (int i = 0; i < size; i++) {
            TaskImpl task = tasks.get(i);
            if (!task.cancelled && --task.remaining <= 0) {
                runTask(task);
                if (task.period > 0) {
                    task.remaining = task.period;
                } else {
                    task.done = true;
                }
            }
            if (!task.cancelled && !task.done) {
                tasks.set(kept++, task);
            }
        }
        for (int i = size - 1; i >= kept; i--) {
            tasks.remove(i);
        }
    }

    private void runTask(TaskImpl task) {
        try {
            task.action.run();
            task.consecutiveErrors = 0;
        } catch (Throwable error) {
            task.consecutiveErrors++;
            if (task.consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                task.cancel();
                context.loggerOf(task.owner)
                        .error("Task failed " + MAX_CONSECUTIVE_ERRORS + " times in a row and was cancelled", error);
            } else {
                context.loggerOf(task.owner).error("Unhandled exception in a scheduled task", error);
            }
        }
    }

    TaskImpl schedule(Owner owner, boolean realtime, long delayTicks, long periodTicks, Runnable action) {
        if (delayTicks < 0) {
            throw new IllegalArgumentException("Delay must not be negative, got " + delayTicks);
        }
        if (periodTicks < 0) {
            throw new IllegalArgumentException("Period must be at least 1 tick, got " + periodTicks);
        }
        context.requireActive(owner, "a task");
        TaskImpl task = new TaskImpl(owner, realtime, Math.max(1, delayTicks), periodTicks, action);
        synchronized (this) {
            (realtime ? pendingReal : pendingGame).add(task);
        }
        return task;
    }

    long toTicks(Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Duration must not be negative, got " + duration);
        }
        double seconds = duration.getSeconds() + duration.getNano() / 1e9;
        return (long) Math.ceil(seconds * ticksPerSecond.getAsInt() - 1e-9);
    }

    /** A scheduled task. The countdown is only touched on the main thread. */
    static final class TaskImpl implements Task {
        final Owner owner;
        final boolean realtime;
        final long period;
        final Runnable action;
        long remaining;
        int consecutiveErrors;
        volatile boolean cancelled;
        volatile boolean done;

        TaskImpl(Owner owner, boolean realtime, long delay, long period, Runnable action) {
            this.owner = owner;
            this.realtime = realtime;
            this.remaining = delay;
            this.period = period;
            this.action = action;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled || done;
        }

        @Override
        public Owner owner() {
            return owner;
        }

        @Override
        public boolean isRepeating() {
            return period > 0;
        }

        @Override
        public boolean isRealtime() {
            return realtime;
        }
    }

    /** A {@link Scheduler} bound to an owner and a clock. */
    final class View implements Scheduler {
        private final Owner owner;
        private final boolean realtime;

        View(Owner owner, boolean realtime) {
            this.owner = owner;
            this.realtime = realtime;
        }

        @Override
        public Scheduler owner(Owner newOwner) {
            return new View(newOwner, realtime);
        }

        @Override
        public Scheduler realtime() {
            return new View(owner, true);
        }

        @Override
        public Task run(Runnable task) {
            return schedule(owner, realtime, 1, 0, task);
        }

        @Override
        public Task later(long delayTicks, Runnable task) {
            return schedule(owner, realtime, delayTicks, 0, task);
        }

        @Override
        public Task later(Duration delay, Runnable task) {
            return schedule(owner, realtime, toTicks(delay), 0, task);
        }

        @Override
        public Task every(long periodTicks, Runnable task) {
            return every(periodTicks, periodTicks, task);
        }

        @Override
        public Task every(Duration period, Runnable task) {
            long ticks = Math.max(1, toTicks(period));
            return every(ticks, ticks, task);
        }

        @Override
        public Task every(long delayTicks, long periodTicks, Runnable task) {
            if (periodTicks < 1) {
                throw new IllegalArgumentException("Period must be at least 1 tick, got " + periodTicks);
            }
            return schedule(owner, realtime, delayTicks, periodTicks, task);
        }

        @Override
        public Task every(Duration delay, Duration period, Runnable task) {
            return every(toTicks(delay), Math.max(1, toTicks(period)), task);
        }

        @Override
        public Sequence sequence() {
            return new SequenceImpl(this);
        }

        @Override
        public <T> Promise<T> async(Supplier<T> supplier) {
            context.requireActive(owner, "an async task");
            PromiseImpl<T> promise = new PromiseImpl<>(owner, context, mainQueue);
            executor.execute(() -> {
                T result;
                try {
                    result = supplier.get();
                } catch (Throwable failure) {
                    mainQueue.post(() -> promise.fail(failure));
                    return;
                }
                mainQueue.post(() -> promise.complete(result));
            });
            return promise;
        }

        long ticks(Duration duration) {
            return toTicks(duration);
        }

        Task startSequence(Owner sequenceOwner, Runnable step) {
            return schedule(sequenceOwner, realtime, 1, 1, step);
        }
    }
}
