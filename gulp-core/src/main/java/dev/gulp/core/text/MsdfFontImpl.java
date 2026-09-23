package dev.gulp.core.text;

import dev.gulp.api.data.JsonArray;
import dev.gulp.api.data.JsonObject;
import dev.gulp.api.data.JsonValue;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.text.FontKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * MSDF font from {@code <name>.msdf.json} written by the {@code gulp-tools} generator:
 *
 * <pre>{@code
 * {"type":"msdf","emSize":40,"distanceRange":8,"ascent":0.93,"descent":0.26,"lineHeight":1.2,
 *  "pages":["default_0.png"],
 *  "glyphs":[{"code":65,"advance":0.57,"page":0,"x":774,"y":399,"w":31,"h":36,
 *             "left":-0.09,"top":-0.8,"right":0.68,"bottom":0.1}],
 *  "kerning":[[65,86,-0.05]]}
 * }</pre>
 *
 * Glyph edges are in em relative to the pen on the baseline, Y down.
 */
public final class MsdfFontImpl extends FontImpl {

    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final Map<Long, Float> kerning = new HashMap<>();
    private final float ascent;
    private final float descent;
    private final float lineHeight;
    private final float distanceRange;

    /**
     * Creates the font from its description and loaded pages.
     *
     * @param name the name
     * @param json the parsed {@code .msdf.json}
     * @param pages the page textures, in the order of {@code pages}
     */
    public MsdfFontImpl(String name, JsonObject json, List<Texture> pages) {
        super(name);
        this.ascent = (float) json.getOrThrow("ascent").asDouble();
        this.descent = (float) json.getOrThrow("descent").asDouble();
        this.lineHeight = (float) json.getOrThrow("lineHeight").asDouble();
        this.distanceRange = (float) json.getOrThrow("distanceRange").asDouble();
        for (JsonValue value : json.getOrThrow("glyphs").asArray()) {
            JsonObject g = value.asObject();
            Glyph glyph = new Glyph(g.getOrThrow("code").asInt(), (float)
                    g.getOrThrow("advance").asDouble());
            JsonValue page = g.get("page");
            if (page != null) {
                glyph.image(
                        pages.get(page.asInt()),
                        g.getOrThrow("x").asInt(),
                        g.getOrThrow("y").asInt(),
                        g.getOrThrow("w").asInt(),
                        g.getOrThrow("h").asInt(),
                        (float) g.getOrThrow("left").asDouble(),
                        (float) g.getOrThrow("top").asDouble(),
                        (float) g.getOrThrow("right").asDouble(),
                        (float) g.getOrThrow("bottom").asDouble());
            }
            glyphs.put(glyph.codePoint, glyph);
        }
        JsonValue pairs = json.get("kerning");
        if (pairs != null) {
            for (JsonValue value : pairs.asArray()) {
                JsonArray pair = value.asArray();
                kerning.put(key(pair.get(0).asInt(), pair.get(1).asInt()), (float)
                        pair.get(2).asDouble());
            }
        }
    }

    /**
     * Returns the page file names listed in a description.
     *
     * @param json the parsed {@code .msdf.json}
     * @return the file names, relative to the description
     */
    public static List<String> pages(JsonObject json) {
        List<String> pages = new ArrayList<>();
        for (JsonValue page : json.getOrThrow("pages").asArray()) {
            pages.add(page.asString());
        }
        return pages;
    }

    static long key(int first, int second) {
        return ((long) first << 32) | (second & 0xffffffffL);
    }

    @Override
    public FontKind kind() {
        return FontKind.MSDF;
    }

    @Override
    public @Nullable Glyph glyph(int codePoint) {
        return glyphs.get(codePoint);
    }

    @Override
    public float kerning(int first, int second) {
        Float value = kerning.get(key(first, second));
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

    @Override
    public float distanceRange() {
        return distanceRange;
    }
}
