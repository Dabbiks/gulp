package dev.gulp.api.ui;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Keyed;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Styles for every widget type and state. A theme set on a node ({@link Node#theme}) applies to its whole subtree;
 * {@code ui().setTheme(...)} sets it for everything. Built in: {@link #LIGHT}, {@link #DARK} (the default) and {@link
 * #PIXEL}, each with {@link #withAccent} and {@link #highContrast()}. Themes are registered in {@code
 * Registries.THEME}.
 *
 * <p>A style is resolved from layers, later ones winning: the theme base, the widget type, the type in the current
 * state, the variant ({@link Node#variant}), the variant in the state, and the node's own {@link Node#style}. Widget
 * types are named in lower case: {@code label}, {@code button}, {@code panel}, {@code field}, {@code slider}, {@code
 * list_item} and so on (each widget's Javadoc names its type).
 *
 * <pre>{@code
 * ui().setTheme(Theme.DARK.withAccent(Color.hex("#7c5cff")));
 *
 * Theme neon = Theme.builder(key("neon"))
 *         .parent(Theme.DARK)
 *         .style("button", s -> s.background(StyleBox.flat(Color.BLACK).border(2, Color.hex("#ff00aa"))))
 *         .style("button", WidgetState.HOVER, s -> s.textColor(Color.hex("#ff00aa")))
 *         .variant("label", "neon", s -> s.textColor(Color.hex("#00ffcc")))
 *         .build();
 * }</pre>
 */
public final class Theme implements Keyed {

    /** Light theme with rounded corners. */
    public static final Theme LIGHT = Themes.light(Themes.BLUE, false);
    /** Dark theme with rounded corners; the default. */
    public static final Theme DARK = Themes.dark(Themes.BLUE, false);
    /** Square pixel-art theme with hard borders and shadows. */
    public static final Theme PIXEL = Themes.pixel(Themes.GOLD, false);
    /** The theme used when nothing else is set: {@link #DARK}. */
    public static final Theme DEFAULT = DARK;

    private final Key key;
    private final @Nullable Theme parent;
    private final Map<String, Style> layers;
    private final float tooltipDelay;
    private final @Nullable Function<Color, Theme> recolor;
    private final @Nullable Theme contrast;
    private final Map<String, Style> resolved = new HashMap<>();

    private Theme(Builder builder) {
        this.key = builder.key;
        this.parent = builder.parent;
        this.layers = new LinkedHashMap<>(builder.layers);
        this.tooltipDelay = builder.tooltipDelay;
        this.recolor = builder.recolor;
        this.contrast = builder.contrast;
    }

    /**
     * Starts building a theme.
     *
     * @param key the key
     * @return the builder
     */
    public static Builder builder(Key key) {
        return new Builder(key);
    }

    @Override
    public Key key() {
        return key;
    }

    /**
     * Returns how long the pointer rests on a node before its tooltip shows.
     *
     * @return seconds
     */
    public float tooltipDelay() {
        return tooltipDelay;
    }

    /**
     * Resolves the style of a widget type, variant and state. Results are cached; do not change the returned style.
     *
     * @param type the widget type, such as {@code button}
     * @param variant the variant, or an empty string
     * @param state the state
     * @return the merged style
     */
    public Style resolve(String type, String variant, WidgetState state) {
        String cacheKey = type + '/' + variant + '/' + state.ordinal();
        Style style = resolved.get(cacheKey);
        if (style == null) {
            style = new Style();
            collect(style, type, variant, state);
            resolved.put(cacheKey, style);
        }
        return style;
    }

    private void collect(Style into, String type, String variant, WidgetState state) {
        Theme up = parent;
        if (up != null) {
            up.collect(into, type, variant, state);
        }
        layer(into, "");
        layer(into, type);
        if (state != WidgetState.NORMAL) {
            layer(into, type + "#" + state.ordinal());
        }
        if (!variant.isEmpty()) {
            layer(into, type + "." + variant);
            if (state != WidgetState.NORMAL) {
                layer(into, type + "." + variant + "#" + state.ordinal());
            }
        }
    }

    private void layer(Style into, String name) {
        Style style = layers.get(name);
        if (style != null) {
            into.apply(style);
        }
    }

    /**
     * Returns this theme with another accent colour (sliders, selection, focus, primary buttons).
     *
     * @param accent the colour
     * @return a new theme
     */
    public Theme withAccent(Color accent) {
        Function<Color, Theme> factory = recolor;
        if (factory != null) {
            return factory.apply(accent);
        }
        return builder(key).parent(this).base(s -> s.accent(accent)).build();
    }

    /**
     * Returns the high-contrast variant: stronger borders and text, no translucency.
     *
     * @return the variant, or this theme if it has none
     */
    public Theme highContrast() {
        Theme high = contrast;
        return high != null ? high : this;
    }

    @Override
    public String toString() {
        return "Theme[" + key + "]";
    }

    /** Builds a {@link Theme}. */
    public static final class Builder {
        private final Key key;
        private @Nullable Theme parent;
        private final Map<String, Style> layers = new LinkedHashMap<>();
        private float tooltipDelay = 0.5f;
        private @Nullable Function<Color, Theme> recolor;
        private @Nullable Theme contrast;

        private Builder(Key key) {
            this.key = key;
        }

        /**
         * Inherits every style of another theme; this theme's layers go on top.
         *
         * @param theme the parent
         * @return this builder
         */
        public Builder parent(Theme theme) {
            this.parent = theme;
            return this;
        }

        /**
         * Changes the base style shared by every widget type.
         *
         * @param change edits the style
         * @return this builder
         */
        public Builder base(Consumer<Style> change) {
            change.accept(layers.computeIfAbsent("", k -> new Style()));
            return this;
        }

        /**
         * Changes the style of a widget type.
         *
         * @param type the type, such as {@code button}
         * @param change edits the style
         * @return this builder
         */
        public Builder style(String type, Consumer<Style> change) {
            change.accept(layers.computeIfAbsent(type, k -> new Style()));
            return this;
        }

        /**
         * Changes the style of a widget type in one state.
         *
         * @param type the type
         * @param state the state
         * @param change edits the style
         * @return this builder
         */
        public Builder style(String type, WidgetState state, Consumer<Style> change) {
            change.accept(layers.computeIfAbsent(type + "#" + state.ordinal(), k -> new Style()));
            return this;
        }

        /**
         * Changes the style of a variant of a widget type, used by {@link Node#variant}.
         *
         * @param type the type
         * @param variant the variant name
         * @param change edits the style
         * @return this builder
         */
        public Builder variant(String type, String variant, Consumer<Style> change) {
            change.accept(layers.computeIfAbsent(type + "." + variant, k -> new Style()));
            return this;
        }

        /**
         * Changes the style of a variant in one state.
         *
         * @param type the type
         * @param variant the variant name
         * @param state the state
         * @param change edits the style
         * @return this builder
         */
        public Builder variant(String type, String variant, WidgetState state, Consumer<Style> change) {
            change.accept(layers.computeIfAbsent(type + "." + variant + "#" + state.ordinal(), k -> new Style()));
            return this;
        }

        /**
         * Sets how long the pointer rests on a node before its tooltip shows.
         *
         * @param seconds the delay
         * @return this builder
         */
        public Builder tooltipDelay(float seconds) {
            this.tooltipDelay = seconds;
            return this;
        }

        Builder recolor(Function<Color, Theme> factory) {
            this.recolor = factory;
            return this;
        }

        Builder contrast(@Nullable Theme high) {
            this.contrast = high;
            return this;
        }

        /**
         * Builds the theme.
         *
         * @return the theme
         */
        public Theme build() {
            return new Theme(this);
        }
    }
}
