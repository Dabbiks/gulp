package dev.gulp.core.debug;

import dev.gulp.api.Logger;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.debug.Debug;
import dev.gulp.api.debug.DebugDraw;
import dev.gulp.api.debug.DebugFlag;
import dev.gulp.api.debug.ProfileReport;
import dev.gulp.api.debug.Stats;
import dev.gulp.api.entity.Component;
import dev.gulp.api.entity.Entity;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.KeyboardKey;
import dev.gulp.api.input.Keys;
import dev.gulp.api.input.MouseButton;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.render.Draw;
import dev.gulp.api.render.RenderStats;
import dev.gulp.api.text.TextAlign;
import dev.gulp.api.text.TextStyle;
import dev.gulp.api.world.World;
import dev.gulp.core.input.InputImpl;
import dev.gulp.core.util.Timestamps;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * {@link Debug}: the F3 overlay and its statistics, F3 key combinations, the entity inspector, shapes drawn in the
 * world with a lifetime in ticks, and the profiler. When the tools are off everything is a no-op, and the engine only
 * pays two clock reads per tick and frame for the statistics.
 */
public final class DebugImpl implements Debug, InputImpl.DebugHook {

    /** What the tools need from the engine, so they can be tested apart from it. */
    public interface Environment {
        /**
         * Returns the active world.
         *
         * @return the world, or {@code null}
         */
        @Nullable World activeWorld();

        /**
         * Returns the loaded chunks of a world.
         *
         * @param world the world
         * @return the count
         */
        int chunks(World world);

        /**
         * Returns the sounds playing.
         *
         * @return the count
         */
        int sounds();

        /**
         * Returns the draw statistics of the last frame.
         *
         * @return the statistics
         */
        RenderStats renderStats();

        /**
         * Returns the pointer in world units of the active world's camera.
         *
         * @param world the world
         * @return the point
         */
        Vec2 mouseWorld(World world);

        /**
         * Returns the heap in use.
         *
         * @return bytes, or {@code -1}
         */
        long memoryUsed();

        /**
         * Returns the largest heap.
         *
         * @return bytes, or {@code -1}
         */
        long memoryMax();

        /**
         * Opens or closes the UI inspector.
         *
         * @param open whether it is open
         */
        void uiInspector(boolean open);

        /**
         * Returns whether the UI inspector is open.
         *
         * @return {@code true} if open
         */
        boolean isUiInspectorOpen();

        /**
         * Writes a user data file, for profiler reports.
         *
         * @param name the file name
         * @param content the content
         */
        void writeFile(String name, byte[] content);
    }

    private static final int GRAPH = 120;
    private static final Color PANEL = Color.rgba(0x000000b0);
    private static final Color GOOD = Color.rgba(0x66bb6aff);
    private static final Color SLOW = Color.rgba(0xffca28ff);
    private static final Color BAD = Color.rgba(0xef5350ff);

    private final boolean enabled;
    private final Environment environment;
    private final Logger logger;
    private final ProfilerImpl profiler;
    private final Shapes shapes;
    private final Function<Component, @Nullable JsonObject> componentState;
    private final boolean[] flags = new boolean[DebugFlag.values().length];
    private boolean overlay;
    private boolean f3Held;
    private boolean comboUsed;
    private @Nullable Entity inspected;

    private final float[] frameTimes = new float[GRAPH];
    private int frameIndex;
    private long lastFrameNanos;
    private long fpsWindowStart;
    private int fpsFrames;
    private float fps;
    private float frameMillis;
    private float renderMillis;
    private float tickMillis;
    private float tps;

    /**
     * Creates the tools.
     *
     * @param enabled whether they work
     * @param environment the engine side
     * @param logger where reports go
     * @param profiler the profiler
     * @param componentState the saved fields of a component, for the inspector
     */
    public DebugImpl(
            boolean enabled,
            Environment environment,
            Logger logger,
            ProfilerImpl profiler,
            Function<Component, @Nullable JsonObject> componentState) {
        this.enabled = enabled;
        this.environment = environment;
        this.logger = logger;
        this.profiler = profiler;
        this.componentState = componentState;
        this.shapes = new Shapes(enabled);
    }

    // ================================================================== Debug

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Shapes draw() {
        return shapes;
    }

    @Override
    public ProfilerImpl profiler() {
        return profiler;
    }

