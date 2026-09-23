package dev.gulp.api.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * An immutable command definition: name, aliases, description, typed arguments and subcommands.
 *
 * <pre>{@code
 * commands().register(this, Command.builder("spawn")
 *         .aliases("s")
 *         .description("Spawns an entity next to the player")
 *         .argument(Arguments.key("type"))
 *         .argument(Arguments.integer("count", 1, 100).optional())
 *         .executes(ctx -> spawn(ctx.arg("type"), ctx.has("count") ? ctx.arg("count") : 1))
 *         .build());
 *
 * Command.builder("module")
 *         .subcommand(Command.builder("enable").argument(Arguments.string("id")).executes(...).build())
 *         .subcommand(Command.builder("disable").argument(Arguments.string("id")).executes(...).build())
 *         .build();
 * }</pre>
 */
public final class Command {

    private final String name;
    private final List<String> aliases;
    private final String description;
    private final List<Argument<?>> arguments;
    private final List<Command> subcommands;
    private final @Nullable CommandExecutor executor;

    private Command(Builder builder) {
        this.name = builder.name;
        this.aliases = List.copyOf(builder.aliases);
        this.description = builder.description;
        this.arguments = List.copyOf(builder.arguments);
        this.subcommands = List.copyOf(builder.subcommands);
        this.executor = builder.executor;
    }

    /**
     * Starts building a command.
     *
     * @param name the name, without spaces or leading slash; stored in lower case
     * @return the builder
     * @throws IllegalArgumentException if the name is empty or has spaces
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Creates a command whose whole remaining input is the optional greedy argument {@code "args"}.
     *
     * @param name the name
     * @param executor what it does
     * @return the command
     */
    public static Command simple(String name, CommandExecutor executor) {
        return builder(name)
                .argument(Arguments.greedy("args").optional())
                .executes(executor)
                .build();
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the alternative names.
     *
     * @return the aliases
     */
    public List<String> aliases() {
        return aliases;
    }

    /**
     * Returns the description shown by {@code /help}.
     *
     * @return the description, possibly empty
     */
    public String description() {
        return description;
    }

    /**
     * Returns the arguments in order.
     *
     * @return the arguments
     */
    public List<Argument<?>> arguments() {
        return arguments;
    }

    /**
     * Returns the subcommands.
     *
     * @return the subcommands
     */
    public List<Command> subcommands() {
        return subcommands;
    }

    /**
     * Returns the executor.
     *
     * @return the executor, or {@code null} for a command that only groups subcommands
     */
    public @Nullable CommandExecutor executor() {
        return executor;
    }

    /**
     * Returns whether a typed name is this command's name or one of its aliases.
     *
     * @param label the typed name
     * @return {@code true} if it matches, ignoring case
     */
    public boolean matches(String label) {
        if (name.equalsIgnoreCase(label)) {
            return true;
        }
        for (String alias : aliases) {
            if (alias.equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the usage line.
     *
     * @return for example {@code /spawn <type> [count]} or {@code /module enable|disable}
     */
    public String usage() {
        StringBuilder usage = new StringBuilder("/").append(name);
        if (!subcommands.isEmpty()) {
            usage.append(' ');
            for (int i = 0; i < subcommands.size(); i++) {
                usage.append(i == 0 ? "" : "|").append(subcommands.get(i).name);
            }
        }
        for (Argument<?> argument : arguments) {
            usage.append(' ').append(argument.usage());
        }
        return usage.toString();
    }

    /**
     * Returns the usage line.
     *
     * @return the usage
     */
    @Override
    public String toString() {
        return usage();
    }

    /**
     * Builds a {@link Command}.
     *
     * <pre>{@code
     * Command.builder("tp").argument(Arguments.floating("x")).argument(Arguments.floating("y")).executes(...).build();
     * }</pre>
     */
    public static final class Builder {

        private final String name;
        private final List<String> aliases = new ArrayList<>();
        private String description = "";
        private final List<Argument<?>> arguments = new ArrayList<>();
        private final List<Command> subcommands = new ArrayList<>();
        private @Nullable CommandExecutor executor;

        private Builder(String name) {
            this.name = requireName(name);
        }

        /**
         * Adds alternative names.
         *
         * @param aliases the aliases
         * @return this builder
         */
        public Builder aliases(String... aliases) {
            for (String alias : aliases) {
                this.aliases.add(requireName(alias));
            }
            return this;
        }

        /**
         * Sets the description.
         *
         * @param description one line for {@code /help}
         * @return this builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Adds an argument after the previous ones.
         *
         * @param argument the argument
         * @return this builder
         * @throws IllegalArgumentException if a required argument follows an optional one, anything follows a greedy
         *     argument, or the name is taken
         */
        public Builder argument(Argument<?> argument) {
            if (!arguments.isEmpty()) {
                Argument<?> last = arguments.getLast();
                if (last.type().isGreedy()) {
                    throw new IllegalArgumentException("No argument may follow the greedy argument " + last.name());
                }
                if (last.isOptional() && !argument.isOptional()) {
                    throw new IllegalArgumentException(
                            "Required argument " + argument.name() + " cannot follow optional " + last.name());
                }
            }
            for (Argument<?> existing : arguments) {
                if (existing.name().equals(argument.name())) {
                    throw new IllegalArgumentException("Duplicate argument name " + argument.name());
                }
            }
            arguments.add(argument);
            return this;
        }

        /**
         * Adds a subcommand, chosen by the first word after this command's name.
         *
         * @param subcommand the subcommand
         * @return this builder
         */
        public Builder subcommand(Command subcommand) {
            subcommands.add(subcommand);
            return this;
        }

        /**
         * Sets what the command does.
         *
         * @param executor the executor
         * @return this builder
         */
        public Builder executes(CommandExecutor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Builds the command.
         *
         * @return the command
         * @throws IllegalStateException if it has neither an executor nor subcommands, or has both arguments and
         *     subcommands
         */
        public Command build() {
            if (executor == null && subcommands.isEmpty()) {
                throw new IllegalStateException("Command /" + name + " needs an executor or subcommands");
            }
            if (!arguments.isEmpty() && !subcommands.isEmpty()) {
                throw new IllegalStateException("Command /" + name + " cannot have both arguments and subcommands");
            }
            return new Command(this);
        }

        private static String requireName(String name) {
            if (name.isEmpty() || name.indexOf(' ') >= 0 || name.startsWith("/")) {
                throw new IllegalArgumentException("Invalid command name '" + name + "'");
            }
            return name.toLowerCase(Locale.ROOT);
        }
    }
}
