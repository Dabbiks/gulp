package dev.gulp.api.command;

import dev.gulp.api.Owner;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The command registry. Commands are removed when their owner is disabled.
 *
 * <pre>{@code
 * commands().register(this, Command.builder("god").executes(ctx -> godMode = !godMode).build());
 * commands().dispatch("/god", line -> logger().info(line));
 * }</pre>
 *
 * <p>Built-in: {@code /help}, {@code /tps}, {@code /modules}, {@code /module enable|disable <id>},
 * {@code /reload config}, {@code /timescale <x>}. Later stages add {@code /spawn}, {@code /tp}, {@code /debug} and
 * {@code /profile}.
 */
public interface Commands {

    /**
     * Registers a command.
     *
     * @param owner the owner; the command is removed when it is disabled
     * @param command the command
     * @return the command
     * @throws IllegalArgumentException if its name or an alias is taken
     */
    Command register(Owner owner, Command command);

    /**
     * Removes a command.
     *
     * @param name the name or an alias
     * @return {@code true} if a command was removed
     */
    boolean unregister(String name);

    /**
     * Returns a command.
     *
     * @param name the name or an alias, ignoring case
     * @return the command, or {@code null}
     */
    @Nullable Command get(String name);

    /**
     * Returns every registered command.
     *
     * @return the commands sorted by name
     */
    Collection<Command> all();

    /**
     * Parses and runs an input line on the main thread. Errors are replied, not thrown.
     *
     * @param input the line, with or without a leading slash
     * @param output receives the reply lines
     * @return {@code true} if a command ran without error
     */
    boolean dispatch(String input, Consumer<String> output);

    /**
     * Suggests completions for a partial input line.
     *
     * @param partial the line typed so far
     * @return full lines that complete it
     */
    List<String> complete(String partial);

    /**
     * Returns the developer console.
     *
     * @return the console
     */
    Console console();
}
