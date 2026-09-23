package dev.gulp.api.text;

import dev.gulp.api.graphics.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * Parses markup into {@link Text}. Tags:
 *
 * <ul>
 *   <li>{@code [b]}, {@code [i]} — bold, italic;
 *   <li>{@code [color=gold]}, {@code [color=#ffd700]} — color by name or hex;
 *   <li>{@code [size=20]} — size in drawing units;
 *   <li>{@code [font=coins:pixel]} — a loaded font;
 *   <li>{@code [img=coins:icons/coin]} — an inline image (no closing tag);
 *   <li>{@code [link=shop]} — a link, reported by {@link TextLinkClickEvent};
 *   <li>{@code [wave]}, {@code [shake]}, {@code [rainbow]}, {@code [pulse]}, {@code [fade]} — animated effects.
 * </ul>
 *
 * <p>Every tag except {@code img} is closed with {@code [/name]}; tags still open at the end close there. {@code [[}
 * writes a literal {@code [}. Mistakes never throw: an unknown or misplaced tag stays visible as text and is reported to
 * the warning consumer.
 *
 * <pre>{@code
 * Text text = Markup.parse("[b]Uwaga:[/b] [color=red]mało życia[/color] [img=coins:icons/heart]");
 * }</pre>
 */
public final class Markup {

    private static final Map<String, Color> COLORS = Map.ofEntries(
            Map.entry("white", Color.WHITE),
            Map.entry("black", Color.BLACK),
            Map.entry("red", Color.RED),
            Map.entry("green", Color.GREEN),
            Map.entry("blue", Color.BLUE),
            Map.entry("yellow", Color.YELLOW),
            Map.entry("cyan", Color.CYAN),
            Map.entry("magenta", Color.MAGENTA),
            Map.entry("orange", Color.ORANGE),
            Map.entry("gray", Color.GRAY),
            Map.entry("grey", Color.GRAY),
            Map.entry("gold", Color.rgb(0xffd700)),
            Map.entry("silver", Color.rgb(0xc0c0c0)),
            Map.entry("purple", Color.rgb(0x8e44ad)),
            Map.entry("pink", Color.rgb(0xff77a8)),
            Map.entry("brown", Color.rgb(0x8b5a2b)),
            Map.entry("lime", Color.rgb(0x7fff00)),
            Map.entry("sky", Color.rgb(0x29adff)));

    private Markup() {}

    /**
     * Parses markup, ignoring warnings.
     *
     * @param markup the marked-up string
     * @return the text
     */
    public static Text parse(String markup) {
        return parse(markup, warning -> {});
    }

    /**
     * Parses markup.
     *
     * @param markup the marked-up string
     * @param warnings receives a message for every tag that could not be used
     * @return the text
     */
    public static Text parse(String markup, Consumer<String> warnings) {
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame("", t -> t));
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < markup.length()) {
            char c = markup.charAt(i);
            if (c != '[') {
                plain.append(c);
                i++;
                continue;
            }
            if (i + 1 < markup.length() && markup.charAt(i + 1) == '[') {
                plain.append('[');
                i += 2;
                continue;
            }
            int end = markup.indexOf(']', i);
            if (end < 0) {
                warnings.accept("Unclosed '[' at " + i + " in: " + markup);
                plain.append(markup, i, markup.length());
                break;
            }
            String tag = markup.substring(i + 1, end);
            String raw = markup.substring(i, end + 1);
            i = end + 1;
            if (tag.startsWith("/")) {
                String name = tag.substring(1).trim().toLowerCase(Locale.ROOT);
                if (stack.size() > 1 && stack.peek().name.equals(name)) {
                    flush(stack.peek(), plain);
                    Frame closed = stack.pop();
                    stack.peek().children.add(closed.build());
                } else {
                    warnings.accept("Closing tag " + raw + " does not match an open tag in: " + markup);
                    plain.append(raw);
                }
                continue;
            }
            int equals = tag.indexOf('=');
            String name = (equals < 0 ? tag : tag.substring(0, equals)).trim().toLowerCase(Locale.ROOT);
            String value = equals < 0 ? null : tag.substring(equals + 1).trim();
            if (name.equals("img")) {
                if (value == null || value.isEmpty()) {
                    warnings.accept("Tag " + raw + " needs an image key in: " + markup);
                    plain.append(raw);
                } else {
                    flush(stack.peek(), plain);
                    stack.peek().children.add(Text.image(value));
                }
                continue;
            }
            UnaryOperator<Text> format = format(name, value);
            if (format == null) {
                warnings.accept("Unknown or invalid tag " + raw + " in: " + markup);
                plain.append(raw);
                continue;
            }
            flush(stack.peek(), plain);
            stack.push(new Frame(name, format));
        }
        flush(stack.peek(), plain);
        while (stack.size() > 1) {
            Frame open = stack.pop();
            stack.peek().children.add(open.build());
        }
        return stack.pop().build();
    }

    private static void flush(Frame frame, StringBuilder plain) {
        if (!plain.isEmpty()) {
            frame.children.add(Text.of(plain.toString()));
            plain.setLength(0);
        }
    }

    private static @Nullable UnaryOperator<Text> format(String name, @Nullable String value) {
        return switch (name) {
            case "b" -> value == null ? Text::bold : null;
            case "i" -> value == null ? Text::italic : null;
            case "wave" -> value == null ? t -> t.effect(TextEffect.WAVE) : null;
            case "shake" -> value == null ? t -> t.effect(TextEffect.SHAKE) : null;
            case "rainbow" -> value == null ? t -> t.effect(TextEffect.RAINBOW) : null;
            case "pulse" -> value == null ? t -> t.effect(TextEffect.PULSE) : null;
            case "fade" -> value == null ? t -> t.effect(TextEffect.FADE) : null;
            case "color" -> {
                Color color = value == null ? null : color(value);
                yield color == null ? null : t -> t.color(color);
            }
            case "size" -> {
                Float size = value == null ? null : number(value);
                yield size == null || size <= 0f ? null : t -> t.size(size);
            }
            case "font" -> value == null || value.isEmpty() ? null : t -> t.font(value);
            case "link" -> value == null || value.isEmpty() ? null : t -> t.link(value);
            default -> null;
        };
    }

    /**
     * Returns a named color or parses a hex color.
     *
     * @param value a name such as {@code gold} or a hex value such as {@code #ffd700}
     * @return the color, or {@code null} if it is neither
     */
    public static @Nullable Color color(String value) {
        Color named = COLORS.get(value.toLowerCase(Locale.ROOT));
        if (named != null) {
            return named;
        }
        try {
            return Color.hex(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static @Nullable Float number(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final class Frame {
        final String name;
        final UnaryOperator<Text> format;
        final List<Text> children = new ArrayList<>();

        Frame(String name, UnaryOperator<Text> format) {
            this.name = name;
            this.format = format;
        }

        Text build() {
            // The format goes on a parent node, so inner tags keep their own values.
            return format.apply(combine(children));
        }

        private static Text combine(List<Text> parts) {
            Text node = Text.empty();
            for (Text part : parts) {
                node = node.append(part);
            }
            return node;
        }
    }
}