    @Override
    public Stats stats() {
        World world = environment.activeWorld();
        RenderStats render = environment.renderStats();
        return new Stats(
                fps,
                frameMillis,
                renderMillis,
                tps,
                tickMillis,
                world == null ? 0 : world.entityCount(),
                world == null ? 0 : environment.chunks(world),
                world == null ? 0 : world.particles().count(),
                environment.sounds(),
                render.drawCalls(),
                render.textureBinds(),
                environment.memoryUsed(),
                environment.memoryMax());
    }

    @Override
    public boolean isOverlayVisible() {
        return overlay;
    }

    @Override
    public void setOverlayVisible(boolean visible) {
        overlay = visible && enabled;
    }

    @Override
    public boolean isOn(DebugFlag flag) {
        if (flag == DebugFlag.UI_INSPECTOR) {
            return environment.isUiInspectorOpen();
        }
        return flags[flag.ordinal()];
    }

    @Override
    public void set(DebugFlag flag, boolean on) {
        boolean value = on && enabled;
        if (flag == DebugFlag.UI_INSPECTOR) {
            environment.uiInspector(value);
            return;
        }
        flags[flag.ordinal()] = value;
        if (flag == DebugFlag.ENTITY_INSPECTOR && !value) {
            inspected = null;
        }
    }

    @Override
    public @Nullable Entity inspected() {
        Entity current = inspected;
        if (current != null && current.isRemoved()) {
            inspected = null;
            return null;
        }
        return current;
    }

    @Override
    public void inspect(@Nullable Entity entity) {
        if (!enabled) {
            return;
        }
        inspected = entity;
        if (entity != null) {
            flags[DebugFlag.ENTITY_INSPECTOR.ordinal()] = true;
        }
    }

    // ================================================================== engine hooks

    /**
     * Counts a frame for the statistics.
     *
     * @param nanos the frame time stamp
     */
    public void frame(long nanos) {
        if (lastFrameNanos != 0L) {
            frameMillis = (nanos - lastFrameNanos) / 1e6f;
            frameTimes[frameIndex] = frameMillis;
            frameIndex = (frameIndex + 1) % GRAPH;
        }
        lastFrameNanos = nanos;
        fpsFrames++;
        if (fpsWindowStart == 0L) {
            fpsWindowStart = nanos;
        } else if (nanos - fpsWindowStart >= 1_000_000_000L) {
            fps = fpsFrames * 1e9f / (nanos - fpsWindowStart);
            fpsFrames = 0;
            fpsWindowStart = nanos;
        }
    }

    /**
     * Records how long a game tick took and advances the shapes' lifetimes.
     *
     * @param nanos the duration
     * @param measuredTps ticks per second over the last second
     */
    public void ticked(long nanos, float measuredTps) {
        tickMillis = nanos / 1e6f;
        tps = measuredTps;
        profiler.tick(nanos);
        shapes.tick();
    }

    /**
     * Records how long a frame took to draw.
     *
     * @param nanos the duration
     */
    public void rendered(long nanos) {
        renderMillis = nanos / 1e6f;
        profiler.render(nanos);
    }

    /**
     * Stops the profiler and reports: to the log, and to {@code profiles/profile-<date>.txt}.
     *
     * @param epochMillis the time stamp of the file name
     * @return the report
     */
    public ProfileReport stopAndReport(long epochMillis) {
        ProfileReport report = profiler.stop();
        String text = report.text();
        logger.info(text);
        String file = "profiles/profile-" + Timestamps.fileStamp(epochMillis) + ".txt";
        environment.writeFile(file, text.getBytes(StandardCharsets.UTF_8));
        return report;
    }

    // ================================================================== input

