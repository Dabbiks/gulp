package dev.gulp.api.ui;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.registry.Key;

/** The built-in themes, generated from a palette so they can change accent and contrast. */
final class Themes {

    static final Color BLUE = Color.rgb(0x4f8cff);
    static final Color GOLD = Color.rgb(0xf2b63d);

    /** Colours one theme is made of. */
    record Palette(
            Color background,
            Color panel,
            Color surface,
            Color hover,
            Color pressed,
            Color border,
            Color text,
            Color muted,
            Color field,
            Color danger,
            Color success,
            Color shadow,
            Color onAccent,
            float radius,
            float border1,
            boolean pixel) {}

    private Themes() {}

    static Theme dark(Color accent, boolean high) {
        Palette palette = high
                ? new Palette(
                        Color.rgb(0x000000),
                        Color.rgb(0x000000),
                        Color.rgb(0x101010),
                        Color.rgb(0x303030),
                        Color.rgb(0x505050),
                        Color.WHITE,
                        Color.WHITE,
                        Color.rgb(0xd0d0d0),
                        Color.rgb(0x000000),
                        Color.rgb(0xff6060),
                        Color.rgb(0x60ff80),
                        Color.CLEAR,
                        Color.BLACK,
                        6f,
                        2f,
                        false)
                : new Palette(
                        Color.rgba(0x0b0e16c0),
                        Color.rgb(0x1a1f2e),
                        Color.rgb(0x272e42),
                        Color.rgb(0x323b54),
                        Color.rgb(0x1f2536),
                        Color.rgb(0x3a4460),
                        Color.rgb(0xe8ecf6),
                        Color.rgb(0x8f98b0),
                        Color.rgb(0x131725),
                        Color.rgb(0xe5484d),
                        Color.rgb(0x46b17b),
                        Color.rgba(0x00000070),
                        Color.WHITE,
                        6f,
                        1f,
                        false);
        String name = high ? "dark_high_contrast" : "dark";
        return build(name, palette, accent, high ? null : dark(accent, true), c -> dark(c, high));
    }

    static Theme light(Color accent, boolean high) {
        Palette palette = high
                ? new Palette(
                        Color.rgb(0xffffff),
                        Color.rgb(0xffffff),
                        Color.rgb(0xf0f0f0),
                        Color.rgb(0xd8d8d8),
                        Color.rgb(0xc0c0c0),
                        Color.BLACK,
                        Color.BLACK,
                        Color.rgb(0x303030),
                        Color.WHITE,
                        Color.rgb(0xb00000),
                        Color.rgb(0x006020),
                        Color.CLEAR,
                        Color.WHITE,
                        6f,
                        2f,
                        false)
                : new Palette(
                        Color.rgba(0xf3f5fac8),
                        Color.rgb(0xffffff),
                        Color.rgb(0xeef1f7),
                        Color.rgb(0xe2e7f1),
                        Color.rgb(0xd3d9e6),
                        Color.rgb(0xc9d0de),
                        Color.rgb(0x1c2233),
                        Color.rgb(0x6a7389),
                        Color.rgb(0xffffff),
                        Color.rgb(0xd92d34),
                        Color.rgb(0x2a8a58),
                        Color.rgba(0x1c223330),
                        Color.WHITE,
                        6f,
                        1f,
                        false);
        String name = high ? "light_high_contrast" : "light";
        return build(name, palette, accent, high ? null : light(accent, true), c -> light(c, high));
    }

    static Theme pixel(Color accent, boolean high) {
        Palette palette = new Palette(
                Color.rgba(0x14101ecc),
                Color.rgb(high ? 0x000000 : 0x2a2139),
                Color.rgb(high ? 0x202020 : 0x3d3052),
                Color.rgb(high ? 0x404040 : 0x514069),
                Color.rgb(high ? 0x606060 : 0x2a2139),
                high ? Color.WHITE : Color.rgb(0x0c0913),
                high ? Color.WHITE : Color.rgb(0xf4ecd8),
                high ? Color.rgb(0xd0d0d0) : Color.rgb(0xa89cb8),
                Color.rgb(high ? 0x000000 : 0x1a1426),
                Color.rgb(0xe04040),
                Color.rgb(0x50c060),
                high ? Color.CLEAR : Color.rgb(0x0c0913),
                Color.rgb(0x1a1426),
                0f,
                2f,
                true);
        String name = high ? "pixel_high_contrast" : "pixel";
        return build(name, palette, accent, high ? null : pixel(accent, true), c -> pixel(c, high));
    }

