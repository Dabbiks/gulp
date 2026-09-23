package dev.gulp.api.text;

import dev.gulp.api.graphics.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Rich text: pieces of plain text, translation keys and inline images, each with optional formatting that children
 * inherit. Immutable; every method returns a changed copy.
 *
 * <pre>{@code
 * Text coins = Text.of("Monety: ").append(Text.of("10").color(Color.hex("#ffd700")).bold());
 * Text title = Text.translatable("hud.coins", 10);
 * Text fancy = Text.markup("[wave]Hurra![/wave] Masz [color=gold]10[/color] monet");
 * }</pre>
 */
public final class Text {

    private static final Text EMPTY = new Text("", null, new Object[0], null, Format.NONE, List.of());

    /** Formatting of one node; {@code null} fields are inherited. */
    private record Format(
            @Nullable Color color,
            @Nullable Boolean bold,
            @Nullable Boolean italic,
            @Nullable Float size,
            @Nullable String font,
            @Nullable String link,
            Set<TextEffect> effects) {
        static final Format NONE = new Format(null, null, null, null, null, null, Set.of());
    }

    private final String content;
    private final @Nullable String translationKey;
    private final Object[] arguments;
    private final @Nullable String image;
    private final Format format;
    private final List<Text> children;

    private Text(
            String content,
            @Nullable String translationKey,
            Object[] arguments,
            @Nullable String image,
            Format format,
            List<Text> children) {
        this.content = content;
        this.translationKey = translationKey;
        this.arguments = arguments;
        this.image = image;
        this.format = format;
        this.children = children;
    }

    /**
     * Returns empty text.
     *
     * @return the text
     */
    public static Text empty() {
        return EMPTY;
    }

    /**
     * Returns plain text.
     *
     * @param content the characters; {@code \n} starts a new line
     * @return the text
     */
    public static Text of(String content) {
        return new Text(content, null, new Object[0], null, Format.NONE, List.of());
    }

    /**
     * Returns text looked up in the translations when it is laid out, so it follows language changes.
     *
     * @param key the translation key
     * @param arguments values for {@code {0}}, {@code {1}}, …
     * @return the text
     */
    public static Text translatable(String key, Object... arguments) {
        return new Text("", key, arguments.clone(), null, Format.NONE, List.of());
    }

    /**
     * Returns an inline image as tall as the text.
     *
     * @param regionKey key of a loaded texture region, for example {@code coins:icons/coin}
     * @return the text
     */
    public static Text image(String regionKey) {
        return new Text("", null, new Object[0], regionKey, Format.NONE, List.of());
    }

    /**
     * Parses markup such as {@code [b]bold[/b]}; see {@link Markup}.
     *
     * @param markup the marked-up string
     * @return the text; malformed tags stay visible as plain text
     */
    public static Text markup(String markup) {
        return Markup.parse(markup);
    }

    /**
     * Returns this text followed by another, which inherits this text's formatting.
     *
     * @param child the text to add
     * @return the combined text
     */
    public Text append(Text child) {
        List<Text> list = new ArrayList<>(children);
        list.add(child);
        return new Text(content, translationKey, arguments, image, format, Collections.unmodifiableList(list));
    }

    /**
     * Returns this text followed by plain text.
     *
     * @param child the characters to add
     * @return the combined text
     */
    public Text append(String child) {
        return append(of(child));
    }

    private Text with(Format newFormat) {
        return new Text(content, translationKey, arguments, image, newFormat, children);
    }

    /**
     * Returns a colored copy.
     *
     * @param color the color
     * @return the text
     */
    public Text color(Color color) {
        return with(
                new Format(color, format.bold, format.italic, format.size, format.font, format.link, format.effects));
    }

    /**
     * Returns a bold copy.
     *
     * @return the text
     */
    public Text bold() {
        return with(
                new Format(format.color, true, format.italic, format.size, format.font, format.link, format.effects));
    }

    /**
     * Returns an italic copy.
     *
     * @return the text
     */
    public Text italic() {
        return with(new Format(format.color, format.bold, true, format.size, format.font, format.link, format.effects));
    }

