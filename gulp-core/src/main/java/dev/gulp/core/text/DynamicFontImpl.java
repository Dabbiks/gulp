package dev.gulp.core.text;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.text.FontKind;
import dev.gulp.core.graphics.RectPacker;
import dev.gulp.platform.GlyphBitmap;
import dev.gulp.platform.PlatformFontFace;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Font rasterised on demand by the platform (FreeType on desktop, Canvas2D on the web). Glyph images are made per pixel
 * size when first drawn and packed into 1024x1024 cache pages; metrics come from a reference size.
 */
public final class DynamicFontImpl extends FontImpl {

    /** Size at which metrics are measured. */
    private static final float REFERENCE = 64f;

    private static final int PAGE_SIZE = 1024;

    private final PlatformFontFace face;
    private final Function<Pixmap, Texture> textures;
    private final Map<Integer, Glyph> metrics = new HashMap<>();
    private final Map<Long, Glyph> images = new HashMap<>();
    private final List<Texture> pages = new ArrayList<>();
    private final float ascent;
    private final float descent;
    private final float lineHeight;
    private RectPacker packer = new RectPacker(PAGE_SIZE, PAGE_SIZE, 1);
    private int packerPages;

    /**
     * Creates the font.
     *
     * @param name the name
     * @param face the opened font file
     * @param textures creates cache page textures
     */
    public DynamicFontImpl(String name, PlatformFontFace face, Function<Pixmap, Texture> textures) {
        super(name);
        this.face = face;
        this.textures = textures;
        this.ascent = face.ascent(REFERENCE) / REFERENCE;
        this.descent = face.descent(REFERENCE) / REFERENCE;
        this.lineHeight = face.lineHeight(REFERENCE) / REFERENCE;
    }

    @Override
    public FontKind kind() {
        return FontKind.DYNAMIC;
    }

    @Override
    public @Nullable Glyph glyph(int codePoint) {
        Glyph glyph = metrics.get(codePoint);
        if (glyph != null || metrics.containsKey(codePoint)) {
            return glyph;
        }
        int index = face.glyphIndex(codePoint);
        if (index == 0 && codePoint != ' ') {
            metrics.put(codePoint, null);
            return null;
        }
        GlyphBitmap bitmap = face.rasterize(index, REFERENCE);
        glyph = new Glyph(codePoint, bitmap.advance() / REFERENCE);
        metrics.put(codePoint, glyph);
        return glyph;
    }

    @Override
    public Glyph image(Glyph glyph, float pixelSize) {
        int bucket = Math.max(4, Math.min(256, Math.round(pixelSize)));
        long key = ((long) glyph.codePoint << 16) | bucket;
        Glyph image = images.get(key);
        if (image != null) {
            return image;
        }
        GlyphBitmap bitmap = face.rasterize(face.glyphIndex(glyph.codePoint), bucket);
        image = new Glyph(glyph.codePoint, glyph.advance);
        if (bitmap.width() > 0 && bitmap.height() > 0) {
            Pixmap pixels = new Pixmap(bitmap.width(), bitmap.height());
            ByteBuffer coverage = bitmap.coverage().duplicate();
            for (int y = 0; y < bitmap.height(); y++) {
                for (int x = 0; x < bitmap.width(); x++) {
                    int alpha = coverage.get(y * bitmap.width() + x) & 0xff;
                    pixels.setPixel(x, y, 0xffffff00 | alpha);
                }
            }
            RectPacker.Placement at = place(bitmap.width(), bitmap.height());
            Texture page = pages.get(at.page());
            page.update(pixels, at.x(), at.y());
            float left = bitmap.offsetX() / bucket;
            float top = -bitmap.offsetY() / bucket;
            image.image(
                    page,
                    at.x(),
                    at.y(),
                    bitmap.width(),
                    bitmap.height(),
                    left,
                    top,
                    left + bitmap.width() / (float) bucket,
                    top + bitmap.height() / (float) bucket);
        }
        images.put(key, image);
        return image;
    }

    private RectPacker.Placement place(int width, int height) {
        RectPacker.Placement at = packer.add(width, height);
        while (at.page() + packerPages >= pages.size()) {
            Pixmap empty = new Pixmap(PAGE_SIZE, PAGE_SIZE);
            empty.fill(Color.CLEAR);
            pages.add(textures.apply(empty));
        }
        return new RectPacker.Placement(at.page() + packerPages, at.x(), at.y());
    }

    /**
     * Returns the cache pages, for tests and statistics.
     *
     * @return the page textures
     */
    public List<Texture> pages() {
        return pages;
    }

    @Override
    public float kerning(int first, int second) {
        return face.kerning(face.glyphIndex(first), face.glyphIndex(second), REFERENCE) / REFERENCE;
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
    public void dispose() {
        for (Texture page : pages) {
            page.dispose();
        }
        pages.clear();
        images.clear();
        packer = new RectPacker(PAGE_SIZE, PAGE_SIZE, 1);
        packerPages = 0;
        face.dispose();
    }
}
