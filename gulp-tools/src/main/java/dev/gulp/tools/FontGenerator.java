package dev.gulp.tools;

import static org.lwjgl.util.msdfgen.MSDFGen.*;
import static org.lwjgl.util.msdfgen.MSDFGenExt.*;

import dev.gulp.api.data.JsonArray;
import dev.gulp.api.data.JsonObject;
import dev.gulp.core.data.JsonWriter;
import dev.gulp.core.graphics.RectPacker;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.imageio.ImageIO;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.msdfgen.MSDFGenBitmap;
import org.lwjgl.util.msdfgen.MSDFGenBounds;
import org.lwjgl.util.msdfgen.MSDFGenTransform;

/**
 * Generates fonts for the engine from TTF or OTF files:
 *
 * <ul>
 *   <li>{@link #msdf} writes a multi-channel signed distance field atlas (sharp at every size, outline and shadow in the
 *       shader) as {@code <name>.msdf.json} plus {@code <name>_<page>.png};
 *   <li>{@link #bitmap} rasterises one size into a BMFont text file {@code <name>.fnt} plus pages.
 * </ul>
 *
 * <p>Glyph shapes and distance fields come from msdfgen; metrics and kerning come from the JDK's text layout, which
 * reads the kerning of modern fonts from their GPOS table.
 */
public final class FontGenerator {

    /** Code points in the default character set: Latin with extensions, Greek, Cyrillic and common punctuation. */
    public static final int[][] DEFAULT_CHARSET = {
        {0x20, 0x7e}, {0xa0, 0x17f}, {0x370, 0x3ff}, {0x400, 0x45f}, {0x2013, 0x2014}, {0x2018, 0x201e},
        {0x2020, 0x2022}, {0x2026, 0x2026}, {0x2030, 0x2030}, {0x2039, 0x203a}, {0x20ac, 0x20ac}, {0x2122, 0x2122}
    };

    private FontGenerator() {}

    /**
     * Expands code point ranges.
     *
     * @param ranges inclusive {@code {first, last}} pairs
     * @return the code points, sorted
     */
    public static int[] codePoints(int[][] ranges) {
        TreeSet<Integer> set = new TreeSet<>();
        for (int[] range : ranges) {
            for (int c = range[0]; c <= range[1]; c++) {
                set.add(c);
            }
        }
        return set.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Generates an MSDF font.
     *
     * @param fontFile the TTF or OTF file
     * @param output directory for the files
     * @param name base name of the files
     * @param codePoints characters to include; missing ones are skipped
     * @param emSize pixels per em in the atlas
     * @param distanceRange width of the distance field in atlas pixels
     * @return the number of glyphs written
     * @throws IOException if a file cannot be read or written
     */
    public static int msdf(Path fontFile, Path output, String name, int[] codePoints, int emSize, int distanceRange)
            throws IOException {
        byte[] data = Files.readAllBytes(fontFile);
        Font awt = awtFont(data);
        Metrics metrics = metrics(awt);
        List<Glyph> glyphs = new ArrayList<>();
        ByteBuffer fontData = MemoryUtil.memAlloc(data.length);
        fontData.put(data).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pointer = stack.mallocPointer(1);
            check(msdf_ft_init(pointer), "msdf_ft_init");
            long freetype = pointer.get(0);
            check(msdf_ft_load_font_data(freetype, fontData, pointer), "msdf_ft_load_font_data");
            long font = pointer.get(0);
            try {
                for (int codePoint : codePoints) {
                    Glyph glyph = msdfGlyph(font, codePoint, emSize, distanceRange);
                    if (glyph != null) {
                        glyphs.add(glyph);
                    }
                }
            } finally {
                msdf_ft_font_destroy(font);
                msdf_ft_deinit(freetype);
            }
        } finally {
            MemoryUtil.memFree(fontData);
        }
        List<BufferedImage> pages = pack(glyphs, 1, BufferedImage.TYPE_INT_RGB);
        writePages(output, name, pages);
        JsonArray.Builder glyphArray = JsonArray.builder();
        for (Glyph glyph : glyphs) {
            JsonObject.Builder object =
                    JsonObject.builder().put("code", glyph.codePoint).put("advance", round(glyph.advance));
            if (glyph.image != null) {
                object.put("page", glyph.page)
                        .put("x", glyph.x)
                        .put("y", glyph.y)
                        .put("w", glyph.image.getWidth())
                        .put("h", glyph.image.getHeight())
                        .put("left", round(glyph.left))
                        .put("top", round(glyph.top))
                        .put("right", round(glyph.right))
                        .put("bottom", round(glyph.bottom));
            }
            glyphArray.add(object.build());
        }
        JsonArray.Builder pageArray = JsonArray.builder();
        for (int i = 0; i < pages.size(); i++) {
            pageArray.add(name + "_" + i + ".png");
        }
        JsonObject json = JsonObject.builder()
                .put("type", "msdf")
                .put("emSize", emSize)
                .put("distanceRange", distanceRange)
                .put("ascent", round(metrics.ascent))
                .put("descent", round(metrics.descent))
                .put("lineHeight", round(metrics.lineHeight))
                .put("pages", pageArray.build())
                .put("glyphs", glyphArray.build())
                .put("kerning", kerning(awt, glyphs))
                .build();
        Files.writeString(output.resolve(name + ".msdf.json"), JsonWriter.write(json, false), StandardCharsets.UTF_8);
        return glyphs.size();
    }