    /**
     * Returns a copy with another size.
     *
     * @param size the size in drawing units
     * @return the text
     */
    public Text size(float size) {
        return with(
                new Format(format.color, format.bold, format.italic, size, format.font, format.link, format.effects));
    }

    /**
     * Returns a copy in another font.
     *
     * @param fontKey key of a loaded font, for example {@code coins:fonts/pixel}
     * @return the text
     */
    public Text font(String fontKey) {
        return with(new Format(
                format.color, format.bold, format.italic, format.size, fontKey, format.link, format.effects));
    }

    /**
     * Returns a copy that is a link; clicking it fires {@link TextLinkClickEvent}.
     *
     * @param id the link id
     * @return the text
     */
    public Text link(String id) {
        return with(new Format(format.color, format.bold, format.italic, format.size, format.font, id, format.effects));
    }

    /**
     * Returns a copy with an animated effect added.
     *
     * @param effect the effect
     * @return the text
     */
    public Text effect(TextEffect effect) {
        EnumSet<TextEffect> set = EnumSet.noneOf(TextEffect.class);
        set.addAll(format.effects);
        set.add(effect);
        return with(new Format(
                format.color,
                format.bold,
                format.italic,
                format.size,
                format.font,
                format.link,
                Collections.unmodifiableSet(set)));
    }

    /**
     * Returns the characters of this node (not its children).
     *
     * @return the content
     */
    public String content() {
        return content;
    }

    /**
     * Returns the translation key of this node.
     *
     * @return the key, or {@code null} for plain text
     */
    public @Nullable String translationKey() {
        return translationKey;
    }

    /**
     * Returns the translation arguments.
     *
     * @return a copy of the arguments
     */
    public Object[] arguments() {
        return arguments.clone();
    }

    /**
     * Returns the inline image of this node.
     *
     * @return the region key, or {@code null}
     */
    public @Nullable String image() {
        return image;
    }

    /**
     * Returns the color set on this node.
     *
     * @return the color, or {@code null} to inherit
     */
    public @Nullable Color colorOrNull() {
        return format.color;
    }

    /**
     * Returns bold as set on this node.
     *
     * @return the value, or {@code null} to inherit
     */
    public @Nullable Boolean boldOrNull() {
        return format.bold;
    }

    /**
     * Returns italic as set on this node.
     *
     * @return the value, or {@code null} to inherit
     */
    public @Nullable Boolean italicOrNull() {
        return format.italic;
    }

    /**
     * Returns the size set on this node.
     *
     * @return the size, or {@code null} to inherit
     */
    public @Nullable Float sizeOrNull() {
        return format.size;
    }

    /**
     * Returns the font set on this node.
     *
     * @return the font key, or {@code null} to inherit
     */
    public @Nullable String fontOrNull() {
        return format.font;
    }

    /**
     * Returns the link set on this node.
     *
     * @return the link id, or {@code null} to inherit
     */
    public @Nullable String linkOrNull() {
        return format.link;
    }

    /**
     * Returns the effects added on this node; children add theirs to these.
     *
     * @return the effects
     */
    public Set<TextEffect> effects() {
        return format.effects;
    }

    /**
     * Returns the child nodes.
     *
     * @return the children
     */
    public List<Text> children() {
        return children;
    }

    /**
     * Returns the text without formatting; translation keys appear as the keys.
     *
     * @return the plain string
     */
    public String plain() {
        StringBuilder builder = new StringBuilder();
        plain(builder);
        return builder.toString();
    }

    private void plain(StringBuilder builder) {
        builder.append(translationKey != null ? translationKey : content);
        for (Text child : children) {
            child.plain(builder);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Text text
                && content.equals(text.content)
                && Objects.equals(translationKey, text.translationKey)
                && Arrays.equals(arguments, text.arguments)
                && Objects.equals(image, text.image)
                && format.equals(text.format)
                && children.equals(text.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, translationKey, Arrays.hashCode(arguments), image, format, children);
    }

    @Override
    public String toString() {
        return "Text[" + plain() + "]";
    }
}
