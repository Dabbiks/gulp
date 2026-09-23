package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.i18n.LocaleChangeEvent;
import dev.gulp.api.math.Rect;
import dev.gulp.api.render.Draw;
import dev.gulp.api.render.RenderLayerEvent;
import dev.gulp.api.text.Font;
import dev.gulp.api.text.FontFamily;
import dev.gulp.api.text.FontKind;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextAlign;
import dev.gulp.api.text.TextBox;
import dev.gulp.api.text.TextLayout;
import dev.gulp.api.text.TextStyle;
import dev.gulp.api.text.TextWrap;
import dev.gulp.backend.headless.HeadlessBackend;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TextTest {

    private static final String FNT = """
            info face="Test" size=8 bold=0 italic=0 smooth=0
            common lineHeight=10 base=8 scaleW=16 scaleH=16 pages=1
            page id=0 file="pixel_0.png"
            chars count=3
            char id=65 x=0 y=0 width=6 height=8 xoffset=0 yoffset=0 xadvance=7 page=0
            char id=66 x=8 y=0 width=6 height=8 xoffset=0 yoffset=0 xadvance=7 page=0
            char id=32 x=0 y=0 width=0 height=0 xoffset=0 yoffset=0 xadvance=4 page=0
            kernings count=1
            kerning first=65 second=66 amount=-1
            """;

    private @Nullable HeadlessRunner runner;
    private final List<Consumer<Draw>> painters = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (runner != null) {
            runner.stop();
        }
    }

    private static void files(HeadlessBackend backend) {
        backend.files().useClasspathAssets(true);
        put(backend, "test/fonts/pixel.fnt", FNT);
        put(backend, "test/fonts/pixel_0.png", "png");
        put(backend, "test/fonts/dynamic.ttf", "ttf");
        put(backend, "test/lang/en_us.json", "{\"hud\": {\"coins\": \"Coins: {0}\", \"other\": \"{5} {x}\"}}");
        put(backend, "test/lang/pl_pl.json", "{\"hud.coins\": \"Monety: {0}\"}");
        put(backend, "gulp/lang/en_us.json", "{\"engine.key\": \"from engine\", \"hud.coins\": \"engine loses\"}");
    }

    private static void put(HeadlessBackend backend, String path, String content) {
        backend.files().putAsset(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private TestGame start() {
        TestGame game = new TestGame();
        game.onStart = () -> game.on(RenderLayerEvent.class, e -> {
            if (e.layer().name().equals("ui")) {
                for (Consumer<Draw> painter : painters) {
                    painter.accept(e.draw());
                }
            }
        });
        HeadlessRunner r = HeadlessRunner.start(game, TextTest::files);
        runner = r;
        for (int i = 0; i < 20 && !r.engine().isRunning(); i++) {
            r.step(1);
        }
        assertThat(r.engine().isRunning()).isTrue();
        return game;
    }

    @Test
    void defaultFontLaysOutAndDrawsPolishText() {
        TestGame game = start();
        FontFamily family = game.graphics().defaultFont();
        Font font = family.regular();
        assertThat(font.kind()).isEqualTo(FontKind.MSDF);
        assertThat(font.hasGlyph('ż')).isTrue();
        assertThat(font.hasGlyph('一')).isFalse();
        assertThat(font.lineHeight(10)).isGreaterThan(font.ascent(10));
        assertThat(font.descent(10)).isPositive();
        assertThat(font.measure("AV", 32)).isLessThan(font.measure("A", 32) + font.measure("V", 32));
        assertThat(font.measure("ab\ncdef", 10)).isEqualTo(font.measure("cdef", 10));

        TextLayout layout = game.graphics().layout(Text.of("Zażółć gęślą jaźń"), TextStyle.of(20), TextBox.NONE);
        assertThat(layout.lineCount()).isEqualTo(1);
        assertThat(layout.width()).isPositive();
        assertThat(layout.characterCount()).isEqualTo(15);
        assertThat(layout.text().plain()).isEqualTo("Zażółć gęślą jaźń");
        assertThat(layout.style().size()).isEqualTo(20f);
        assertThat(layout.box()).isEqualTo(TextBox.NONE);
        assertThat(layout.isTruncated()).isFalse();
        assertThat(layout.toString()).contains("1 lines");

        painters.add(d -> d.text("Punkty: 10", 8, 8)
                .text("Wyśrodkowany", 100, 50, TextStyle.of(16), TextAlign.CENTER)
                .text(layout, 0, 0, 5));
        int before = game.display().stats().drawCalls();
        runner.step(1);
        assertThat(game.display().stats().vertices()).isGreaterThan(before);
    }

    @Test
    void wrappingLimitsAndLinks() {
        TestGame game = start();
        TextStyle style = TextStyle.of(10);
        TextLayout words =
                game.graphics().layout(Text.of("jeden dwa trzy cztery pięć sześć siedem"), style, TextBox.width(60));
        assertThat(words.lineCount()).isGreaterThan(2);
        assertThat(words.width()).isLessThanOrEqualTo(60f);

        TextLayout characters = game.graphics()
                .layout(
                        Text.of("abcdefghijklmnopqrstuvwxyz"),
                        style,
                        TextBox.width(40).wrap(TextWrap.CHARACTERS));
        assertThat(characters.lineCount()).isGreaterThan(2);

        TextLayout none = game.graphics()
                .layout(
                        Text.of("jeden dwa trzy\ncztery"),
                        style,
                        TextBox.width(10).wrap(TextWrap.NONE));
        assertThat(none.lineCount()).isEqualTo(2);

        TextLayout cut = game.graphics()
                .layout(
                        Text.of("jeden dwa trzy cztery pięć sześć siedem"),
                        style,
                        TextBox.width(60).maxLines(2).ellipsis(true));
        assertThat(cut.lineCount()).isEqualTo(2);
        assertThat(cut.isTruncated()).isTrue();

        TextLayout right =
                game.graphics().layout(Text.of("a"), style, TextBox.width(100).align(TextAlign.RIGHT));
        TextLayout left = game.graphics().layout(Text.of("a"), style, TextBox.width(100));
        assertThat(right.linkAt(0, 0)).isNull();
        assertThat(left.width()).isEqualTo(right.width());

        TextLayout linked = game.graphics().layout(Text.markup("go [link=shop]SHOP[/link]"), style, TextBox.NONE);
        assertThat(linked.linkAt(linked.width() - 1, 5)).isEqualTo("shop");
        assertThat(linked.linkAt(1, 5)).isNull();
        assertThat(game.graphics().layout(Text.of(""), style, TextBox.NONE).lineCount())
                .isEqualTo(1);
    }

    @Test
    void bitmapGridAndDynamicFontsWithFallbacks() {
        TestGame game = start();
        Font pixel = assetNow(game, AssetKey.font("test:fonts/pixel"));
        assertThat(pixel.kind()).isEqualTo(FontKind.BITMAP);
        assertThat(pixel.measure("AB", 8)).isEqualTo(13f);
        assertThat(pixel.hasGlyph('C')).isFalse();
        assertThat(pixel.fallback()).isNull();
        pixel.setFallback(game.graphics().defaultFont().regular());
        assertThat(pixel.measure("C", 8)).isPositive();
        assertThatThrownBy(() -> game.graphics().defaultFont().regular().setFallback(pixel))
                .isInstanceOf(IllegalArgumentException.class);

        Texture sheet = game.graphics().texture(new Pixmap(16, 8));
        Font grid = game.graphics().gridFont(sheet.region(), "XY", 8, 8);
        assertThat(grid.measure("XYX", 8)).isEqualTo(24f);
        assertThat(grid.ascent(8)).isEqualTo(8f);
        assertThatThrownBy(() -> game.graphics().gridFont(sheet.region(), "XYZ", 8, 8))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> game.graphics().gridFont(sheet.region(), "X", 0, 8))
                .isInstanceOf(IllegalArgumentException.class);

        Font dynamic = assetNow(game, AssetKey.font("test:fonts/dynamic"));
        assertThat(dynamic.kind()).isEqualTo(FontKind.DYNAMIC);
        assertThat(dynamic.hasGlyph('Ω')).isTrue();
        assertThat(dynamic.measure("ab", 10)).isEqualTo(12f);
        assertThat(dynamic.lineHeight(10)).isEqualTo(12f);

        painters.add(d -> d.text(
                        "AB C",
                        0,
                        0,
                        TextStyle.of(8).font(pixel).outline(1, Color.BLACK).bold())
                .text(
                        "dyn",
                        0,
                        20,
                        TextStyle.of(16).font(dynamic).shadow(1, 1, Color.BLACK).italic())
                .text("XY", 0, 40, TextStyle.of(8).font(grid)));
        runner.step(2);
        assertThat(game.display().stats().vertices()).isPositive();
    }

    @Test
    void richTextEffectsAndStyles() {
        TestGame game = start();
        List<String> warnings = new ArrayList<>();
        Text text = Text.markup("[b]Bold[/b] [i]italic[/i] [wave]wave[/wave] [shake]shake[/shake] "
                + "[rainbow]rainbow[/rainbow] [pulse]pulse[/pulse] [fade]fade[/fade] [size=30]big[/size] "
                + "[color=gold]gold[/color] [font=test:fonts/missing]x[/font] [img=test:none/coin]");
        TextStyle style = TextStyle.of(16)
                .outline(2, Color.BLACK)
                .shadow(2, 2, Color.rgba(0x00000080))
                .letterSpacing(1);
        painters.add(d -> d.text(text, 10, 10, style)
                .text(text, new Rect(0, 0, 120, 200), style, TextAlign.CENTER)
                .text(Text.translatable("hud.coins", 3), 0, 0, style));
        runner.step(3);
        assertThat(game.display().stats().vertices()).isPositive();
        assertThat(warnings).isEmpty();
    }

    @Test
    void translationsFollowTheLocaleChain() {
        TestGame game = start();
        assertThat(game.translations().locale()).isEqualTo("en_us");
        assertThat(game.tr("hud.coins", 5)).isEqualTo("Coins: 5");
        assertThat(game.tr("hud.other", 1)).isEqualTo("{5} {x}");
        assertThat(game.tr("engine.key")).isEqualTo("from engine");
        assertThat(game.tr("missing.key")).isEqualTo("missing.key");
        assertThat(game.translations().has("hud.coins")).isTrue();
        assertThat(game.translations().has("missing.key")).isFalse();
        List<String> changes = new ArrayList<>();
        game.on(LocaleChangeEvent.class, e -> changes.add(e.locale()));
        game.translations().setLocale("pl-PL");
        runner.step(2);
        assertThat(changes).containsExactly("pl_pl");
        assertThat(game.tr("hud.coins", 7)).isEqualTo("Monety: 7");
        assertThat(game.tr("engine.key")).isEqualTo("from engine");
        TextLayout layout = game.graphics().layout(Text.translatable("hud.coins", 1), TextStyle.of(10), TextBox.NONE);
        assertThat(layout.characterCount()).isEqualTo(8);
        assertThat(game.translations().availableLocales()).isEmpty();
    }

    private <T> T assetNow(TestGame game, AssetKey<T> key) {
        game.assets().load(key);
        for (int i = 0; i < 10 && !game.assets().isLoaded(key); i++) {
            runner.step(1);
        }
        return game.assets().get(key);
    }
}
