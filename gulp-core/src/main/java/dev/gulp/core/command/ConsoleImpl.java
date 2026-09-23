package dev.gulp.core.command;

import dev.gulp.api.command.Commands;
import dev.gulp.api.command.Console;
import dev.gulp.platform.PlatformConsole;
import java.util.ArrayDeque;
import java.util.List;

/** {@link Console} with bounded history and output, mirrored to the platform terminal. */
public final class ConsoleImpl implements Console {

    /** Maximum remembered input lines. */
    public static final int HISTORY = 100;

    /** Maximum remembered output lines. */
    public static final int OUTPUT = 500;

    private final Commands commands;
    private final PlatformConsole terminal;
    private final boolean available;
    private final ArrayDeque<String> history = new ArrayDeque<>();
    private final ArrayDeque<String> output = new ArrayDeque<>();

    /**
     * Creates the console.
     *
     * @param commands runs submitted lines
     * @param terminal receives printed lines
     * @param available whether input is accepted
     */
    public ConsoleImpl(Commands commands, PlatformConsole terminal, boolean available) {
        this.commands = commands;
        this.terminal = terminal;
        this.available = available;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void submit(String line) {
        if (!available || line.isBlank()) {
            return;
        }
        history.addLast(line);
        while (history.size() > HISTORY) {
            history.removeFirst();
        }
        print("> " + line);
        commands.dispatch(line, this::print);
    }

    @Override
    public void print(String line) {
        synchronized (output) {
            output.addLast(line);
            while (output.size() > OUTPUT) {
                output.removeFirst();
            }
        }
        terminal.print(line);
    }

    @Override
    public List<String> history() {
        return List.copyOf(history);
    }

    @Override
    public List<String> output() {
        synchronized (output) {
            return List.copyOf(output);
        }
    }

    @Override
    public List<String> complete(String partial) {
        return commands.complete(partial);
    }
}
