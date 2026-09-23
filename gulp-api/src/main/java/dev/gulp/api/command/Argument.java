package dev.gulp.api.command;

/**
 * A named, typed command argument. Create them with {@link Arguments}.
 *
 * <pre>{@code
 * Argument<Integer> count = Arguments.integer("count", 1, 64).optional();
 * }</pre>
 *
 * @param <T> the parsed type
 * @param name the name used with {@link CommandContext#arg(String)}
 * @param type the parser
 * @param isOptional whether the argument may be omitted; optional arguments must come last
 */
public record Argument<T>(String name, ArgumentType<T> type, boolean isOptional) {

    /**
     * Returns an optional copy of this argument.
     *
     * @return the optional argument
     */
    public Argument<T> optional() {
        return new Argument<>(name, type, true);
    }

    /**
     * Returns the usage form.
     *
     * @return {@code <name>} or {@code [name]} if optional
     */
    public String usage() {
        return isOptional ? "[" + name + "]" : "<" + name + ">";
    }
}
