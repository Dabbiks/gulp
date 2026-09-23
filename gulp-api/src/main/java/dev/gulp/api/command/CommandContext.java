package dev.gulp.api.command;

import org.jspecify.annotations.Nullable;

/**
 * Parsed arguments of a running command and a way to reply.
 *
 * <pre>{@code
 * Command.builder("give")
 *         .argument(Arguments.key("item"))
 *         .argument(Arguments.integer("count", 1, 64).optional())
 *         .executes(ctx -> {
 *             Key item = ctx.arg("item");
 *             int count = ctx.has("count") ? ctx.arg("count") : 1;
 *             ctx.reply("Gave " + count + " x " + item);
 *         })
 *         .build();
 * }</pre>
 */
public interface CommandContext {

    /**
     * Returns the command being run (the subcommand, if one matched).
     *
     * @return the command
     */
    Command command();

    /**
     * Returns the full input line.
     *
     * @return the input, without the leading slash
     */
    String input();

    /**
     * Returns a parsed argument.
     *
     * @param <T> the argument type
     * @param name the argument name
     * @return the value
     * @throws IllegalArgumentException if the command has no such argument or an optional one was not given
     */
    <T> T arg(String name);

    /**
     * Returns a parsed argument or {@code null} when an optional argument was not given.
     *
     * @param <T> the argument type
     * @param name the argument name
     * @return the value or {@code null}
     */
    <T> @Nullable T argOrNull(String name);

    /**
     * Returns whether an argument was given.
     *
     * @param name the argument name
     * @return {@code true} if present
     */
    boolean has(String name);

    /**
     * Sends a line of output to whoever ran the command (console or terminal).
     *
     * @param message the line
     */
    void reply(String message);
}
