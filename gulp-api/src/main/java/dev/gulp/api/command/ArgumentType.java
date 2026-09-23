package dev.gulp.api.command;

import java.util.List;

/**
 * Parses one command argument from text and suggests completions.
 *
 * <pre>{@code
 * ArgumentType<Color> color = new ArgumentType<>() {
 *     public Color parse(String token) { return Color.rgb(Integer.parseInt(token, 16)); }
 *     public List<String> suggest(String partial) { return List.of("ff0000", "00ff00"); }
 *     public String description() { return "hex color"; }
 * };
 * }</pre>
 *
 * @param <T> the parsed type
 */
public interface ArgumentType<T> {

    /**
     * Parses a token.
     *
     * @param token the text of the argument
     * @return the value
     * @throws CommandException if the text is invalid; the message is shown to the user
     */
    T parse(String token);

    /**
     * Suggests completions.
     *
     * @param partial what has been typed so far for this argument
     * @return suggestions starting with {@code partial}, possibly empty
     */
    List<String> suggest(String partial);

    /**
     * Describes the expected input for usage messages.
     *
     * @return for example {@code "int 1..64"}
     */
    String description();

    /**
     * Returns whether this argument consumes the rest of the line, spaces included.
     *
     * @return {@code false} by default
     */
    default boolean isGreedy() {
        return false;
    }
}