    private static Glyph msdfGlyph(long font, int codePoint, int emSize, int range) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer index = stack.mallocInt(1);
            if (msdf_ft_font_get_glyph_index(font, codePoint, index) != MSDF_SUCCESS
                    || (index.get(0) == 0 && codePoint != ' ')) {
                return null;
            }
            DoubleBuffer advance = stack.mallocDouble(1);
            PointerBuffer shapePointer = stack.mallocPointer(1);
            check(
                    msdf_ft_font_load_glyph(font, codePoint, MSDF_FONT_SCALING_EM_NORMALIZED, advance, shapePointer),
                    "msdf_ft_font_load_glyph");
            long shape = shapePointer.get(0);
            try {
                Glyph glyph = new Glyph(codePoint, advance.get(0));
                PointerBuffer edges = stack.mallocPointer(1);
                msdf_shape_get_edge_count(shape, edges);
                if (edges.get(0) == 0) {
                    return glyph;
                }
                msdf_shape_normalize(shape);
                msdf_shape_edge_colors_simple(shape, 3.0);
                MSDFGenBounds bounds = MSDFGenBounds.calloc(stack);
                msdf_shape_get_bounds(shape, bounds);
                double pad = range / (double) emSize;
                double left = bounds.l() - pad;
                double bottom = bounds.b() - pad;
                double right = bounds.r() + pad;
                double top = bounds.t() + pad;
                int width = (int) Math.ceil((right - left) * emSize);
                int height = (int) Math.ceil((top - bottom) * emSize);
                MSDFGenBitmap bitmap = MSDFGenBitmap.calloc(stack);
                check(msdf_bitmap_alloc(MSDF_BITMAP_TYPE_MSDF, width, height, bitmap), "msdf_bitmap_alloc");
                try {
                    MSDFGenTransform transform = MSDFGenTransform.calloc(stack);
                    transform.scale().x(emSize).y(emSize);
                    transform.translation().x(-left).y(-bottom);
                    transform.distance_mapping().lower(-pad / 2).upper(pad / 2);
                    check(msdf_generate_msdf(bitmap, shape, transform), "msdf_generate_msdf");
                    PointerBuffer pixels = stack.mallocPointer(1);
                    msdf_bitmap_get_pixels(bitmap, pixels);
                    FloatBuffer values = MemoryUtil.memFloatBuffer(pixels.get(0), width * height * 3);
                    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int o = (y * width + x) * 3;
                            int r = channel(values.get(o));
                            int g = channel(values.get(o + 1));
                            int b = channel(values.get(o + 2));
                            // msdfgen rows go bottom-up; images top-down.
                            image.setRGB(x, height - 1 - y, (r << 16) | (g << 8) | b);
                        }
                    }
                    glyph.image = image;
                } finally {
                    msdf_bitmap_free(bitmap);
                }
                // Plane bounds in em with Y down: top is above the baseline, so negative.
                glyph.left = left;
                glyph.right = left + width / (double) emSize;
                glyph.top = -(bottom + height / (double) emSize);
                glyph.bottom = -bottom;
                return glyph;
            } finally {
                msdf_shape_free(shape);
            }
        }
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255f)));
    }

    /**
     * Generates a bitmap font in BMFont text format.
     *
     * @param fontFile the TTF or OTF file
     * @param output directory for the files
     * @param name base name of the files
     * @param codePoints characters to include; missing ones are skipped
     * @param size font size in pixels
     * @param antialias whether edges are smoothed (off for pixel fonts)
     * @return the number of glyphs written
     * @throws IOException if a file cannot be read or written
     */
    public static int bitmap(Path fontFile, Path output, String name, int[] codePoints, int size, boolean antialias)
            throws IOException {
        Font awt = awtFont(Files.readAllBytes(fontFile)).deriveFont((float) size);
        FontRenderContext context = new FontRenderContext(null, antialias, true);
        LineMetrics line = awt.getLineMetrics("Ag", context);
        int base = (int) Math.ceil(line.getAscent());
        int lineHeight = (int) Math.ceil(line.getAscent() + line.getDescent() + line.getLeading());
        List<Glyph> glyphs = new ArrayList<>();
        for (int codePoint : codePoints) {
            if (!awt.canDisplay(codePoint)) {
                continue;
            }
            GlyphVector vector = awt.createGlyphVector(context, Character.toChars(codePoint));
            java.awt.Rectangle bounds = vector.getPixelBounds(context, 0, 0);
            Glyph glyph = new Glyph(codePoint, vector.getGlyphMetrics(0).getAdvanceX());
            if (bounds.width > 0 && bounds.height > 0) {
                BufferedImage image = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D graphics = image.createGraphics();
                graphics.setRenderingHint(
                        RenderingHints.KEY_TEXT_ANTIALIASING,
                        antialias ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
                graphics.setColor(java.awt.Color.WHITE);
                graphics.drawGlyphVector(vector, -bounds.x, -bounds.y);
                graphics.dispose();
                glyph.image = image;
                glyph.left = bounds.x;
                glyph.top = bounds.y + base;
            }
            glyphs.add(glyph);
        }
        List<BufferedImage> pages = pack(glyphs, 1, BufferedImage.TYPE_INT_ARGB);
        writePages(output, name, pages);
        StringBuilder text = new StringBuilder();
        text.append("info face=\"")
                .append(awt.getFamily())
                .append("\" size=")
                .append(size)
                .append(" bold=0 italic=0 charset=\"\" unicode=1 stretchH=100 smooth=")
                .append(antialias ? 1 : 0)
                .append(" aa=1 padding=0,0,0,0 spacing=1,1\n");
        text.append("common lineHeight=")
                .append(lineHeight)
                .append(" base=")
                .append(base)
                .append(" scaleW=")
                .append(pages.get(0).getWidth())
                .append(" scaleH=")
                .append(pages.get(0).getHeight())
                .append(" pages=")
                .append(pages.size())
                .append(" packed=0\n");
        for (int i = 0; i < pages.size(); i++) {
            text.append("page id=")
                    .append(i)
                    .append(" file=\"")
                    .append(name)
                    .append('_')
                    .append(i)
                    .append(".png\"\n");
        }
        text.append("chars count=").append(glyphs.size()).append('\n');
        for (Glyph glyph : glyphs) {
            int w = glyph.image == null ? 0 : glyph.image.getWidth();
            int h = glyph.image == null ? 0 : glyph.image.getHeight();
            text.append("char id=")
                    .append(glyph.codePoint)
                    .append(" x=")
                    .append(glyph.x)
                    .append(" y=")
                    .append(glyph.y)
                    .append(" width=")
                    .append(w)
                    .append(" height=")
                    .append(h)
                    .append(" xoffset=")
                    .append((int) glyph.left)
                    .append(" yoffset=")
                    .append((int) glyph.top)
                    .append(" xadvance=")
                    .append(Math.round(glyph.advance))
                    .append(" page=")
                    .append(glyph.page)
                    .append(" chnl=15\n");
        }
        List<int[]> pairs = kerningPairs(awt.deriveFont(1000f), glyphs);
        text.append("kernings count=").append(pairs.size()).append('\n');
        for (int[] pair : pairs) {
            text.append("kerning first=")
                    .append(pair[0])
                    .append(" second=")
                    .append(pair[1])
                    .append(" amount=")
                    .append(Math.round(pair[2] / 1000f * size))
                    .append('\n');
        }
        Files.writeString(output.resolve(name + ".fnt"), text.toString(), StandardCharsets.UTF_8);
        return glyphs.size();
    }

    // ------------------------------------------------------------------ shared

    private static final class Glyph {
        final int codePoint;
        final double advance;
        BufferedImage image;
        int page;
        int x;
        int y;
        double left;
        double top;
        double right;
        double bottom;

        Glyph(int codePoint, double advance) {
            this.codePoint = codePoint;
            this.advance = advance;
        }
    }

    private record Metrics(double ascent, double descent, double lineHeight) {}

    private static Font awtFont(byte[] data) throws IOException {
        try {
            return Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(data));
        } catch (FontFormatException e) {
            throw new IOException("Not a TrueType or OpenType font: " + e.getMessage(), e);
        }
    }

    /** Ascent, descent and line height in em, from the font's own metrics. */
    private static Metrics metrics(Font font) {
        Font big = font.deriveFont(1000f);
        LineMetrics line = big.getLineMetrics("Ag", new FontRenderContext(null, true, true));
        return new Metrics(
                line.getAscent() / 1000.0,
                line.getDescent() / 1000.0,
                (line.getAscent() + line.getDescent() + line.getLeading()) / 1000.0);
    }

    private static JsonArray kerning(Font font, List<Glyph> glyphs) {
        JsonArray.Builder array = JsonArray.builder();
        for (int[] pair : kerningPairs(font.deriveFont(1000f), glyphs)) {
            array.add(JsonArray.builder()
                    .add(pair[0])
                    .add(pair[1])
                    .add(round(pair[2] / 1000.0))
                    .build());
        }
        return array.build();
    }

    /** Pairs whose distance differs from the sum of advances, as {@code {first, second, amount}} in font units. */
    private static List<int[]> kerningPairs(Font font, List<Glyph> glyphs) {
        Font kerned = font.deriveFont(Map.of(TextAttribute.KERNING, TextAttribute.KERNING_ON));
        FontRenderContext context = new FontRenderContext(null, true, true);
        List<Integer> printable = new ArrayList<>();
        double[] advances = new double[glyphs.size()];
        for (int i = 0; i < glyphs.size(); i++) {
            int c = glyphs.get(i).codePoint;
            if (Character.isLetterOrDigit(c) || ".,:;!?'\"-()".indexOf(c) >= 0) {
                printable.add(i);
            }
            advances[i] = kerned.createGlyphVector(context, Character.toChars(c))
                    .getGlyphMetrics(0)
                    .getAdvanceX();
        }
        List<int[]> pairs = new ArrayList<>();
        char[] text = new char[4];
        for (int a : printable) {
            for (int b : printable) {
                int first = glyphs.get(a).codePoint;
                int second = glyphs.get(b).codePoint;
                int length = Character.toChars(first, text, 0);
                length += Character.toChars(second, text, length);
                GlyphVector vector = kerned.layoutGlyphVector(context, text, 0, length, Font.LAYOUT_LEFT_TO_RIGHT);
                if (vector.getNumGlyphs() != 2) {
                    continue;
                }
                double amount = vector.getGlyphPosition(1).getX() - advances[a];
                if (Math.abs(amount) >= 8) {
                    pairs.add(new int[] {first, second, (int) Math.round(amount)});
                }
            }
        }
        return pairs;
    }

    /** Packs glyph images into as few square-ish pages as possible and records their positions. */
    private static List<BufferedImage> pack(List<Glyph> glyphs, int padding, int imageType) {
        long area = 0;
        int widest = 1;
        for (Glyph glyph : glyphs) {
            if (glyph.image != null) {
                area += (long) (glyph.image.getWidth() + 2 * padding) * (glyph.image.getHeight() + 2 * padding);
                widest = Math.max(widest, Math.max(glyph.image.getWidth(), glyph.image.getHeight()) + 2 * padding);
            }
        }
        int side = 64;
        while ((long) side * side < area * 1.15 || side < widest) {
            side *= 2;
        }
        side = Math.min(side, 4096);
        RectPacker packer = new RectPacker(side, side, padding);
        List<Glyph> sorted = new ArrayList<>(glyphs);
        sorted.sort((a, b) -> Integer.compare(height(b), height(a)));
        for (Glyph glyph : sorted) {
            if (glyph.image != null) {
                RectPacker.Placement placement = packer.add(glyph.image.getWidth(), glyph.image.getHeight());
                glyph.page = placement.page();
                glyph.x = placement.x();
                glyph.y = placement.y();
            }
        }
        List<BufferedImage> pages = new ArrayList<>();
        for (int page = 0; page < Math.max(1, packer.pageCount()); page++) {
            int height = side;
            if (page == packer.pageCount() - 1) {
                // Trim the last page to a power of two that holds everything.
                int used = packer.usedSize(page)[1];
                while (height / 2 >= used && height > 64) {
                    height /= 2;
                }
            }
            pages.add(new BufferedImage(side, height, imageType));
        }
        for (Glyph glyph : glyphs) {
            if (glyph.image != null) {
                BufferedImage page = pages.get(glyph.page);
                for (int y = 0; y < glyph.image.getHeight(); y++) {
                    for (int x = 0; x < glyph.image.getWidth(); x++) {
                        int argb = glyph.image.getRGB(x, y);
                        if (glyph.image.getType() == BufferedImage.TYPE_INT_RGB) {
                            argb |= 0xff000000;
                        }
                        page.setRGB(glyph.x + x, glyph.y + y, argb);
                    }
                }
            }
        }
        return pages;
    }

    private static int height(Glyph glyph) {
        return glyph.image == null ? 0 : glyph.image.getHeight();
    }

    private static void writePages(Path output, String name, List<BufferedImage> pages) throws IOException {
        Files.createDirectories(output);
        for (int i = 0; i < pages.size(); i++) {
            ImageIO.write(
                    pages.get(i), "png", output.resolve(name + "_" + i + ".png").toFile());
        }
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static void check(int result, String call) {
        if (result != MSDF_SUCCESS) {
            throw new IllegalStateException(call + " failed with code " + result);
        }
    }
}
