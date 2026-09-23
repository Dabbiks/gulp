package dev.gulp.core.text;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.text.Font;
import dev.gulp.api.text.FontFamily;
import dev.gulp.api.text.Markup;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextEffect;
import dev.gulp.api.text.TextStyle;
import dev.gulp.api.text.TextWrap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Lays out text: resolves translations, fonts and inline images, breaks lines, applies kerning, alignment, the line
 * limit and the ellipsis. Recent layouts are cached by text, style, box and language, so drawing the same string every
 * frame lays it out once.
 */
public final class TextSystem {

    private static final int CACHE_SIZE = 512;
    private static final float ITALIC_SLANT = 0.21f;
    private static final int ELLIPSIS = 0x2026;

    /** Where text finds what it refers to. */
    public interface Resolver {
        /**
         * Returns a loaded font by asset key.
         *
         * @param key the key
         * @return the font, or {@code null} if not loaded
         */
        @Nullable Font font(String key);

        /**
         * Returns a loaded region by asset key.
         *
         * @param key the key
         * @return the region, or {@code null} if not loaded
         */
        @Nullable TextureRegion region(String key);

        /**
         * Translates a key.
         *
         * @param key the key
         * @param arguments the arguments
         * @return the translation
         */
        String translate(String key, Object[] arguments);

        /**
         * Returns a number that changes when the language or translations change.
         *
         * @return the revision
         */
        int translationRevision();
    }

    private record CacheKey(Object text, TextStyle style, TextBox box, int revision) {}

    private record Style(
            FontFamily family,
            float size,
            Color color,
            boolean bold,
            boolean italic,
            @Nullable String link,
            int effects) {}

    private final Resolver resolver;
    private final Consumer<String> warnings;
    private @Nullable FontFamily defaultFamily;
    private final Map<CacheKey, TextLayoutImpl> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, TextLayoutImpl> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    /**
     * Creates the text system.
     *
     * @param resolver fonts, images and translations
     * @param warnings receives markup and font problems
     */
    public TextSystem(Resolver resolver, Consumer<String> warnings) {
        this.resolver = resolver;
        this.warnings = warnings;
    }

    /**
     * Sets the font used when a style names none.
     *
     * @param family the default family
     */
    public void setDefaultFamily(@Nullable FontFamily family) {
        this.defaultFamily = family;
        cache.clear();
    }

    /**
     * Returns the default font.
     *
     * @return the family, or {@code null} before it loaded
     */
    public @Nullable FontFamily defaultFamily() {
        return defaultFamily;
    }

    /** Forgets cached layouts, after fonts or translations were reloaded. */
    public void invalidate() {
        cache.clear();
    }

    /**
     * Lays out a string, parsing no markup.
     *
     * @param text the string
     * @param style the style
     * @param box the box
     * @return the layout, or {@code null} if no font is available yet
     */
    public @Nullable TextLayoutImpl layout(String text, TextStyle style, TextBox box) {
        return cached(text, style, box, () -> Text.of(text));
    }

    /**
     * Lays out rich text.
     *
     * @param text the text
     * @param style the base style
     * @param box the box
     * @return the layout, or {@code null} if no font is available yet
     */
    public @Nullable TextLayoutImpl layout(Text text, TextStyle style, TextBox box) {
        return cached(text, style, box, () -> text);
    }

    private @Nullable TextLayoutImpl cached(
            Object key, TextStyle style, TextBox box, java.util.function.Supplier<Text> text) {
        CacheKey cacheKey = new CacheKey(key, style, box, resolver.translationRevision());
        TextLayoutImpl layout = cache.get(cacheKey);
        if (layout == null) {
            layout = build(text.get(), style, box);
            if (layout != null) {
                cache.put(cacheKey, layout);
            }
        }
        return layout;
    }

    /**
     * Parses markup, logging problems.
     *
     * @param markup the marked-up string
     * @return the text
     */
    public Text markup(String markup) {
        return Markup.parse(markup, warnings);
    }

    // ------------------------------------------------------------------ layout

