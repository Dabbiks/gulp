package dev.gulp.core.graphics;

import dev.gulp.api.render.AspectMode;
import dev.gulp.api.render.StretchMode;

/**
 * Where the game area lies in the window and how large it is in logical units, computed from the framebuffer size and
 * the display settings.
 *
 * @param viewportX left edge of the game area in framebuffer pixels
 * @param viewportY top edge of the game area in framebuffer pixels
 * @param viewportWidth width of the game area in framebuffer pixels
 * @param viewportHeight height of the game area in framebuffer pixels
 * @param logicalWidth visible width in logical units
 * @param logicalHeight visible height in logical units
 */
public record DisplayLayout(
        int viewportX, int viewportY, int viewportWidth, int viewportHeight, float logicalWidth, float logicalHeight) {

    /**
     * Computes the layout.
     *
     * @param framebufferWidth window width in pixels
     * @param framebufferHeight window height in pixels
     * @param contentScale pixels per window point
     * @param baseWidth base resolution width, 0 if none
     * @param baseHeight base resolution height, 0 if none
     * @param stretch the stretch mode
     * @param aspect the aspect mode
     * @param integerScaling whether to scale by whole multiples
     * @return the layout
     */
    public static DisplayLayout compute(
            int framebufferWidth,
            int framebufferHeight,
            float contentScale,
            int baseWidth,
            int baseHeight,
            StretchMode stretch,
            AspectMode aspect,
            boolean integerScaling) {
        int fw = Math.max(1, framebufferWidth);
        int fh = Math.max(1, framebufferHeight);
        if (stretch == StretchMode.DISABLED || baseWidth <= 0 || baseHeight <= 0) {
            float scale = contentScale > 0f ? contentScale : 1f;
            return new DisplayLayout(0, 0, fw, fh, fw / scale, fh / scale);
        }
        float sx = fw / (float) baseWidth;
        float sy = fh / (float) baseHeight;
        switch (aspect) {
            case IGNORE -> {
                return new DisplayLayout(0, 0, fw, fh, baseWidth, baseHeight);
            }
            case KEEP -> {
                float s = scale(Math.min(sx, sy), integerScaling);
                return centered(fw, fh, Math.round(baseWidth * s), Math.round(baseHeight * s), baseWidth, baseHeight);
            }
            case KEEP_WIDTH -> {
                float s = scale(sx, integerScaling);
                int w = Math.round(baseWidth * s);
                return centered(fw, fh, w, fh, baseWidth, fh / s);
            }
            case KEEP_HEIGHT -> {
                float s = scale(sy, integerScaling);
                int h = Math.round(baseHeight * s);
                return centered(fw, fh, fw, h, fw / s, baseHeight);
            }
            case EXPAND -> {
                float s = scale(Math.min(sx, sy), integerScaling);
                if (!integerScaling) {
                    return new DisplayLayout(0, 0, fw, fh, fw / s, fh / s);
                }
                // Whole base pixels only, so pixel art stays crisp.
                int logicalW = (int) (fw / s);
                int logicalH = (int) (fh / s);
                return centered(fw, fh, Math.round(logicalW * s), Math.round(logicalH * s), logicalW, logicalH);
            }
            default -> throw new IllegalStateException("Unknown aspect mode " + aspect);
        }
    }

    private static float scale(float value, boolean integer) {
        if (!integer || value < 1f) {
            return value;
        }
        return (float) Math.floor(value);
    }

    private static DisplayLayout centered(int fw, int fh, int w, int h, float logicalW, float logicalH) {
        int width = Math.min(fw, Math.max(1, w));
        int height = Math.min(fh, Math.max(1, h));
        return new DisplayLayout((fw - width) / 2, (fh - height) / 2, width, height, logicalW, logicalH);
    }

    /**
     * Framebuffer pixels per logical unit horizontally.
     *
     * @return the scale
     */
    public float scaleX() {
        return viewportWidth / logicalWidth;
    }

    /**
     * Framebuffer pixels per logical unit vertically.
     *
     * @return the scale
     */
    public float scaleY() {
        return viewportHeight / logicalHeight;
    }
}
