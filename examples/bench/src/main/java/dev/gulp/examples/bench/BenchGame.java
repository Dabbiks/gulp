package dev.gulp.examples.bench;

import static dev.gulp.api.ui.Ui.*;

import dev.gulp.api.Game;
import dev.gulp.api.GameSettings;
import dev.gulp.api.Gulp;
import dev.gulp.api.debug.Stats;
import dev.gulp.api.input.KeyPressEvent;
import dev.gulp.api.input.Keys;
import dev.gulp.api.ui.Anchor;
import dev.gulp.api.ui.Node;
import dev.gulp.api.ui.State;
import dev.gulp.api.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Performance scenes of section 20.5 to watch with the F3 overlay: keys 1 to 5 switch between sprites, entities,
 * particles, UI and a tile map at the counts the budgets name. With {@code -Dgulp.bench.auto=true} (for example
 * {@code ./gradlew :examples:bench:runDesktop -Pgulp.bench}) every scene runs for a while, the averages go to the log
 * as {@code BENCH} lines and the game closes.
 */
public final class BenchGame extends Game {

    private static final int WARMUP_FRAMES = 120;
    private static final int MEASURED_FRAMES = 300;

    private final State<String> status = State.of("");
    private @Nullable Node<?> statusPanel;
    private @Nullable World current;
    private BenchScene scene = BenchScene.SPRITES;
    private final boolean auto = Boolean.getBoolean("gulp.bench.auto");
    private final List<BenchScene> queue = new ArrayList<>();
    private int frames;
    private double frameSum;
    private double tickSum;
    private double renderSum;

    /** Creates the game. */
    public BenchGame() {}

    /**
     * Starts the benchmark on the best available backend.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Gulp.launch(new BenchGame());
    }

    @Override
    public String id() {
        return "bench";
    }

    @Override
    public void configure(GameSettings settings) {
        settings.title("Gulp Bench").windowSize(1280, 720).vsync(false).debugTools(true);
    }

    @Override
    public void onStart() {
        statusPanel = ui().hud()
                .add(this, panel(label(status)).anchor(Anchor.BOTTOM_LEFT).offset(4, -4));
        on(KeyPressEvent.class, e -> {
            int index = e.key().equals(Keys.NUM_1)
                    ? 0
                    : e.key().equals(Keys.NUM_2)
                            ? 1
                            : e.key().equals(Keys.NUM_3)
                                    ? 2
                                    : e.key().equals(Keys.NUM_4) ? 3 : e.key().equals(Keys.NUM_5) ? 4 : -1;
            if (index >= 0) {
                show(BenchScene.values()[index]);
            }
        });
        debug().setOverlayVisible(true);
        if (auto) {
            queue.addAll(List.of(BenchScene.values()));
            show(queue.remove(0));
        } else {
            show(BenchScene.SPRITES);
        }
        every(1, this::measure);
    }

    private void show(BenchScene next) {
        World previous = current;
        for (Node<?> node : List.copyOf(ui().hud().nodes())) {
            if (node != statusPanel) {
                ui().hud().remove(node);
            }
        }
        scene = next;
        int count = next.count(engine().platform().isWeb());
        World world = next.build(this, count);
        current = world;
        worlds().switchTo(world.name()).thenSync(active -> {
            if (previous != null) {
                worlds().unload(previous.name());
            }
        });
        frames = 0;
        frameSum = 0;
        tickSum = 0;
        renderSum = 0;
        status.set(next + " x " + count + "   (1 sprites  2 entities  3 particles  4 UI  5 map)");
    }

    private void measure() {
        frames++;
        if (frames <= WARMUP_FRAMES) {
            return;
        }
        Stats stats = debug().stats();
        frameSum += stats.frameMillis();
        tickSum += stats.tickMillis();
        renderSum += stats.renderMillis();
        int measured = frames - WARMUP_FRAMES;
        if (measured == MEASURED_FRAMES && auto) {
            logger().info(String.format(
                    Locale.ROOT,
                    "BENCH scene=%s count=%d frameMs=%.2f tickMs=%.2f renderMs=%.2f",
                    scene.name().toLowerCase(Locale.ROOT),
                    scene.count(engine().platform().isWeb()),
                    frameSum / measured,
                    tickSum / measured,
                    renderSum / measured));
            if (queue.isEmpty()) {
                engine().stop();
            } else {
                show(queue.remove(0));
            }
        }
    }
}
