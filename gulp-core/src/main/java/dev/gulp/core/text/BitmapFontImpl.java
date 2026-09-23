package dev.gulp.core.text;

import dev.gulp.api.graphics.Texture;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.text.FontKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Bitmap font: glyphs drawn at one size, from a BMFont text file ({@code .fnt}) or a grid of equal cells. Metrics are
 * stored in em of the font's native size.
 */
public final class BitmapFontImpl extends FontImpl {

    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final Map<Long, Float> kerning = new HashMap<>();
    private final float ascent;
    private final float descent;
    private final float lineHeight;
    private final float nativeSize;

    private BitmapFontImpl(String name, float nativeSize, float ascent, float descent, float lineHeight) {
        super(name);
        this.nativeSize = nativeSize;
        this.ascent = ascent;
        this.descent = descent;
        this.lineHeight = lineHeight;
    }

    /** A parsed BMFont file before its pages are loaded. */
    public static final class Description {
        final Map<String, String> info = new HashMap<>();
        final Map<String, String> common = new HashMap<>();
        final List<String> pages = new ArrayList<>();
        final List<Map<String, String>> chars = new ArrayList<>();
        final List<Map<String, String>> kernings = new ArrayList<>();

        /**
         * Returns the page file names.
         *
         * @return the names, relative to the {@code .fnt} file
         */
        public List<String> pages() {
            return pages;
        }

        /**
         * Returns whether the font asks for smooth (linear) filtering.
         *
         * @return {@code false} for pixel fonts
         */
        public boolean smooth() {
            return !"0".equals(info.get("smooth"));
        }
    }

    /**
     * Parses a BMFont text file.
     *
     * @param text the file contents
     * @return the description
     * @throws IllegalArgumentException if required lines are missing
     */
    public static Description parse(String text) {
        Description description = new Description();
        for (String rawLine : text.split("\r?\n")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            int space = line.indexOf(' ');
            String tag = space < 0 ? line : line.substring(0, space);
            Map<String, String> values = attributes(space < 0 ? "" : line.substring(space + 1));
            switch (tag) {
                case "info" -> description.info.putAll(values);
                case "common" -> description.common.putAll(values);
                case "page" -> {
                    int id = Integer.parseInt(values.getOrDefault("id", "0"));
                    while (description.pages.size() <= id) {
                        description.pages.add("");
                    }
                    description.pages.set(id, values.getOrDefault("file", ""));
                }
                case "char" -> description.chars.add(values);
                case "kerning" -> description.kernings.add(values);
                default -> {}
            }
        }
        if (!description.common.containsKey("lineHeight") || !description.common.containsKey("base")) {
            throw new IllegalArgumentException("Not a BMFont text file: no 'common lineHeight=... base=...' line");
        }
        return description;
    }

    private static Map<String, String> attributes(String text) {
        Map<String, String> values = new HashMap<>();
        int i = 0;
        while (i < text.length()) {
            while (i < text.length() && text.charAt(i) == ' ') {
                i++;
            }
            int equals = text.indexOf('=', i);
            if (equals < 0) {
                break;
            }
            String key = text.substring(i, equals);
            int start = equals + 1;
            int end;
            String value;
            if (start < text.length() && text.charAt(start) == '"') {
                end = text.indexOf('"', start + 1);
                end = end < 0 ? text.length() : end;
                value = text.substring(start + 1, end);
                end++;
            } else {
                end = text.indexOf(' ', start);
                end = end < 0 ? text.length() : end;
                value = text.substring(start, end);
            }
            values.put(key, value);
            i = end;
        }
        return values;
    }