    /** A glyph or image being placed. */
    private static final class Item {
        final @Nullable FontImpl font;
        final @Nullable Glyph glyph;
        final @Nullable TextureRegion image;
        final int codePoint;
        final Style style;
        float x;
        final float advance;
        final float ascent;
        final float descent;
        final float lineHeight;

        Item(
                @Nullable FontImpl font,
                @Nullable Glyph glyph,
                @Nullable TextureRegion image,
                int codePoint,
                Style style,
                float advance,
                float ascent,
                float descent,
                float lineHeight) {
            this.font = font;
            this.glyph = glyph;
            this.image = image;
            this.codePoint = codePoint;
            this.style = style;
            this.advance = advance;
            this.ascent = ascent;
            this.descent = descent;
            this.lineHeight = lineHeight;
        }
    }

    private @Nullable TextLayoutImpl build(Text text, TextStyle base, TextBox box) {
        FontFamily family = base.font() != null ? base.font() : defaultFamily;
        if (family == null) {
            return null;
        }
        Style root = new Style(family, base.size(), base.color(), base.isBold(), base.isItalic(), null, 0);
        List<List<Item>> lines = new ArrayList<>();
        List<Item> line = new ArrayList<>();
        lines.add(line);
        float[] pen = {0f};
        int[] lastBreak = {-1};
        boolean wrap = box.wrap() != TextWrap.NONE && box.width() != Float.POSITIVE_INFINITY;
        visit(text, root, base.letterSpacing(), (item, newline) -> {
            List<Item> current = lines.get(lines.size() - 1);
            if (newline) {
                lines.add(new ArrayList<>());
                pen[0] = 0f;
                lastBreak[0] = -1;
                return;
            }
            if (item == null) {
                return;
            }
            Item previous = current.isEmpty() ? null : current.get(current.size() - 1);
            float kerning = 0f;
            if (previous != null
                    && item.font != null
                    && previous.font == item.font
                    && previous.style.size == item.style.size) {
                kerning = item.font.kerning(previous.codePoint, item.codePoint) * item.style.size;
            }
            boolean space = item.codePoint == ' ';
            if (wrap && !space && !current.isEmpty() && pen[0] + kerning + visibleWidth(item) > box.width()) {
                List<Item> next = new ArrayList<>();
                if (box.wrap() == TextWrap.WORDS && lastBreak[0] >= 0 && lastBreak[0] < current.size() - 1) {
                    List<Item> moved = new ArrayList<>(current.subList(lastBreak[0] + 1, current.size()));
                    current.subList(lastBreak[0] + 1, current.size()).clear();
                    float shift = moved.get(0).x;
                    for (Item m : moved) {
                        m.x -= shift;
                    }
                    next.addAll(moved);
                    Item last = moved.get(moved.size() - 1);
                    pen[0] = last.x + last.advance;
                } else {
                    pen[0] = 0f;
                }
                lines.add(next);
                lastBreak[0] = -1;
                current = next;
                kerning = 0f;
            }
            item.x = pen[0] + kerning;
            pen[0] = item.x + item.advance;
            current.add(item);
            if (space) {
                lastBreak[0] = current.size() - 1;
            }
        });

        boolean truncated = false;
        if (box.maxLines() > 0 && lines.size() > box.maxLines()) {
            truncated = true;
            lines.subList(box.maxLines(), lines.size()).clear();
            if (box.ellipsis()) {
                addEllipsis(lines.get(lines.size() - 1), root, box);
            }
        }

        float widest = 0f;
        float[] lineWidths = new float[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            lineWidths[i] = lineWidth(lines.get(i));
            widest = Math.max(widest, lineWidths[i]);
        }
        float alignWidth = box.width() == Float.POSITIVE_INFINITY ? widest : box.width();
        FontImpl baseFont = asImpl(family.pick(root.bold(), root.italic()));
        int count = 0;
        for (List<Item> l : lines) {
            for (Item item : l) {
                if (item.codePoint != ' ') {
                    count++;
                }
            }
        }
        float[] xs = new float[count];
        float[] ys = new float[count];
        float[] sizes = new float[count];
        Glyph[] glyphs = new Glyph[count];
        FontImpl[] fonts = new FontImpl[count];
        Color[] colors = new Color[count];
        int[] flags = new int[count];
        int[] effects = new int[count];
        TextureRegion[] images = new TextureRegion[count];
        List<TextLayoutImpl.LinkArea> links = new ArrayList<>();
        float top = 0f;
        int n = 0;
        for (int li = 0; li < lines.size(); li++) {
            List<Item> l = lines.get(li);
            float ascent = 0f;
            float descent = 0f;
            float lineHeight = 0f;
            if (l.isEmpty()) {
                if (baseFont != null) {
                    ascent = baseFont.ascentEm() * root.size();
                    descent = baseFont.descentEm() * root.size();
                    lineHeight = baseFont.lineHeightEm() * root.size();
                }
            }
            for (Item item : l) {
                ascent = Math.max(ascent, item.ascent);
                descent = Math.max(descent, item.descent);
                lineHeight = Math.max(lineHeight, item.lineHeight);
            }
            lineHeight = Math.max(lineHeight, ascent + descent) * base.lineHeight();
            float baseline = top + (lineHeight - ascent - descent) / 2f + ascent;
            float offset = (alignWidth - lineWidths[li]) * box.align().horizontal();
            for (Item item : l) {
                if (item.style.link != null) {
                    links.add(new TextLayoutImpl.LinkArea(
                            item.style.link, offset + item.x, top, offset + item.x + item.advance, top + lineHeight));
                }
                if (item.codePoint == ' ') {
                    continue;
                }
                xs[n] = offset + item.x;
                ys[n] = baseline;
                sizes[n] = item.style.size;
                glyphs[n] = item.glyph;
                fonts[n] = item.font;
                colors[n] = item.style.color;
                images[n] = item.image;
                effects[n] = item.style.effects;
                if (item.font != null) {
                    Font picked = item.style.family.pick(item.style.bold, item.style.italic);
                    boolean realBold = picked == item.style.family.bold() || picked == item.style.family.boldItalic();
                    boolean realItalic =
                            picked == item.style.family.italic() || picked == item.style.family.boldItalic();
                    flags[n] = (item.style.bold && !realBold ? TextLayoutImpl.FAKE_BOLD : 0)
                            | (item.style.italic && !realItalic ? TextLayoutImpl.FAKE_ITALIC : 0);
                }
                n++;
            }
            top += lineHeight;
        }
        return new TextLayoutImpl(
                text,
                base,
                box,
                count,
                xs,
                ys,
                sizes,
                glyphs,
                fonts,
                colors,
                flags,
                effects,
                images,
                links,
                widest,
                top,
                lines.size(),
                truncated);
    }