    private static StyleBox.Flat box(Palette p, Color fill) {
        StyleBox.Flat box = StyleBox.flat(fill).radius(p.radius).border(p.border1, p.border);
        return p.pixel ? box.shadow(4f, p.shadow) : box;
    }

    private static Theme build(
            String name, Palette p, Color accent, Theme contrast, java.util.function.Function<Color, Theme> recolor) {
        Color accentHover = accent.lerp(Color.WHITE, 0.15f);
        Color selection = accent.withAlpha(0.28f);
        float pad = p.pixel ? 8f : 12f;
        return Theme.builder(Key.of("gulp", name))
                .recolor(recolor)
                .contrast(contrast)
                .base(s -> s.textColor(p.text)
                        .mutedColor(p.muted)
                        .fontSize(16f)
                        .gap(6f)
                        .accent(accent)
                        .track(p.pressed)
                        .focusColor(accent)
                        .focusWidth(2f)
                        .transition(p.pixel ? 0f : 0.1f))
                .style("label", s -> {})
                .variant("label", "title", s -> s.fontSize(32f).bold(true))
                .variant("label", "heading", s -> s.fontSize(22f).bold(true))
                .variant("label", "caption", s -> s.fontSize(13f).textColor(p.muted))
                .variant("label", "danger", s -> s.textColor(p.danger))
                .variant("label", "success", s -> s.textColor(p.success))
                .style("panel", s -> s.background(box(p, p.panel)).padding(pad))
                .style("screen", s -> s.background(StyleBox.flat(p.background)))
                .style(
                        "button",
                        s -> s.background(box(p, p.surface))
                                .padding(Insets.symmetric(14f, 6f))
                                .minSize(0f, 34f))
                .style("button", WidgetState.HOVER, s -> s.background(box(p, p.hover)))
                .style("button", WidgetState.FOCUSED, s -> s.background(box(p, p.hover)))
                .style("button", WidgetState.PRESSED, s -> s.background(box(p, p.pressed)))
                .style(
                        "button",
                        WidgetState.CHECKED,
                        s -> s.background(box(p, p.surface).border(2f, accent)))
                .style("button", WidgetState.DISABLED, s -> s.textColor(p.muted))
                .variant("button", "primary", s -> s.background(box(p, accent)).textColor(p.onAccent))
                .variant("button", "primary", WidgetState.HOVER, s -> s.background(box(p, accentHover)))
                .variant("button", "primary", WidgetState.FOCUSED, s -> s.background(box(p, accentHover)))
                .variant("button", "danger", s -> s.background(box(p, p.danger)).textColor(Color.WHITE))
                .variant(
                        "button",
                        "danger",
                        WidgetState.HOVER,
                        s -> s.background(box(p, p.danger.lerp(Color.WHITE, 0.15f))))
                .variant("button", "flat", s -> s.background(StyleBox.NONE))
                .variant(
                        "button",
                        "flat",
                        WidgetState.HOVER,
                        s -> s.background(StyleBox.flat(p.hover).radius(p.radius)))
                .variant(
                        "button",
                        "flat",
                        WidgetState.FOCUSED,
                        s -> s.background(StyleBox.flat(p.hover).radius(p.radius)))
                .style("checkbox", s -> s.minSize(0f, 28f))
                .style("checkbox", WidgetState.DISABLED, s -> s.textColor(p.muted))
                .style("slider", s -> s.minSize(140f, 24f))
                .style("progress", s -> s.minSize(140f, 12f))
                .style(
                        "field",
                        s -> s.background(box(p, p.field))
                                .padding(Insets.symmetric(8f, 6f))
                                .minSize(140f, 34f))
                .style(
                        "field",
                        WidgetState.FOCUSED,
                        s -> s.background(box(p, p.field).border(2f, accent)))
                .style("field", WidgetState.DISABLED, s -> s.textColor(p.muted))
                .variant("field", "invalid", s -> s.background(box(p, p.field).border(2f, p.danger)))
                .style("list", s -> s.background(box(p, p.field)).padding(4f))
                .style("list_item", s -> s.padding(Insets.symmetric(8f, 4f)).minSize(0f, 28f))
                .style("list_item", WidgetState.HOVER, s -> s.background(StyleBox.flat(p.hover)))
                .style("list_item", WidgetState.FOCUSED, s -> s.background(StyleBox.flat(p.hover)))
                .style("list_item", WidgetState.CHECKED, s -> s.background(StyleBox.flat(selection)))
                .style(
                        "tab",
                        s -> s.background(StyleBox.NONE)
                                .padding(Insets.symmetric(14f, 6f))
                                .textColor(p.muted)
                                .minSize(0f, 32f))
                .style(
                        "tab",
                        WidgetState.HOVER,
                        s -> s.textColor(p.text)
                                .background(StyleBox.flat(p.hover).radius(p.radius)))
                .style(
                        "tab",
                        WidgetState.FOCUSED,
                        s -> s.textColor(p.text)
                                .background(StyleBox.flat(p.hover).radius(p.radius)))
                .style(
                        "tab",
                        WidgetState.CHECKED,
                        s -> s.textColor(accent)
                                .background(StyleBox.flat(selection).radius(p.radius)))
                .style(
                        "window",
                        s -> s.background(box(p, p.panel).shadow(10f, p.shadow)).padding(0f))
                .style(
                        "window_title",
                        s -> s.background(StyleBox.flat(p.surface).radius(p.radius))
                                .padding(Insets.symmetric(10f, 6f))
                                .bold(true))
                .style(
                        "dialog",
                        s -> s.background(box(p, p.panel).shadow(12f, p.shadow))
                                .padding(20f)
                                .gap(12f))
                .style(
                        "tooltip",
                        s -> s.background(StyleBox.flat(p.text.lerp(p.panel, 0.1f))
                                        .radius(Math.min(p.radius, 4f)))
                                .textColor(p.panel)
                                .fontSize(13f)
                                .padding(Insets.symmetric(8f, 5f)))
                .style(
                        "menu",
                        s -> s.background(box(p, p.panel).shadow(6f, p.shadow))
                                .padding(4f)
                                .gap(0f))
                .style("menu_item", s -> s.padding(Insets.symmetric(12f, 5f)).minSize(120f, 28f))
                .style(
                        "menu_item",
                        WidgetState.HOVER,
                        s -> s.background(StyleBox.flat(accent).radius(p.radius))
                                .textColor(p.onAccent))
                .style(
                        "menu_item",
                        WidgetState.FOCUSED,
                        s -> s.background(StyleBox.flat(accent).radius(p.radius))
                                .textColor(p.onAccent))
                .style("menu_item", WidgetState.DISABLED, s -> s.textColor(p.muted))
                .style("scroll", s -> s.track(p.hover).accent(p.muted))
                .style("separator", s -> s.track(p.border).minSize(1f, 1f))
                .style(
                        "toast",
                        s -> s.background(box(p, p.panel).shadow(8f, p.shadow))
                                .padding(Insets.symmetric(14f, 10f))
                                .fontSize(15f))
                .variant("toast", "danger", s -> s.background(box(p, p.danger)).textColor(Color.WHITE))
                .variant(
                        "toast", "success", s -> s.background(box(p, p.success)).textColor(Color.WHITE))
                .style(
                        "foldable",
                        s -> s.background(StyleBox.flat(p.surface).radius(p.radius))
                                .padding(Insets.symmetric(10f, 6f))
                                .minSize(0f, 32f))
                .style(
                        "foldable",
                        WidgetState.HOVER,
                        s -> s.background(StyleBox.flat(p.hover).radius(p.radius)))
                .style(
                        "foldable",
                        WidgetState.FOCUSED,
                        s -> s.background(StyleBox.flat(p.hover).radius(p.radius)))
                .style(
                        "slot",
                        s -> s.background(box(p, p.field)).minSize(44f, 44f).padding(4f))
                .style("slot", WidgetState.HOVER, s -> s.background(box(p, p.hover)))
                .style("slot", WidgetState.FOCUSED, s -> s.background(box(p, p.hover)))
                .style(
                        "slot",
                        WidgetState.CHECKED,
                        s -> s.background(box(p, p.field).border(2f, accent)))
                .style("joystick", s -> s.track(p.surface.withAlpha(0.6f)).minSize(120f, 120f))
                .style("split", s -> s.track(p.border))
                .style("color_picker", s -> s.minSize(220f, 170f))
                .style(
                        "console",
                        s -> s.background(StyleBox.flat(Color.rgba(0x05070de8)))
                                .textColor(Color.rgb(0xd6deeb))
                                .mutedColor(Color.rgb(0x7f8aa3))
                                .fontSize(14f)
                                .padding(8f))
                .style(
                        "inspector",
                        s -> s.background(StyleBox.flat(Color.rgba(0x000000d0)))
                                .textColor(Color.rgb(0x9ef0a0))
                                .fontSize(12f)
                                .padding(6f)
                                .accent(Color.rgb(0x40ff80)))
                .build();
    }
}
