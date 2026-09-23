package dev.gulp.core.command;

import dev.gulp.api.Owner;
import dev.gulp.api.command.Argument;
import dev.gulp.api.command.Command;
import dev.gulp.api.command.CommandContext;
import dev.gulp.api.command.CommandException;
import dev.gulp.api.command.Commands;
import dev.gulp.api.command.Console;
import dev.gulp.core.CoreContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** {@link Commands}: registry by name and alias, whitespace-separated parsing, typed arguments and completion. */
public final class CommandsImpl implements Commands {

    private record Registered(Command command, Owner owner) {}

    private final CoreContext context;
    private final Map<String, Registered> byLabel = new HashMap<>();
    private final Map<String, Registered> byName = new TreeMap<>();
    private @Nullable Console console;

    /**
     * Creates the registry.
     *
     * @param context owner state, loggers and main-thread checks
     */
    public CommandsImpl(CoreContext context) {
        this.context = context;
    }

    /**
     * Attaches the console returned by {@link #console()}.
     *
     * @param console the console
     */
    public void setConsole(Console console) {
        this.console = console;
    }

    @Override
    public Command register(Owner owner, Command command) {
        context.checkMainThread("Commands.register");
        context.requireActive(owner, "command /" + command.name());
        List<String> labels = new ArrayList<>();
        labels.add(command.name());
        labels.addAll(command.aliases());
        for (String label : labels) {
            Registered existing = byLabel.get(label);
            if (existing != null) {
                throw new IllegalArgumentException(
                        "Command /" + label + " is already registered by '" + existing.owner.id() + "'");
            }
        }
        Registered registered = new Registered(command, owner);
        for (String label : labels) {
            byLabel.put(label, registered);
        }
        byName.put(command.name(), registered);
        return command;
    }

    @Override
    public boolean unregister(String name) {
        Registered registered = byLabel.get(name.toLowerCase(Locale.ROOT));
        if (registered == null) {
            return false;
        }
        remove(registered);
        return true;
    }

    /**
     * Removes every command of an owner; called when it is disabled.
     *
     * @param owner the owner
     */
    public void unregisterAll(Owner owner) {
        for (Registered registered : new ArrayList<>(byName.values())) {
            if (registered.owner == owner) {
                remove(registered);
            }
        }
    }

    private void remove(Registered registered) {
        byName.remove(registered.command.name());
        byLabel.values().removeIf(r -> r == registered);
    }

    @Override
    public @Nullable Command get(String name) {
        Registered registered = byLabel.get(name.toLowerCase(Locale.ROOT));
        return registered == null ? null : registered.command;
    }

    @Override
    public Collection<Command> all() {
        List<Command> commands = new ArrayList<>();
        for (Registered registered : byName.values()) {
            commands.add(registered.command);
        }
        return List.copyOf(commands);
    }

    @Override
    public Console console() {
        if (console == null) {
            throw new IllegalStateException("Console is not attached");
        }
        return console;
    }

