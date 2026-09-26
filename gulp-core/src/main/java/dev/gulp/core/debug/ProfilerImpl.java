package dev.gulp.core.debug;

import dev.gulp.api.Owner;
import dev.gulp.api.debug.ProfileReport;
import dev.gulp.api.debug.ProfileReport.Category;
import dev.gulp.api.debug.Profiler;
import dev.gulp.api.module.GameModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * {@link Profiler}: while stopped, the engine only reads {@link #running}; while running, it measures handlers,
 * tasks, component types, ticks and frames through {@link #begin()} and the {@code end*} methods. Sections use a
 * stack of start times, so opening and closing them allocates nothing once their entry exists.
 */
public final class ProfilerImpl implements Profiler {

    /** Whether the profiler is measuring; the engine checks it before measuring anything. */
    public boolean running;

    private final boolean enabled;
    private final LongSupplier clock;
    private final LongSupplier ticks;
    private final LongSupplier frames;
    private final Map<Object, Stat> byKey = new IdentityHashMap<>();
    private final Map<String, Stat> byName = new HashMap<>();
    private final List<Stat> all = new ArrayList<>();
    private final Stat tick = new Stat(Category.TICK, "game tick");
    private final Stat render = new Stat(Category.RENDER, "frame render");
    private long startNanos;
    private long startTicks;
    private long startFrames;
    private long[] sectionStarts = new long[16];
    private Stat[] sectionStats = new Stat[16];
    private int sectionDepth;
    private final Section section = this::closeSection;

    /**
     * Creates the profiler.
     *
     * @param enabled whether it may run at all (the developer tools are on)
     * @param clock nanoseconds
     * @param ticks the game tick counter
     * @param frames the frame counter
     */
    public ProfilerImpl(boolean enabled, LongSupplier clock, LongSupplier ticks, LongSupplier frames) {
        this.enabled = enabled;
        this.clock = clock;
        this.ticks = ticks;
        this.frames = frames;
    }

    @Override
    public void start() {
        if (running || !enabled) {
            return;
        }
        byKey.clear();
        byName.clear();
        all.clear();
        tick.reset();
        render.reset();
        all.add(tick);
        all.add(render);
        sectionDepth = 0;
        startNanos = clock.getAsLong();
        startTicks = ticks.getAsLong();
        startFrames = frames.getAsLong();
        running = true;
    }

    @Override
    public ProfileReport stop() {
        if (!running) {
            return ProfileReport.EMPTY;
        }
        running = false;
        List<ProfileReport.Entry> entries = new ArrayList<>();
        for (Stat stat : all) {
            if (stat.calls > 0) {
                entries.add(new ProfileReport.Entry(stat.category, stat.name, stat.calls, stat.total, stat.max));
            }
        }
        entries.sort((a, b) -> Long.compare(b.totalNanos(), a.totalNanos()));
        return new ProfileReport(
                clock.getAsLong() - startNanos,
                ticks.getAsLong() - startTicks,
                frames.getAsLong() - startFrames,
                entries);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public Section section(String name) {
        if (!running) {
            return NOOP;
        }
        if (sectionDepth == sectionStarts.length) {
            sectionStarts = java.util.Arrays.copyOf(sectionStarts, sectionDepth * 2);
            sectionStats = java.util.Arrays.copyOf(sectionStats, sectionDepth * 2);
        }
        Stat stat = byName.get(name);
        if (stat == null) {
            stat = new Stat(Category.SECTION, name);
            byName.put(name, stat);
            all.add(stat);
        }
        sectionStats[sectionDepth] = stat;
        sectionStarts[sectionDepth++] = clock.getAsLong();
        return section;
    }

    private void closeSection() {
        if (sectionDepth == 0) {
            return;
        }
        sectionDepth--;
        Stat stat = sectionStats[sectionDepth];
        sectionStats[sectionDepth] = null;
        if (running) {
            stat.add(clock.getAsLong() - sectionStarts[sectionDepth]);
        }
    }

    private static final Section NOOP = () -> {};

    // ------------------------------------------------------------------ engine hooks

    /**
     * Returns the time to pass to an {@code end*} method, or 0 when not running.
     *
     * @return nanoseconds, or 0
     */
    public long begin() {
        return running ? clock.getAsLong() : 0L;
    }

    /**
     * Records an event handler run.
     *
     * @param handler the handler, the key of its entry
     * @param eventName the event name
     * @param owner the owner of the handler
     * @param start what {@link #begin()} returned
     */
    public void endListener(Object handler, String eventName, Owner owner, long start) {
        if (start == 0L || !running) {
            return;
        }
        long took = clock.getAsLong() - start;
        Stat stat = byKey.get(handler);
        if (stat == null) {
            stat = keyed(handler, Category.LISTENER, eventName + " (" + owner.id() + ")");
        }
        stat.add(took);
        module(owner, took);
    }

    /**
     * Records a scheduled task run.
     *
     * @param owner the owner of the task, the key of its entry
     * @param start what {@link #begin()} returned
     */
    public void endTask(Owner owner, long start) {
        if (start == 0L || !running) {
            return;
        }
        long took = clock.getAsLong() - start;
        Stat stat = byKey.get(owner);
        if (stat == null) {
            stat = keyed(owner, Category.TASK, "tasks of " + owner.id());
        }
        stat.add(took);
        module(owner, took);
    }

    /**
     * Records the ticks of one component type in one world tick.
     *
     * @param type the component class
     * @param calls how many components ticked
     * @param start what {@link #begin()} returned
     */
    public void endComponents(Class<?> type, int calls, long start) {
        if (start == 0L || !running || calls == 0) {
            return;
        }
        Stat stat = byKey.get(type);
        if (stat == null) {
            String name = type.getSimpleName();
            stat = keyed(type, Category.COMPONENT, name.isEmpty() ? type.getName() : name);
        }
        stat.add(clock.getAsLong() - start, calls);
    }

    /**
     * Records a game tick.
     *
     * @param nanos how long it took
     */
    public void tick(long nanos) {
        if (running) {
            tick.add(nanos);
        }
    }

    /**
     * Records a frame render.
     *
     * @param nanos how long it took
     */
    public void render(long nanos) {
        if (running) {
            render.add(nanos);
        }
    }

    private void module(Owner owner, long took) {
        if (!(owner instanceof GameModule)) {
            return;
        }
        String name = "module " + owner.id();
        Stat stat = byName.get(name);
        if (stat == null) {
            stat = new Stat(Category.MODULE, owner.id());
            byName.put(name, stat);
            all.add(stat);
        }
        stat.add(took);
    }

    private Stat keyed(Object key, Category category, String name) {
        Stat stat = new Stat(category, name);
        byKey.put(key, stat);
        all.add(stat);
        return stat;
    }

    /** Totals of one entry. */
    private static final class Stat {
        final Category category;
        final String name;
        long calls;
        long total;
        long max;

        Stat(Category category, String name) {
            this.category = category;
            this.name = name;
        }

        void add(long nanos) {
            add(nanos, 1);
        }

        void add(long nanos, int count) {
            calls += count;
            total += nanos;
            if (nanos > max) {
                max = nanos;
            }
        }

        void reset() {
            calls = 0;
            total = 0;
            max = 0;
        }
    }
}