    private static float visibleWidth(Item item) {
        if (item.glyph != null && item.glyph.right > 0f) {
            return Math.max(item.advance, item.glyph.right * item.style.size);
        }
        return item.advance;
    }

    private static float lineWidth(List<Item> line) {
        for (int i = line.size() - 1; i >= 0; i--) {
            Item item = line.get(i);
            if (item.codePoint != ' ') {
                return item.x + item.advance;
            }
        }
        return 0f;
    }

    private void addEllipsis(List<Item> line, Style root, TextBox box) {
        Style style = line.isEmpty() ? root : line.get(line.size() - 1).style;
        FontImpl font = asImpl(style.family.pick(style.bold, style.italic));
        FontImpl owner = font == null ? null : font.resolve(ELLIPSIS);
        int codePoint = ELLIPSIS;
        if (owner == null && font != null) {
            owner = font.resolve('.');
            codePoint = '.';
        }
        if (owner == null) {
            return;
        }
        Glyph glyph = owner.glyph(codePoint);
        if (glyph == null) {
            return;
        }
        float advance = glyph.advance * style.size;
        int repeat = codePoint == '.' ? 3 : 1;
        float limit = box.width() == Float.POSITIVE_INFINITY ? Float.MAX_VALUE : box.width();
        while (!line.isEmpty() && lineWidth(line) + advance * repeat > limit) {
            line.remove(line.size() - 1);
        }
        while (!line.isEmpty() && line.get(line.size() - 1).codePoint == ' ') {
            line.remove(line.size() - 1);
        }
        float x = lineWidth(line);
        for (int i = 0; i < repeat; i++) {
            Item item = new Item(
                    owner,
                    glyph,
                    null,
                    codePoint,
                    style,
                    advance,
                    owner.ascentEm() * style.size,
                    owner.descentEm() * style.size,
                    owner.lineHeightEm() * style.size);
            item.x = x;
            x += advance;
            line.add(item);
        }
    }

