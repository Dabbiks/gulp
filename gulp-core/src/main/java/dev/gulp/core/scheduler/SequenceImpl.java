package dev.gulp.core.scheduler;

import dev.gulp.api.Owner;
import dev.gulp.api.scheduler.Sequence;
import dev.gulp.api.scheduler.Task;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/** {@link Sequence} executed by one repeating task that advances through its steps. */
final class SequenceImpl implements Sequence {

    private sealed interface Step permits Action, WaitTicks, WaitDuration, WaitUntil {}

    private record Action(Runnable action) implements Step {}

    private record WaitTicks(long ticks) implements Step {}

    private record WaitDuration(Duration duration) implements Step {}

    private record WaitUntil(BooleanSupplier condition) implements Step {}

    private final SchedulerImpl.View scheduler;
    private final List<Step> steps = new ArrayList<>();
    private int times = 1;
    private boolean started;

    private int index;
    private long waitRemaining = -1;
    private int completedRuns;
    private @Nullable Task task;

    SequenceImpl(SchedulerImpl.View scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public Sequence run(Runnable action) {
        return add(new Action(action));
    }

    @Override
    public Sequence wait(Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Duration must not be negative");
        }
        return add(new WaitDuration(duration));
    }

    @Override
    public Sequence waitTicks(long ticks) {
        if (ticks < 1) {
            throw new IllegalArgumentException("Wait must be at least 1 tick, got " + ticks);
        }
        return add(new WaitTicks(ticks));
    }

    @Override
    public Sequence waitUntil(BooleanSupplier condition) {
        return add(new WaitUntil(condition));
    }

    @Override
    public Sequence repeat(int times) {
        if (times < 1) {
            throw new IllegalArgumentException("Repeat count must be at least 1, got " + times);
        }
        requireNotStarted();
        this.times = times;
        return this;
    }

    @Override
    public Sequence repeatForever() {
        requireNotStarted();
        this.times = 0;
        return this;
    }

    @Override
    public Task start(Owner owner) {
        requireNotStarted();
        if (steps.isEmpty()) {
            throw new IllegalStateException("Sequence has no steps");
        }
        started = true;
        Task scheduled = scheduler.startSequence(owner, this::step);
        task = scheduled;
        return scheduled;
    }

    private Sequence add(Step step) {
        requireNotStarted();
        steps.add(step);
        return this;
    }

    private void requireNotStarted() {
        if (started) {
            throw new IllegalStateException("Sequence already started");
        }
    }

    /** Called once per tick: runs actions until a wait blocks or the sequence ends. */
    private void step() {
        boolean restarted = false;
        while (true) {
            if (index >= steps.size()) {
                completedRuns++;
                if (times != 0 && completedRuns >= times) {
                    if (task != null) {
                        task.cancel();
                    }
                    return;
                }
                index = 0;
                if (restarted) {
                    return; // a pass without blocking waits: continue next tick instead of looping forever
                }
                restarted = true;
            }
            Step current = steps.get(index);
            switch (current) {
                case Action action -> {
                    action.action().run();
                    index++;
                }
                case WaitTicks wait -> {
                    if (!waited(wait.ticks())) {
                        return;
                    }
                }
                case WaitDuration wait -> {
                    if (!waited(Math.max(1, scheduler.ticks(wait.duration())))) {
                        return;
                    }
                }
                case WaitUntil wait -> {
                    if (!wait.condition().getAsBoolean()) {
                        return;
                    }
                    index++;
                }
            }
        }
    }

    /** Counts down a wait; the tick that starts a wait counts as its first tick. Returns whether it finished. */
    private boolean waited(long ticks) {
        if (waitRemaining < 0) {
            waitRemaining = ticks;
            return false;
        }
        waitRemaining--;
        if (waitRemaining > 0) {
            return false;
        }
        waitRemaining = -1;
        index++;
        return true;
    }
}
