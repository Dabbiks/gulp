package dev.gulp.api.ui;

import dev.gulp.api.Gulp;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.FrameBuffer;
import dev.gulp.api.graphics.Material;
import dev.gulp.api.graphics.Mesh2D;
import dev.gulp.api.graphics.Shader;
import dev.gulp.api.graphics.TextureFilter;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.registry.Key;
import dev.gulp.api.render.Draw;
import org.jspecify.annotations.Nullable;

/**
 * Built-in transitions. The engine registers them with default lengths in {@code Registries.TRANSITION} as {@code
 * gulp:fade}, {@code gulp:slide}, {@code gulp:circle_wipe} and {@code gulp:pixelate}.
 *
 * <pre>{@code
 * worlds().switchTo("cave", Transitions.fadeColor(Color.WHITE, 0.3f));
 * worlds().switchTo("shop", Transitions.slide(Vec2.LEFT, 0.4f));
 * }</pre>
 */
public final class Transitions {

    private Transitions() {}

    /**
     * Fades through black.
     *
     * @param seconds length of each half
     * @return the transition
     */
    public static Transition fade(float seconds) {
        return fadeColor(Color.BLACK, seconds);
    }

    /**
     * Fades through a colour.
     *
     * @param color the colour
     * @param seconds length of each half
     * @return the transition
     */
    public static Transition fadeColor(Color color, float seconds) {
        return new Simple(key("fade"), seconds) {
            @Override
            public void draw(Draw draw, TransitionFrame frame) {
                draw.color(color.withAlpha(color.a() * frame.coverage())).rect(0, 0, frame.width(), frame.height());
            }
        };
    }

    /**
     * Slides a black panel over the screen and away in the same direction.
     *
     * @param direction where the panel moves, for example {@link Vec2#LEFT}
     * @param seconds length of each half
     * @return the transition
     */
    public static Transition slide(Vec2 direction, float seconds) {
        Vec2 unit = direction.normalized();
        return new Simple(key("slide"), seconds) {
            @Override
            public void draw(Draw draw, TransitionFrame frame) {
                float shift = frame.entering() ? frame.coverage() - 1f : 1f - frame.coverage();
                float x = -unit.x() * shift * frame.width();
                float y = -unit.y() * shift * frame.height();
                draw.color(Color.BLACK).rect(x, y, frame.width(), frame.height());
            }
        };
    }

    /**
     * Closes a circle to the centre of the screen and opens it again.
     *
     * @param seconds length of each half
     * @return the transition
     */
    public static Transition circleWipe(float seconds) {
        return new Simple(key("circle_wipe"), seconds) {
            private static final int SEGMENTS = 64;
            // One mesh without anti-aliased edges: separate shapes would leave seams where their fringes meet.
            private final Mesh2D ring = new Mesh2D();

            @Override
            public void draw(Draw draw, TransitionFrame frame) {
                float cx = frame.width() / 2f;
                float cy = frame.height() / 2f;
                float outer = (float) Math.sqrt(cx * cx + cy * cy) + 2f;
                float inner = outer * (1f - frame.coverage());
                ring.clear();
                for (int i = 0; i < SEGMENTS; i++) {
                    double angle = Math.PI * 2 * i / SEGMENTS;
                    float cos = (float) Math.cos(angle);
                    float sin = (float) Math.sin(angle);
                    ring.vertex(cx + cos * inner, cy + sin * inner, 0f, 0f, Color.BLACK);
                    ring.vertex(cx + cos * outer, cy + sin * outer, 0f, 0f, Color.BLACK);
                }
                for (int i = 0; i < SEGMENTS; i++) {
                    int a = i * 2;
                    int b = ((i + 1) % SEGMENTS) * 2;
                    ring.triangle(a, a + 1, b + 1).triangle(a, b + 1, b);
                }
                draw.color(Color.WHITE).mesh(ring, null);
            }
        };
    }

    /**
     * Breaks the picture into ever larger blocks, then sharpens the new one.
     *
     * @param seconds length of each half
     * @return the transition
     */
    public static Transition pixelate(float seconds) {
        return new FrameTransition(key("pixelate"), seconds) {
            private @Nullable FrameBuffer small;

            @Override
            public void draw(Draw draw, TransitionFrame frame) {
                TextureRegion screen = frame.screen();
                if (screen == null) {
                    return;
                }
                float block = 1f + 31f * frame.coverage() * frame.coverage();
                int width = Math.max(1, Math.round(frame.width() / block));
                int height = Math.max(1, Math.round(frame.height() / block));
                FrameBuffer target = small;
                if (target == null || target.width() != width || target.height() != height) {
                    if (target != null) {
                        target.dispose();
                    }
                    target = Gulp.engine().graphics().frameBuffer(width, height);
                    target.texture().setFilter(TextureFilter.NEAREST);
                    small = target;
                }
                draw.into(target, d -> d.color(Color.WHITE).image(screen, 0, 0, width, height));
                draw.color(Color.WHITE).image(target.region(), 0, 0, frame.width(), frame.height());
                draw.color(Color.BLACK.withAlpha(frame.coverage() * frame.coverage()))
                        .rect(0, 0, frame.width(), frame.height());
            }
        };
    }

    /**
     * Draws the picture through a shader, which gets {@code u_coverage} ({@code 0..1}) as a uniform.
     *
     * @param shader the shader
     * @param seconds length of each half
     * @return the transition
     */
    public static Transition shader(Shader shader, float seconds) {
        return new FrameTransition(key("shader"), seconds) {
            @Override
            public void draw(Draw draw, TransitionFrame frame) {
                TextureRegion screen = frame.screen();
                if (screen == null) {
                    return;
                }
                shader.set("u_coverage", frame.coverage());
                draw.material(Material.of(shader))
                        .color(Color.WHITE)
                        .image(screen, 0, 0, frame.width(), frame.height())
                        .material(Material.DEFAULT);
            }
        };
    }

    private static Key key(String path) {
        return Key.of(Key.RESERVED, path);
    }

    /** A transition drawn over the picture. */
    private abstract static class Simple implements Transition {
        private final Key key;
        private final float seconds;

        Simple(Key key, float seconds) {
            if (!(seconds >= 0f)) {
                throw new IllegalArgumentException("Transition length must not be negative: " + seconds);
            }
            this.key = key;
            this.seconds = seconds;
        }

        @Override
        public Key key() {
            return key;
        }

        @Override
        public float duration() {
            return seconds;
        }

        @Override
        public String toString() {
            return "Transition[" + key + ", " + seconds + " s]";
        }
    }

    /** A transition that redraws the picture. */
    private abstract static class FrameTransition extends Simple {
        FrameTransition(Key key, float seconds) {
            super(key, seconds);
        }

        @Override
        public boolean needsFrame() {
            return true;
        }
    }
}
