package dev.gulp.api.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.graphics.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TextApiTest {

    @Test
    void markupBuildsNestedFormatting() {
        List<String> warnings = new ArrayList<>();
        Text text = Markup.parse(
                "a[b]b[color=gold]c[/color][/b][[d [img=coins:icons/coin] [size=20]e[/size]", warnings::add);
        assertThat(warnings).isEmpty();
        assertThat(text.plain()).isEqualTo("abc[d  e");
        Text bold = text.children().get(1);
        assertThat(bold.boldOrNull()).isTrue();
        Text gold = bold.children().get(1);
        assertThat(gold.colorOrNull()).isEqualTo(Color.rgb(0xffd700));
        assertThat(gold.boldOrNull()).isNull();
        assertThat(text.children().stream().anyMatch(t -> "coins:icons/coin".equals(t.image())))
                .isTrue();
        assertThat(text.children().get(text.children().size() - 1).sizeOrNull()).isEqualTo(20f);
    }

    @Test
    void innerTagsKeepTheirOwnValues() {
        Text text = Markup.parse("[color=red][color=blue]x[/color][/color]");
        Text red = text.children().get(0);
        Text blue = red.children().get(0);
        assertThat(red.colorOrNull()).isEqualTo(Color.RED);
        assertThat(blue.colorOrNull()).isEqualTo(Color.BLUE);
    }

    @Test
    void markupMistakesStayVisible() {
        List<String> warnings = new ArrayList<>();
        Text text = Markup.parse("[nope]x[/b][color=unknown]y[img] z [size=abc]w [unclosed", warnings::add);
        assertThat(text.plain()).isEqualTo("[nope]x[/b][color=unknown]y[img] z [size=abc]w [unclosed");
        assertThat(warnings).hasSize(6);
    }

    @Test
    void effectsLinksFontsAndUnclosedTags() {
        Text text = Markup.parse("[wave][shake][rainbow][pulse][fade][link=shop][font=a:b][i]x");
        Text node = text;
        while (!node.children().isEmpty() && node.content().isEmpty()) {
            node = node.children().get(0);
        }
        assertThat(text.plain()).isEqualTo("x");
        Text wave = text.children().get(0);
        assertThat(wave.effects()).containsExactly(TextEffect.WAVE);
        Text italic = wave.children()
                .get(0)
                .children()
                .get(0)
                .children()
                .get(0)
                .children()
                .get(0)
                .children()
                .get(0)
                .children()
                .get(0)
                .children()
                .get(0);
        assertThat(italic.italicOrNull()).isTrue();
        assertThat(italic.children().get(0).content()).isEqualTo("x");
        Text link = wave.children()
                .get(0)
                .children()
                .get(0)
                .children()
                .get(0)
                .children()
                .get(0)
                .children()
                .get(0);
        assertThat(link.linkOrNull()).isEqualTo("shop");
        assertThat(link.children().get(0).fontOrNull()).isEqualTo("a:b");
        assertThat(Markup.color("#ff0000")).isEqualTo(Color.RED);
        assertThat(Markup.color("nothing")).isNull();
    }

    @Test
    void textIsImmutableAndComparable() {
        Text a = Text.of("Monety: ")
                .append(Text.of("10").color(Color.YELLOW).bold().italic().size(3));
        Text b = Text.of("Monety: ")
                .append(Text.of("10").color(Color.YELLOW).bold().italic().size(3));
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a.plain()).isEqualTo("Monety: 10");
        assertThat(a.toString()).contains("Monety: 10");
        Text translated =
                Text.translatable("hud.coins", 5).effect(TextEffect.FADE).effect(TextEffect.WAVE);
        assertThat(translated.plain()).isEqualTo("hud.coins");
        assertThat(translated.arguments()).containsExactly(5);
        assertThat(translated.translationKey()).isEqualTo("hud.coins");
        assertThat(translated.effects()).isEqualTo(Set.of(TextEffect.FADE, TextEffect.WAVE));
        assertThat(Text.empty().plain()).isEmpty();
        assertThat(Text.markup("[b]x[/b]").plain()).isEqualTo("x");
        assertThat(Text.of("a").append("b").plain()).isEqualTo("ab");
        assertThat(Text.image("a:b").image()).isEqualTo("a:b");
        assertThat(Text.of("x").link("l").font("f:g").linkOrNull()).isEqualTo("l");
        assertThat(a).isNotEqualTo("Monety: 10");
    }

    @Test
    void stylesBoxesAndFamilies() {
        TextStyle style = TextStyle.of(24)
                .color(Color.RED)
                .outline(2, Color.BLACK)
                .shadow(1, 2, Color.GRAY)
                .letterSpacing(1)
                .lineHeight(1.5f)
                .bold()
                .italic()
                .bold(false)
                .italic(false)
                .font((FontFamily) null);
        assertThat(style.size()).isEqualTo(24f);
        assertThat(style.outlineWidth()).isEqualTo(2f);
        assertThat(style.hasShadow()).isTrue();
        assertThat(TextStyle.DEFAULT.hasShadow()).isFalse();
        assertThat(style.isBold()).isFalse();
        assertThatThrownBy(() -> TextStyle.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TextStyle.DEFAULT.outline(-1, Color.RED)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TextStyle.DEFAULT.lineHeight(0)).isInstanceOf(IllegalArgumentException.class);

        TextBox box = TextBox.width(100)
                .wrap(TextWrap.CHARACTERS)
                .maxLines(2)
                .ellipsis(true)
                .align(TextAlign.RIGHT);
        assertThat(box.maxLines()).isEqualTo(2);
        assertThat(box.align().horizontal()).isEqualTo(1f);
        assertThat(TextAlign.BOTTOM.vertical()).isEqualTo(1f);
        assertThatThrownBy(() -> TextBox.width(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TextBox.NONE.maxLines(-1)).isInstanceOf(IllegalArgumentException.class);

        Font regular = new StubFont("r");
        Font bold = new StubFont("b");
        FontFamily family = new FontFamily(regular, bold, null, null);
        assertThat(family.pick(true, false)).isSameAs(bold);
        assertThat(family.pick(true, true)).isSameAs(bold);
        assertThat(family.pick(false, true)).isSameAs(regular);
        assertThat(family.hasVariant(true, false)).isTrue();
        assertThat(family.hasVariant(false, true)).isFalse();
        assertThat(family.hasVariant(true, true)).isFalse();
        assertThat(family.hasVariant(false, false)).isTrue();
        assertThat(FontFamily.of(regular).regular()).isSameAs(regular);
        assertThat(style.font(regular).font().regular()).isSameAs(regular);
    }

    private record StubFont(String name) implements Font {
        @Override
        public FontKind kind() {
            return FontKind.BITMAP;
        }

        @Override
        public boolean hasGlyph(int codePoint) {
            return true;
        }

        @Override
        public float lineHeight(float size) {
            return size;
        }

        @Override
        public float ascent(float size) {
            return size;
        }

        @Override
        public float descent(float size) {
            return 0;
        }

        @Override
        public float measure(String text, float size) {
            return text.length() * size;
        }

        @Override
        public Font fallback() {
            return null;
        }

        @Override
        public Font setFallback(Font fallback) {
            return this;
        }
    }
}