    /**
     * Creates a font from a parsed BMFont file and its loaded pages.
     *
     * @param name the name
     * @param description the parsed file
     * @param pages the page textures
     * @return the font
     */
    public static BitmapFontImpl create(String name, Description description, List<Texture> pages) {
        float size = Math.abs(Float.parseFloat(description.info.getOrDefault("size", "0")));
        float lineHeight = Float.parseFloat(description.common.get("lineHeight"));
        float base = Float.parseFloat(description.common.get("base"));
        if (size == 0f) {
            size = lineHeight;
        }
        BitmapFontImpl font =
                new BitmapFontImpl(name, size, base / size, (lineHeight - base) / size, lineHeight / size);
        for (Map<String, String> c : description.chars) {
            int code = Integer.parseInt(c.get("id"));
            Glyph glyph = new Glyph(code, Float.parseFloat(c.getOrDefault("xadvance", "0")) / size);
            int width = Integer.parseInt(c.getOrDefault("width", "0"));
            int height = Integer.parseInt(c.getOrDefault("height", "0"));
            if (width > 0 && height > 0) {
                float left = Float.parseFloat(c.getOrDefault("xoffset", "0")) / size;
                float top = (Float.parseFloat(c.getOrDefault("yoffset", "0")) - base) / size;
                glyph.image(
                        pages.get(Integer.parseInt(c.getOrDefault("page", "0"))),
                        Integer.parseInt(c.getOrDefault("x", "0")),
                        Integer.parseInt(c.getOrDefault("y", "0")),
                        width,
                        height,
                        left,
                        top,
                        left + width / size,
                        top + height / size);
            }
            font.glyphs.put(code, glyph);
        }
        for (Map<String, String> k : description.kernings) {
            font.kerning.put(
                    MsdfFontImpl.key(Integer.parseInt(k.get("first")), Integer.parseInt(k.get("second"))),
                    Float.parseFloat(k.get("amount")) / size);
        }
        return font;
    }

    /**
     * Creates a monospaced font from a grid of cells.
     *
     * @param name the name
     * @param sheet the image with the characters
     * @param characters the characters in cell order, row by row
     * @param cellWidth cell width in pixels
     * @param cellHeight cell height in pixels
     * @return the font; its native size is the cell height
     */
    public static BitmapFontImpl grid(
            String name, TextureRegion sheet, String characters, int cellWidth, int cellHeight) {
        if (cellWidth < 1 || cellHeight < 1) {
            throw new IllegalArgumentException("Cells must be at least 1x1: " + cellWidth + "x" + cellHeight);
        }
        int columns = sheet.width() / cellWidth;
        if (columns < 1) {
            throw new IllegalArgumentException("The sheet is narrower than one cell");
        }
        float size = cellHeight;
        BitmapFontImpl font = new BitmapFontImpl(name, size, 1f, 0f, 1.1f);
        int index = 0;
        for (int i = 0; i < characters.length(); ) {
            int c = characters.codePointAt(i);
            i += Character.charCount(c);
            int x = sheet.x() + (index % columns) * cellWidth;
            int y = sheet.y() + (index / columns) * cellHeight;
            index++;
            if (y + cellHeight > sheet.y() + sheet.height()) {
                throw new IllegalArgumentException("The sheet has fewer cells than the " + index + " characters given");
            }
            Glyph glyph = new Glyph(c, cellWidth / size);
            if (c != ' ') {
                glyph.image(sheet.texture(), x, y, cellWidth, cellHeight, 0f, -1f, cellWidth / size, 0f);
            }
            font.glyphs.put(c, glyph);
        }
        return font;
    }

    /**
     * Returns the size the glyphs were drawn at.
     *
     * @return the size in pixels
     */
    public float nativeSize() {
        return nativeSize;
    }

    @Override
    public FontKind kind() {
        return FontKind.BITMAP;
    }

    @Override
    public @Nullable Glyph glyph(int codePoint) {
        return glyphs.get(codePoint);
    }

    @Override
    public float kerning(int first, int second) {
        Float value = kerning.get(MsdfFontImpl.key(first, second));
        return value == null ? 0f : value;
    }

    @Override
    public float ascentEm() {
        return ascent;
    }

    @Override
    public float descentEm() {
        return descent;
    }

    @Override
    public float lineHeightEm() {
        return lineHeight;
    }
}
