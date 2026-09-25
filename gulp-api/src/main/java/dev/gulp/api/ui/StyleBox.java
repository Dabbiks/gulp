package dev.gulp.api.ui;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.NinePatch;
import dev.gulp.api.render.Draw;

/**
 * The background of a node: a flat colour with rounded corners, a border and a shadow, a nine-patch from a texture, or
 * nothing. Drawing does not allocate.
 *
 * <pre>{@code
 * StyleBox card = StyleBox.flat(Color.hex("#1e2233")).radius(8).border(1, Color.hex("#3a4060")).shadow(4, Color.BLACK.withAlpha(0.4f));
 * StyleBox frame = StyleBox.ninePatch(new NinePatch(assets().region(GameAssets.Sprites.FRAME), 4, 4, 4, 4));
 * }</pre>
 */
public sealed interface StyleBox permits StyleBox.Flat, StyleBox.Patch, StyleBox.Empty {

    /** No background. */
    StyleBox NONE = new Empty();

    /**
     * A flat colour.
     *
     * @param color the fill colour
     * @return the box, without corners, border or shadow
     */
    static Flat flat(Color color) {
        return new Flat(color, 0f, 0f, Color.CLEAR, 0f, Color.CLEAR);
    }

    /**
     * A nine-patch stretched over the node.
     *
     * @param patch the patch
     * @return the box
     */
    static Patch ninePatch(NinePatch patch) {
        return new Patch(patch, Color.WHITE);
    }

    /**
     * Draws the box.
     *
     * @param draw where to draw
     * @param x left
     * @param y top
     * @param width width
     * @param height height
     */
    void draw(Draw draw, float x, float y, float width, float height);

    /**
     * Blends towards another box, for smooth state transitions; boxes of different kinds switch at once.
     *
     * @param to the target box
     * @param t {@code 0} gives this box, {@code 1} the target
     * @return the blended box
     */
    default StyleBox lerp(StyleBox to, float t) {
        return t >= 1f ? to : this;
    }

    /**
     * A flat colour box.
     *
     * @param color fill colour
     * @param radius corner radius in UI points
     * @param borderWidth border thickness
     * @param borderColor border colour
     * @param shadowSize how far the shadow reaches below and around
     * @param shadowColor shadow colour
     */
    record Flat(Color color, float radius, float borderWidth, Color borderColor, float shadowSize, Color shadowColor)
            implements StyleBox {

        /**
         * Returns a copy with rounded corners.
         *
         * @param value corner radius
         * @return the new box
         */
        public Flat radius(float value) {
            return new Flat(color, value, borderWidth, borderColor, shadowSize, shadowColor);
        }

        /**
         * Returns a copy with a border.
         *
         * @param width border thickness
         * @param borderColor border colour
         * @return the new box
         */
        public Flat border(float width, Color borderColor) {
            return new Flat(color, radius, width, borderColor, shadowSize, shadowColor);
        }

        /**
         * Returns a copy with a shadow.
         *
         * @param size how far the shadow reaches
         * @param shadowColor shadow colour
         * @return the new box
         */
        public Flat shadow(float size, Color shadowColor) {
            return new Flat(color, radius, borderWidth, borderColor, size, shadowColor);
        }

        /**
         * Returns a copy with another fill colour.
         *
         * @param fill the colour
         * @return the new box
         */
        public Flat color(Color fill) {
            return new Flat(fill, radius, borderWidth, borderColor, shadowSize, shadowColor);
        }

        @Override
        public void draw(Draw draw, float x, float y, float width, float height) {
            if (width <= 0f || height <= 0f) {
                return;
            }
            Color previous = draw.color();
            if (shadowSize > 0f && shadowColor.a() > 0f) {
                draw.color(shadowColor);
                draw.roundedRect(
                        x - shadowSize * 0.25f,
                        y + shadowSize * 0.5f,
                        width + shadowSize * 0.5f,
                        height + shadowSize * 0.5f,
                        radius + shadowSize * 0.5f);
            }
            if (borderWidth > 0f && borderColor.a() > 0f) {
                draw.color(borderColor);
                draw.roundedRect(x, y, width, height, radius);
                if (color.a() > 0f) {
                    draw.color(color);
                    draw.roundedRect(
                            x + borderWidth,
                            y + borderWidth,
                            width - 2 * borderWidth,
                            height - 2 * borderWidth,
                            Math.max(0f, radius - borderWidth));
                }
            } else if (color.a() > 0f) {
                draw.color(color);
                draw.roundedRect(x, y, width, height, radius);
            }
            draw.color(previous);
        }

        @Override
        public StyleBox lerp(StyleBox to, float t) {
            if (t >= 1f) {
                return to;
            }
            if (!(to instanceof Flat other)) {
                return t >= 0.5f ? to : this;
            }
            return new Flat(
                    color.lerp(other.color, t),
                    radius + (other.radius - radius) * t,
                    borderWidth + (other.borderWidth - borderWidth) * t,
                    borderColor.lerp(other.borderColor, t),
                    shadowSize + (other.shadowSize - shadowSize) * t,
                    shadowColor.lerp(other.shadowColor, t));
        }
    }

    /**
     * A nine-patch box.
     *
     * @param patch the patch
     * @param tint colour multiplied over the patch
     */
    record Patch(NinePatch patch, Color tint) implements StyleBox {

        /**
         * Returns a copy with a tint.
         *
         * @param color the tint
         * @return the new box
         */
        public Patch tint(Color color) {
            return new Patch(patch, color);
        }

        @Override
        public void draw(Draw draw, float x, float y, float width, float height) {
            Color previous = draw.color();
            draw.color(previous.mul(tint));
            draw.ninePatch(patch, new dev.gulp.api.math.Rect(x, y, width, height));
            draw.color(previous);
        }
    }

    /** No background. */
    final class Empty implements StyleBox {
        private Empty() {}

        @Override
        public void draw(Draw draw, float x, float y, float width, float height) {}
    }
}