    @Override
    public boolean dispatch(String input, Consumer<String> output) {
        context.checkMainThread("Commands.dispatch");
        String line = strip(input);
        List<int[]> tokens = tokenize(line);
        if (tokens.isEmpty()) {
            return false;
        }
        String label = token(line, tokens, 0).toLowerCase(Locale.ROOT);
        Registered registered = byLabel.get(label);
        if (registered == null) {
            output.accept("Unknown command /" + label + ". Type /help for a list.");
            return false;
        }
        Command command = registered.command;
        int index = 1;
        while (!command.subcommands().isEmpty()) {
            if (index >= tokens.size()) {
                if (command.executor() != null) {
                    break;
                }
                output.accept("Usage: " + command.usage());
                return false;
            }
            Command sub = subcommand(command, token(line, tokens, index));
            if (sub == null) {
                output.accept("Unknown subcommand '" + token(line, tokens, index) + "'. Usage: " + command.usage());
                return false;
            }
            command = sub;
            index++;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (Argument<?> argument : command.arguments()) {
            if (index >= tokens.size()) {
                if (argument.isOptional()) {
                    break;
                }
                output.accept("Missing " + argument.usage() + ". Usage: " + command.usage());
                return false;
            }
            String text =
                    argument.type().isGreedy() ? line.substring(tokens.get(index)[0]) : token(line, tokens, index);
            try {
                values.put(argument.name(), argument.type().parse(text));
            } catch (CommandException e) {
                output.accept("Invalid " + argument.usage() + ": " + e.getMessage());
                return false;
            }
            index = argument.type().isGreedy() ? tokens.size() : index + 1;
        }
        if (index < tokens.size()) {
            output.accept("Too many arguments. Usage: " + command.usage());
            return false;
        }
        var executor = command.executor();
        if (executor == null) {
            output.accept("Usage: " + command.usage());
            return false;
        }
        try {
            executor.execute(new ContextImpl(command, line, values, output));
            return true;
        } catch (CommandException e) {
            output.accept(e.getMessage() == null ? "Command failed" : e.getMessage());
            return false;
        } catch (Throwable error) {
            context.loggerOf(registered.owner).error("Command /" + line + " failed", error);
            output.accept("Command failed with an internal error; see the log");
            return false;
        }
    }

    @Override
    public List<String> complete(String partial) {
        String line = strip(partial);
        List<int[]> tokens = tokenize(line);
        boolean newToken = line.isEmpty() || line.endsWith(" ");
        List<String> result = new ArrayList<>();
        if (tokens.isEmpty() || (tokens.size() == 1 && !newToken)) {
            String prefix = tokens.isEmpty() ? "" : token(line, tokens, 0).toLowerCase(Locale.ROOT);
            for (String label : new TreeMap<>(byLabel).keySet()) {
                if (label.startsWith(prefix)) {
                    result.add("/" + label);
                }
            }
            return result;
        }
        Registered registered = byLabel.get(token(line, tokens, 0).toLowerCase(Locale.ROOT));
        if (registered == null) {
            return result;
        }
        int complete = newToken ? tokens.size() : tokens.size() - 1;
        String current = newToken ? "" : token(line, tokens, tokens.size() - 1);
        String before = newToken ? line : line.substring(0, tokens.getLast()[0]);
        Command command = registered.command;
        int index = 1;
        while (!command.subcommands().isEmpty() && index < complete) {
            Command sub = subcommand(command, token(line, tokens, index));
            if (sub == null) {
                return result;
            }
            command = sub;
            index++;
        }
        List<String> options = new ArrayList<>();
        if (!command.subcommands().isEmpty()) {
            for (Command sub : command.subcommands()) {
                if (sub.name().startsWith(current)) {
                    options.add(sub.name());
                }
            }
        } else {
            int argumentIndex = complete - index;
            if (argumentIndex < command.arguments().size()) {
                options.addAll(command.arguments().get(argumentIndex).type().suggest(current));
            }
        }
        for (String option : options) {
            result.add("/" + before + option);
        }
        return result;
    }

    private static @Nullable Command subcommand(Command command, String label) {
        for (Command sub : command.subcommands()) {
            if (sub.matches(label)) {
                return sub;
            }
        }
        return null;
    }

    private static String strip(String input) {
        String line = input.stripLeading();
        return line.startsWith("/") ? line.substring(1) : line;
    }

    /** Start and end offsets of whitespace-separated tokens. */
    private static List<int[]> tokenize(String line) {
        List<int[]> tokens = new ArrayList<>();
        int i = 0;
        while (i < line.length()) {
            while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < line.length() && !Character.isWhitespace(line.charAt(i))) {
                i++;
            }
            if (i > start) {
                tokens.add(new int[] {start, i});
            }
        }
        return tokens;
    }

    private static String token(String line, List<int[]> tokens, int index) {
        int[] range = tokens.get(index);
        return line.substring(range[0], range[1]);
    }

    /** Parsed arguments of one command run. */
    private static final class ContextImpl implements CommandContext {
        private final Command command;
        private final String input;
        private final Map<String, Object> values;
        private final Consumer<String> output;

        ContextImpl(Command command, String input, Map<String, Object> values, Consumer<String> output) {
            this.command = command;
            this.input = input;
            this.values = values;
            this.output = output;
        }

        @Override
        public Command command() {
            return command;
        }

        @Override
        public String input() {
            return input;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T arg(String name) {
            Object value = values.get(name);
            if (value == null) {
                boolean declared = false;
                for (Argument<?> argument : command.arguments()) {
                    declared |= argument.name().equals(name);
                }
                throw new IllegalArgumentException(
                        declared
                                ? "Optional argument '" + name + "' was not given; check has(\"" + name + "\") first"
                                : "Command " + command.usage() + " has no argument '" + name + "'");
            }
            return (T) value;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> @Nullable T argOrNull(String name) {
            return (T) values.get(name);
        }

        @Override
        public boolean has(String name) {
            return values.containsKey(name);
        }

        @Override
        public void reply(String message) {
            output.accept(message);
        }
    }
}