    private interface Sink {
        void accept(@Nullable Item item, boolean newline);
    }

    private void visit(Text node, Style inherited, float spacing, Sink sink) {
        Style style = resolve(node, inherited);
        String image = node.image();
        if (image != null) {
            TextureRegion region = resolver.region(image);
            FontImpl font = asImpl(style.family.pick(style.bold, style.italic));
            float height = font == null ? style.size : font.ascentEm() * style.size;
            if (region == null) {
                warnings.accept("Inline image '" + image + "' is not loaded; load its atlas or region first");
            } else {
                float width = height * region.width() / Math.max(1, region.height());
                sink.accept(new Item(null, null, region, -1, style, width, height, 0f, height / 0.8f), false);
            }
        }
        String key = node.translationKey();
        String content = key != null ? resolver.translate(key, node.arguments()) : node.content();
        if (!content.isEmpty()) {
            FontImpl font = asImpl(style.family.pick(style.bold, style.italic));
            for (int i = 0; i < content.length(); ) {
                int c = content.codePointAt(i);
                i += Character.charCount(c);
                if (c == '\n') {
                    sink.accept(null, true);
                    continue;
                }
                if (c == '\r' || font == null) {
                    continue;
                }
                if (c == '\t') {
                    c = ' ';
                }
                FontImpl owner = font.resolve(c);
                int drawn = c;
                if (owner == null) {
                    owner = font.resolve('?');
                    drawn = '?';
                    if (owner == null) {
                        continue;
                    }
                }
                Glyph glyph = owner.glyph(drawn);
                if (glyph == null) {
                    continue;
                }
                float advance = glyph.advance * style.size + (c == ' ' ? 0f : spacing);
                sink.accept(
                        new Item(
                                owner,
                                glyph,
                                null,
                                drawn,
                                style,
                                advance,
                                owner.ascentEm() * style.size,
                                owner.descentEm() * style.size,
                                owner.lineHeightEm() * style.size),
                        false);
            }
        }
        for (Text child : node.children()) {
            visit(child, style, spacing, sink);
        }
    }

    private Style resolve(Text node, Style inherited) {
        FontFamily family = inherited.family;
        String fontKey = node.fontOrNull();
        if (fontKey != null) {
            Font font = resolver.font(fontKey);
            if (font == null) {
                warnings.accept("Font '" + fontKey + "' is not loaded; using the surrounding font");
            } else {
                family = FontFamily.of(font);
            }
        }
        int effects = inherited.effects;
        for (TextEffect effect : node.effects()) {
            effects |= 1 << effect.ordinal();
        }
        return new Style(
                family,
                node.sizeOrNull() != null ? Objects.requireNonNull(node.sizeOrNull()) : inherited.size,
                node.colorOrNull() != null ? Objects.requireNonNull(node.colorOrNull()) : inherited.color,
                node.boldOrNull() != null ? Objects.requireNonNull(node.boldOrNull()) : inherited.bold,
                node.italicOrNull() != null ? Objects.requireNonNull(node.italicOrNull()) : inherited.italic,
                node.linkOrNull() != null ? node.linkOrNull() : inherited.link,
                effects);
    }

    private static @Nullable FontImpl asImpl(Font font) {
        return font instanceof FontImpl impl ? impl : null;
    }

    /**
     * Returns the slant of imitated italic, as horizontal shift per unit of height.
     *
     * @return the slant
     */
    public static float italicSlant() {
        return ITALIC_SLANT;
    }
}
