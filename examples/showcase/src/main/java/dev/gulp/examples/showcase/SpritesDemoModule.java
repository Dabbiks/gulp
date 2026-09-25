package dev.gulp.examples.showcase;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.command.Arguments;
import dev.gulp.api.command.Command;
import dev.gulp.api.event.lifecycle.TickStartEvent;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.TextureFilter;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.math.Mathf;
import dev.gulp.api.math.Rect;
import dev.gulp.api.math.Rng;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleInfo;
import dev.gulp.api.render.Draw;
import dev.gulp.api.render.RenderLayerEvent;
import dev.gulp.api.render.RenderStats;
import java.time.Duration;

/**
 * Stages 2 and 3 demo: many bouncing coins (a region of the sprites atlas, loaded in the startup group) and balls (drawn into a {@code Pixmap}),
 * with interpolation, shapes on the UI layer, and render statistics in the
 * log.
 *
 * <p>Try in the terminal: {@code /sprites 50000}, {@code /zoom 2}.
 */
@ModuleInfo(id = "sprites")
final class SpritesDemoModule extends GameModule {

    private static final int DEFAULT_COUNT = 10_000;
    private static final AssetKey<TextureRegion> COIN = GameAssets.Sprites.COIN;

    private final Rng rng = new Rng(42);
    private TextureRegion[] sprites = new TextureRegion[0];
    private int count;
    private float[] x = new float[0];
    private float[] y = new float[0];
    private float[] previousX = new float[0];
    private float[] previousY = new float[0];
    private float[] velocityX = new float[0];
    private float[] velocityY = new float[0];
    private float time;

    @Override
    public void onLoad() {
        // Loaded before Game.onStart behind the loading screen, so onEnable can use it.
        assets().startup().add(COIN);
    }

    @Override
    public void onEnable() {
        TextureRegion coin = assets().get(COIN);
        coin.texture().setFilter(TextureFilter.NEAREST);
        sprites = new TextureRegion[] {
            coin, graphics().texture(ball(), TextureFilter.NEAREST).region()
        };
        spawn(DEFAULT_COUNT);
        on(TickStartEvent.class, e -> update(1f / engine().targetTps()));
        on(RenderLayerEvent.class, e -> {
            if (ShowcaseGame.screen() == JuiceDemoModule.SCREEN) {
                return;
            }
            if (e.layer().name().equals("entities")) {
                drawWorld(e.draw(), e.alpha());
            } else if (e.layer().name().equals("ui")) {
                drawUi(e.draw());
            }
        });
        every(Duration.ofSeconds(2), () -> {
            RenderStats stats = display().stats();
            logger().info(count + " sprites, " + Math.round(display().fps()) + " FPS, " + stats.drawCalls()
                    + " draw calls, " + stats.vertices() + " vertices");
        });
        commands()
                .register(
                        this,
                        Command.builder("sprites")
                                .description("Sets the number of bouncing sprites")
                                .argument(Arguments.integer("count", 0, 1_000_000))
                                .executes(ctx -> {
                                    int requested = ctx.arg("count");
                                    spawn(requested);
                                    ctx.reply(count + " sprites");
                                })
                                .build());
        commands()
                .register(
                        this,
                        Command.builder("zoom")
                                .description("Sets the camera zoom")
                                .argument(Arguments.floating("zoom", 0.1f, 10f))
                                .executes(ctx -> {
                                    float zoom = ctx.arg("zoom");
                                    display().camera().setZoom(zoom);
                                    ctx.reply("Zoom " + zoom);
                                })
                                .build());
    }

    private static Pixmap ball() {
        Pixmap pixmap = new Pixmap(16, 16);
        pixmap.fillCircle(8, 8, 7, Color.WHITE);
        pixmap.fillCircle(6, 6, 2, Color.rgb(0xfff1e8));
        pixmap.drawCircle(8, 8, 7, Color.GRAY);
        return pixmap;
    }

    private void spawn(int newCount) {
        count = newCount;
        x = new float[count];
        y = new float[count];
        previousX = new float[count];
        previousY = new float[count];
        velocityX = new float[count];
        velocityY = new float[count];
        Rect bounds = display().camera().bounds();
        for (int i = 0; i < count; i++) {
            x[i] = previousX[i] = bounds.x() + rng.nextFloat() * bounds.width();
            y[i] = previousY[i] = bounds.y() + rng.nextFloat() * bounds.height();
            float angle = rng.nextFloat() * 360f;
            float speed = 2f + rng.nextFloat() * 8f;
            velocityX[i] = Mathf.cosDeg(angle) * speed;
            velocityY[i] = Mathf.sinDeg(angle) * speed;
        }
    }

    private void update(float delta) {
        if (ShowcaseGame.screen() == JuiceDemoModule.SCREEN) {
            // Hidden there, and the juice camera would squeeze the coins into its smaller view.
            return;
        }
        time += delta;
        Rect bounds = display().camera().bounds();
        float right = bounds.right() - 1f;
        float bottom = bounds.bottom() - 1f;
        for (int i = 0; i < count; i++) {
            previousX[i] = x[i];
            previousY[i] = y[i];
            x[i] += velocityX[i] * delta;
            y[i] += velocityY[i] * delta;
            if (x[i] < bounds.x() || x[i] > right) {
                velocityX[i] = -velocityX[i];
                x[i] = Mathf.clamp(x[i], bounds.x(), right);
            }
            if (y[i] < bounds.y() || y[i] > bottom) {
                velocityY[i] = -velocityY[i];
                y[i] = Mathf.clamp(y[i], bounds.y(), bottom);
            }
        }
    }

    private void drawWorld(Draw draw, float alpha) {
        // One pass per texture keeps each in a single batch: even indices are coins, odd ones balls.
        for (int t = 0; t < sprites.length; t++) {
            for (int i = t; i < count; i += sprites.length) {
                draw.image(
                        sprites[t],
                        Mathf.lerp(previousX[i], x[i], alpha),
                        Mathf.lerp(previousY[i], y[i], alpha),
                        1f,
                        1f);
            }
        }
    }

    private void drawUi(Draw draw) {
        draw.color(Color.rgb(0x000000).withAlpha(0.5f)).roundedRect(new Rect(8, 8, 180, 36), 8);
        float pulse = 0.5f + 0.5f * Mathf.sinDeg(time * 180f);
        draw.color(Color.ORANGE).circle(26, 26, 8 + 3 * pulse);
        draw.color(Color.CYAN).arc(60, 26, 10, -90, 360f * (time % 2f) / 2f);
        draw.color(Color.WHITE).line(80, 26, 180, 26, 2);
        draw.color(Color.WHITE);
    }
}
