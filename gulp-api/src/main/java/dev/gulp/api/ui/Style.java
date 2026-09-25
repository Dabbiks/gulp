package dev.gulp.api.ui;

import dev.gulp.api.audio.Sound;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.text.FontFamily;
import dev.gulp.api.text.TextStyle;
import org.jspecify.annotations.Nullable;

/**
 * Look of a node in one state: background, text, padding, spacing, accent colours and sounds. A {@link Theme} defines
 * styles per widget type and state; {@link Node#style} overrides single values for one node. Values left unset come
 * from the layer below (node, variant, type, theme base).
 *
 * <pre>{@code
 * button("Delete").style(s -> s.textColor(Color.RED).background(StyleBox.flat(Color.hex("#401010")).radius(6)));
 * column(items).style(s -> s.gap(4));
 * }</pre>
 */
public final class Style {

    private @Nullable StyleBox background;
    private @Nullable Color textColor;
    private @Nullable Color mutedColor;
    private @Nullable Float fontSize;
    private @Nullable FontFamily font;
    private @Nullable Boolean bold;
    private @Nullable Insets padding;
    private @Nullable Float gap;
    private @Nullable Color accent;
    private @Nullable Color track;
    private @Nullable Color focusColor;
    private @Nullable Float focusWidth;
    private @Nullable Float minWidth;
    private @Nullable Float minHeight;
    private @Nullable Sound clickSound;
    private @Nullable Sound hoverSound;
    private @Nullable Float transition;
    private @Nullable TextStyle textStyle;

    /** Creates a style with nothing set. */
    public Style() {}

    /**
     * Copies every value set in another style over this one.
     *
     * @param other the style on top
     * @return this style
     */
    public Style apply(Style other) {
        if (other.background != null) {
            background = other.background;
        }
        if (other.textColor != null) {
            textColor = other.textColor;
        }
        if (other.mutedColor != null) {
            mutedColor = other.mutedColor;
        }
        if (other.fontSize != null) {
            fontSize = other.fontSize;
        }
        if (other.font != null) {
            font = other.font;
        }
        if (other.bold != null) {
            bold = other.bold;
        }
        if (other.padding != null) {
            padding = other.padding;
        }
        if (other.gap != null) {
            gap = other.gap;
        }
        if (other.accent != null) {
            accent = other.accent;
        }
        if (other.track != null) {
            track = other.track;
        }
        if (other.focusColor != null) {
            focusColor = other.focusColor;
        }
        if (other.focusWidth != null) {
            focusWidth = other.focusWidth;
        }
        if (other.minWidth != null) {
            minWidth = other.minWidth;
        }
        if (other.minHeight != null) {
            minHeight = other.minHeight;
        }
        if (other.clickSound != null) {
            clickSound = other.clickSound;
        }
        if (other.hoverSound != null) {
            hoverSound = other.hoverSound;
        }
        if (other.transition != null) {
            transition = other.transition;
        }
        textStyle = null;
        return this;
    }

    /**
     * Returns a copy.
     *
     * @return a new style with the same values
     */
    public Style copy() {
        return new Style().apply(this);
    }

    // ------------------------------------------------------------------ setters

    /**
     * Sets the background.
     *
     * @param box the box
     * @return this style
     */
    public Style background(StyleBox box) {
        this.background = box;
        return this;
    }

    /**
     * Sets a flat background colour.
     *
     * @param color the colour
     * @return this style
     */
    public Style background(Color color) {
        StyleBox current = background;
        this.background = current instanceof StyleBox.Flat flat ? flat.color(color) : StyleBox.flat(color);
        return this;
    }

    /**
     * Sets the text colour.
     *
     * @param color the colour
     * @return this style
     */
    public Style textColor(Color color) {
        this.textColor = color;
        textStyle = null;
        return this;
    }

    /**
     * Sets the colour of secondary text, such as placeholders.
     *
     * @param color the colour
     * @return this style
     */
    public Style mutedColor(Color color) {
        this.mutedColor = color;
        return this;
    }

    /**
     * Sets the font size.
     *
     * @param size UI points
     * @return this style
     */
    public Style fontSize(float size) {
        this.fontSize = size;
        textStyle = null;
        return this;
    }

    /**
     * Sets the font family.
     *
     * @param family the family, for example from {@code FontFamily.of(font)}
     * @return this style
     */
    public Style font(FontFamily family) {
        this.font = family;
        textStyle = null;
        return this;
    }

    /**
     * Makes the text bold or regular.
     *
     * @param on whether bold
     * @return this style
     */
    public Style bold(boolean on) {
        this.bold = on;
        textStyle = null;
        return this;
    }

    /**
     * Sets the space between the background edge and the content.
     *
     * @param insets the padding
     * @return this style
     */
    public Style padding(Insets insets) {
        this.padding = insets;
        return this;
    }

    /**
     * Sets the same padding on every edge.
     *
     * @param value UI points
     * @return this style
     */
    public Style padding(float value) {
        return padding(Insets.all(value));
    }

    /**
     * Sets the space between children of rows, columns, grids and flows.
     *
     * @param value UI points
     * @return this style
     */
    public Style gap(float value) {
        this.gap = value;
        return this;
    }