    @Override
    public boolean keyDown(KeyboardKey key) {
        if (!enabled) {
            return false;
        }
        if (key.equals(Keys.F3)) {
            f3Held = true;
            comboUsed = false;
            return true;
        }
        if (!f3Held) {
            return false;
        }
        for (DebugFlag flag : DebugFlag.values()) {
            if (key.equals(keyOf(flag))) {
                toggle(flag);
                comboUsed = true;
                logger.info("Debug " + flag.commandName() + (isOn(flag) ? " on" : " off"));
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the key pressed with F3 for a flag.
     *
     * @param flag the flag
     * @return the key
     */
    public static KeyboardKey keyOf(DebugFlag flag) {
        return switch (flag) {
            case COLLISION -> Keys.C;
            case NAVIGATION -> Keys.N;
            case CHUNKS -> Keys.G;
            case LIGHTS -> Keys.L;
            case UI_INSPECTOR -> Keys.U;
            case ENTITY_INSPECTOR -> Keys.E;
        };
    }

    @Override
    public void keyUp(KeyboardKey key) {
        if (key.equals(Keys.F3) && f3Held) {
            f3Held = false;
            if (!comboUsed) {
                setOverlayVisible(!overlay);
            }
        }
    }

    @Override
    public boolean mouseDown(MouseButton button) {
        if (!enabled || button != MouseButton.LEFT || !flags[DebugFlag.ENTITY_INSPECTOR.ordinal()]) {
            return false;
        }
        World world = environment.activeWorld();
        if (world == null) {
            return false;
        }
        Vec2 point = environment.mouseWorld(world);
        List<Entity> entities = world.entities();
        for (int i = entities.size() - 1; i >= 0; i--) {
            Entity entity = entities.get(i);
            if (entity.bounds().contains(point)) {
                inspected = entity;
                return true;
            }
        }
        return false;
    }

    // ================================================================== drawing

    /**
     * Draws the shapes in the active world's camera.
     *
     * @param draw the drawing, in world units
     * @param pixel one screen pixel in world units
     */
    public void drawWorld(Draw draw, float pixel) {
        shapes.render(draw, pixel);
        Entity current = inspected();
        if (current != null && flags[DebugFlag.ENTITY_INSPECTOR.ordinal()]) {
            draw.color(SLOW).rectOutline(current.bounds(), pixel * 2f);
        }
    }

    /**
     * Draws the F3 overlay and the entity inspector, in UI points.
     *
     * @param draw the drawing
     * @param width the UI width
     * @param height the UI height
     */
    public void drawOverlay(Draw draw, float width, float height) {
        if (!enabled) {
            return;
        }
        Color previous = draw.color();
        if (overlay) {
            drawStats(draw);
        }
        Entity current = inspected();
        if (current != null && flags[DebugFlag.ENTITY_INSPECTOR.ordinal()]) {
            drawInspector(draw, current, width);
        }
        draw.color(previous);
    }

    private void drawStats(Draw draw) {
        Stats stats = stats();
        TextStyle style = TextStyle.of(12).color(Color.WHITE);
        List<String> lines = new ArrayList<>();
        lines.add(round(stats.fps()) + " fps  " + fixed(stats.frameMillis()) + " ms  render "
                + fixed(stats.renderMillis()) + " ms");
        lines.add(round(stats.tps()) + " tps  tick " + fixed(stats.tickMillis()) + " ms");
        lines.add(stats.entities() + " entities  " + stats.chunks() + " chunks  " + stats.particles() + " particles  "
                + stats.sounds() + " sounds");
        lines.add(stats.drawCalls() + " draw calls  " + stats.textureBinds() + " texture binds");
        if (stats.memoryUsed() >= 0) {
            lines.add("memory " + stats.memoryUsed() / 1_048_576 + " / " + stats.memoryMax() / 1_048_576 + " MB");
        }
        World world = environment.activeWorld();
        if (world != null) {
            Vec2 mouse = environment.mouseWorld(world);
            int tileX = (int) Math.floor(mouse.x());
            int tileY = (int) Math.floor(mouse.y());
            lines.add(world.name() + "  cursor " + fixed(mouse.x()) + ", " + fixed(mouse.y()) + "  tile " + tileX + ", "
                    + tileY);
        }
        StringBuilder toggles = new StringBuilder("F3+");
        for (DebugFlag flag : DebugFlag.values()) {
            toggles.append(' ').append(flag.key()).append(isOn(flag) ? "*" : "");
        }
        if (profiler.isRunning()) {
            toggles.append("  profiling");
        }
        lines.add(toggles.toString());
        float lineHeight = 15f;
        float panelWidth = 300f;
        float panelHeight = lines.size() * lineHeight + 58f;
        draw.color(PANEL).rect(4f, 4f, panelWidth, panelHeight);
        draw.color(Color.WHITE);
        float y = 8f;
        for (String line : lines) {
            draw.text(line, 10f, y, style);
            y += lineHeight;
        }
        // Frame time graph: one bar per frame, 16.7 ms at the middle line.
        float graphTop = y + 4f;
        float graphHeight = 40f;
        float barWidth = (panelWidth - 12f) / GRAPH;
        for (int i = 0; i < GRAPH; i++) {
            float ms = frameTimes[(frameIndex + i) % GRAPH];
            float h = Math.min(graphHeight, ms / 33.3f * graphHeight);
            draw.color(ms <= 17.5f ? GOOD : ms <= 34f ? SLOW : BAD)
                    .rect(10f + i * barWidth, graphTop + graphHeight - h, Math.max(1f, barWidth - 0.5f), h);
        }
        draw.color(Color.WHITE.withAlpha(0.4f)).rect(10f, graphTop + graphHeight / 2f, panelWidth - 12f, 1f);
    }

    private void drawInspector(Draw draw, Entity entity, float width) {
        String text = describe(entity);
        TextStyle style = TextStyle.of(12).color(Color.WHITE);
        String[] lines = text.split("\n");
        float lineHeight = 15f;
        float panelWidth = 340f;
        float x = Math.max(4f, width - panelWidth - 4f);
        draw.color(PANEL).rect(x, 4f, panelWidth, lines.length * lineHeight + 8f);
        draw.color(Color.WHITE);
        float y = 8f;
        for (String line : lines) {
            draw.text(line, x + 6f, y, style);
            y += lineHeight;
        }
    }

    /**
     * Describes an entity for the inspector: type, id, name, position, tags, data and components with their saved
     * fields.
     *
     * @param entity the entity
     * @return several lines
     */
    public String describe(Entity entity) {
        StringBuilder out = new StringBuilder();
        out.append(entity.type().key());
        if (entity.name() != null) {
            out.append("  \"").append(entity.name()).append('"');
        }
        out.append("\nid ").append(entity.id());
        out.append("\npos ")
                .append(fixed(entity.x()))
                .append(", ")
                .append(fixed(entity.y()))
                .append("  rot ")
                .append(fixed(entity.rotation()))
                .append("  size ")
                .append(fixed(entity.size().x()))
                .append(" x ")
                .append(fixed(entity.size().y()));
        out.append("\ntags ")
                .append(
                        entity.tags().all().isEmpty()
                                ? "-"
                                : String.join(", ", entity.tags().all()));
        if (!entity.data().isEmpty()) {
            out.append("\ndata ").append(((dev.gulp.core.data.DataContainerImpl) entity.data()).toJson());
        }
        for (Component component : entity.components()) {
            String name = component.getClass().getSimpleName();
            out.append("\n- ").append(name.isEmpty() ? component.getClass().getName() : name);
            if (!component.isEnabled()) {
                out.append(" (disabled)");
            }
            JsonObject fields = componentState.apply(component);
            if (fields != null && fields.size() > 0) {
                out.append(' ').append(fields);
            }
        }
        return out.toString();
    }

    private static String round(float value) {
        return Integer.toString(Math.round(value));
    }

    private static String fixed(float value) {
        long tenths = Math.round(value * 10.0);
        String sign = tenths < 0 ? "-" : "";
        long abs = Math.abs(tenths);
        return sign + abs / 10 + "." + abs % 10;
    }

    // ================================================================== shapes

    /** Shapes with a lifetime; kept in parallel arrays, so drawing them allocates nothing. */
    public static final class Shapes implements DebugDraw {
        private static final int LINE = 0;
        private static final int RECT = 1;
        private static final int CIRCLE = 2;
        private static final int ARROW = 3;
        private static final int TEXT = 4;

        private final boolean enabled;
        private int size;
        private int[] kinds = new int[32];
        private float[] values = new float[32 * 4];
        private Color[] colors = new Color[32];
        private int[] ticks = new int[32];
        private @Nullable String[] texts = new String[32];

        Shapes(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns the shapes waiting to be drawn.
         *
         * @return the count
         */
        public int size() {
            return size;
        }

        @Override
        public DebugDraw line(Vec2 from, Vec2 to, Color color, int lifetime) {
            return add(LINE, from.x(), from.y(), to.x(), to.y(), color, lifetime, null);
        }

        @Override
        public DebugDraw rect(Rect rect, Color color, int lifetime) {
            return add(RECT, rect.x(), rect.y(), rect.width(), rect.height(), color, lifetime, null);
        }

        @Override
        public DebugDraw circle(Vec2 center, float radius, Color color, int lifetime) {
            return add(CIRCLE, center.x(), center.y(), radius, 0f, color, lifetime, null);
        }

        @Override
        public DebugDraw arrow(Vec2 from, Vec2 to, Color color, int lifetime) {
            return add(ARROW, from.x(), from.y(), to.x(), to.y(), color, lifetime, null);
        }

        @Override
        public DebugDraw text(Vec2 at, String text, Color color, int lifetime) {
            return add(TEXT, at.x(), at.y(), 0f, 0f, color, lifetime, text);
        }

        @Override
        public void clear() {
            java.util.Arrays.fill(colors, 0, size, null);
            java.util.Arrays.fill(texts, 0, size, null);
            size = 0;
        }

        private DebugDraw add(
                int kind, float a, float b, float c, float d, Color color, int lifetime, @Nullable String text) {
            if (!enabled) {
                return this;
            }
            if (size == kinds.length) {
                int capacity = size * 2;
                kinds = java.util.Arrays.copyOf(kinds, capacity);
                values = java.util.Arrays.copyOf(values, capacity * 4);
                colors = java.util.Arrays.copyOf(colors, capacity);
                ticks = java.util.Arrays.copyOf(ticks, capacity);
                texts = java.util.Arrays.copyOf(texts, capacity);
            }
            kinds[size] = kind;
            values[size * 4] = a;
            values[size * 4 + 1] = b;
            values[size * 4 + 2] = c;
            values[size * 4 + 3] = d;
            colors[size] = color;
            ticks[size] = Math.max(1, lifetime);
            texts[size] = text;
            size++;
            return this;
        }

        /** Ages the shapes by a tick and drops the expired ones. */
        void tick() {
            int kept = 0;
            for (int i = 0; i < size; i++) {
                if (--ticks[i] > 0) {
                    if (kept != i) {
                        kinds[kept] = kinds[i];
                        System.arraycopy(values, i * 4, values, kept * 4, 4);
                        colors[kept] = colors[i];
                        ticks[kept] = ticks[i];
                        texts[kept] = texts[i];
                    }
                    kept++;
                }
            }
            java.util.Arrays.fill(colors, kept, size, null);
            java.util.Arrays.fill(texts, kept, size, null);
            size = kept;
        }

        void render(Draw draw, float pixel) {
            float width = pixel * 1.5f;
            TextStyle style = null;
            for (int i = 0; i < size; i++) {
                float a = values[i * 4];
                float b = values[i * 4 + 1];
                float c = values[i * 4 + 2];
                float d = values[i * 4 + 3];
                draw.color(colors[i]);
                switch (kinds[i]) {
                    case LINE -> draw.line(a, b, c, d, width);
                    case RECT -> draw.rectOutline(a, b, c, d, width);
                    case CIRCLE -> draw.circleOutline(a, b, c, width);
                    case ARROW -> {
                        draw.line(a, b, c, d, width);
                        float dx = c - a;
                        float dy = d - b;
                        float length = (float) Math.sqrt(dx * dx + dy * dy);
                        if (length > 0f) {
                            float head = Math.min(length * 0.4f, pixel * 10f);
                            float ux = dx / length;
                            float uy = dy / length;
                            draw.line(c, d, c - head * (ux - uy * 0.5f), d - head * (uy + ux * 0.5f), width);
                            draw.line(c, d, c - head * (ux + uy * 0.5f), d - head * (uy - ux * 0.5f), width);
                        }
                    }
                    default -> {
                        if (style == null) {
                            style = TextStyle.of(pixel * 12f);
                        }
                        String text = texts[i];
                        if (text != null) {
                            draw.text(text, a, b, style.color(colors[i]), TextAlign.CENTER);
                        }
                    }
                }
            }
        }
    }

    /**
     * Writes bytes as a user file through the platform, logging failures.
     *
     * @param files the platform files
     * @param logger where failures go
     * @param name the file name
     * @param content the content
     */
    public static void write(dev.gulp.platform.PlatformFiles files, Logger logger, String name, byte[] content) {
        files.writeUserData(name, ByteBuffer.wrap(content), new dev.gulp.platform.PlatformCallback<>() {
            @Override
            public void success(@Nullable Void result) {
                logger.info("Wrote " + name);
            }

            @Override
            public void failure(Throwable error) {
                logger.warn("Could not write " + name + ": " + error.getMessage());
            }
        });
    }
}
