package dev.gulp.gradle;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code assets { ... }} block: fonts generated at build time. Every {@code sprites/} folder of a namespace is
 * packed into an atlas automatically.
 *
 * <pre>{@code
 * gulp {
 *     assets {
 *         msdfFont("coins:fonts/title", file("art/Inter-Bold.ttf"))
 *         bitmapFont("coins:fonts/pixel", file("art/PixelFont.ttf"), 8, false)
 *     }
 * }
 * }</pre>
 */
public class GulpAssets {

    /**
     * A font to generate.
     *
     * @param key asset key of the result, {@code namespace:path}
     * @param source the TTF or OTF file
     * @param kind {@code msdf} or {@code bitmap}
     * @param size pixels per em (MSDF) or font size (bitmap)
     * @param antialias smooth edges (bitmap fonts)
     */
    public record FontSpec(String key, File source, String kind, int size, boolean antialias) implements Serializable {}

    private final List<FontSpec> fonts = new ArrayList<>();

    /** Creates the block; instantiated by Gradle. */
    public GulpAssets() {}

    /**
     * Generates an MSDF font: sharp at every size, with outline and shadow.
     *
     * @param key asset key of the result, for example {@code coins:fonts/title}
     * @param source the TTF or OTF file
     */
    public void msdfFont(String key, File source) {
        fonts.add(new FontSpec(key, source, "msdf", 40, true));
    }

    /**
     * Generates a bitmap font at one size.
     *
     * @param key asset key of the result
     * @param source the TTF or OTF file
     * @param size the font size in pixels
     * @param antialias whether edges are smoothed; {@code false} for pixel fonts
     */
    public void bitmapFont(String key, File source, int size, boolean antialias) {
        fonts.add(new FontSpec(key, source, "bitmap", size, antialias));
    }

    /**
     * Returns the declared fonts.
     *
     * @return the fonts
     */
    public List<FontSpec> getFonts() {
        return fonts;
    }
}