    /**
     * Sets the accent colour: slider and progress fill, checkbox tick, selection.
     *
     * @param color the colour
     * @return this style
     */
    public Style accent(Color color) {
        this.accent = color;
        return this;
    }

    /**
     * Sets the colour of tracks behind sliders, progress bars and scroll bars.
     *
     * @param color the colour
     * @return this style
     */
    public Style track(Color color) {
        this.track = color;
        return this;
    }

    /**
     * Sets the colour of the focus frame.
     *
     * @param color the colour
     * @return this style
     */
    public Style focusColor(Color color) {
        this.focusColor = color;
        return this;
    }

    /**
     * Sets the thickness of the focus frame.
     *
     * @param width UI points
     * @return this style
     */
    public Style focusWidth(float width) {
        this.focusWidth = width;
        return this;
    }

    /**
     * Sets a minimum size for widgets of this style, such as a comfortable button height.
     *
     * @param width minimum width
     * @param height minimum height
     * @return this style
     */
    public Style minSize(float width, float height) {
        this.minWidth = width;
        this.minHeight = height;
        return this;
    }

    /**
     * Sets the sound played on click.
     *
     * @param sound the sound, usually on the {@code ui} bus
     * @return this style
     */
    public Style clickSound(Sound sound) {
        this.clickSound = sound;
        return this;
    }

    /**
     * Sets the sound played when the pointer or focus enters.
     *
     * @param sound the sound
     * @return this style
     */
    public Style hoverSound(Sound sound) {
        this.hoverSound = sound;
        return this;
    }

    /**
     * Sets how long state changes (such as the hover colour) blend.
     *
     * @param seconds duration, {@code 0} to switch at once
     * @return this style
     */
    public Style transition(float seconds) {
        this.transition = seconds;
        return this;
    }

    // ------------------------------------------------------------------ resolved values

    /**
     * Returns the background.
     *
     * @return the box, {@link StyleBox#NONE} if unset
     */
    public StyleBox background() {
        return background != null ? background : StyleBox.NONE;
    }

    /**
     * Returns the text colour.
     *
     * @return the colour, white if unset
     */
    public Color textColor() {
        return textColor != null ? textColor : Color.WHITE;
    }

    /**
     * Returns the colour of secondary text.
     *
     * @return the colour, the text colour at half opacity if unset
     */
    public Color mutedColor() {
        return mutedColor != null
                ? mutedColor
                : textColor().withAlpha(textColor().a() * 0.5f);
    }

    /**
     * Returns the font size.
     *
     * @return UI points, 16 if unset
     */
    public float fontSize() {
        return fontSize != null ? fontSize : 16f;
    }

    /**
     * Returns the font family.
     *
     * @return the family, or {@code null} for the engine default
     */
    public @Nullable FontFamily font() {
        return font;
    }

    /**
     * Returns whether text is bold.
     *
     * @return {@code false} if unset
     */
    public boolean isBold() {
        return bold != null && bold;
    }

    /**
     * Returns the padding.
     *
     * @return the insets, zero if unset
     */
    public Insets padding() {
        return padding != null ? padding : Insets.ZERO;
    }

    /**
     * Returns the gap between children.
     *
     * @return UI points, 6 if unset
     */
    public float gap() {
        return gap != null ? gap : 6f;
    }

    /**
     * Returns the accent colour.
     *
     * @return the colour, a blue if unset
     */
    public Color accent() {
        return accent != null ? accent : Color.rgb(0x4f8cff);
    }

    /**
     * Returns the track colour.
     *
     * @return the colour, translucent grey if unset
     */
    public Color track() {
        return track != null ? track : Color.rgba(0x80808060);
    }

    /**
     * Returns the focus frame colour.
     *
     * @return the colour, the accent if unset
     */
    public Color focusColor() {
        return focusColor != null ? focusColor : accent();
    }

    /**
     * Returns the focus frame thickness.
     *
     * @return UI points, 2 if unset
     */
    public float focusWidth() {
        return focusWidth != null ? focusWidth : 2f;
    }

    /**
     * Returns the minimum width.
     *
     * @return UI points, 0 if unset
     */
    public float minWidth() {
        return minWidth != null ? minWidth : 0f;
    }

    /**
     * Returns the minimum height.
     *
     * @return UI points, 0 if unset
     */
    public float minHeight() {
        return minHeight != null ? minHeight : 0f;
    }

    /**
     * Returns the click sound.
     *
     * @return the sound, or {@code null}
     */
    public @Nullable Sound clickSound() {
        return clickSound;
    }

    /**
     * Returns the hover sound.
     *
     * @return the sound, or {@code null}
     */
    public @Nullable Sound hoverSound() {
        return hoverSound;
    }

    /**
     * Returns the state transition time.
     *
     * @return seconds, 0.1 if unset
     */
    public float transition() {
        return transition != null ? transition : 0.1f;
    }

    /**
     * Returns the text style made of the font, size, colour and weight.
     *
     * @return the text style, cached until a text value changes
     */
    public TextStyle textStyle() {
        TextStyle cached = textStyle;
        if (cached == null) {
            cached = TextStyle.DEFAULT
                    .font(font)
                    .size(fontSize())
                    .color(textColor())
                    .bold(isBold());
            textStyle = cached;
        }
        return cached;
    }
}
