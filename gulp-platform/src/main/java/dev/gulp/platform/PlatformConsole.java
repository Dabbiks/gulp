package dev.gulp.platform;

import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Text console outside the game window: the terminal on desktop. Lines typed there are delivered to the listener on
 * the main thread, before a frame. Platforms without a terminal (the web) drop input and print to their log.
 *
 * <pre>{@code
 * backend.console().setInputListener(line -> commands.dispatch(line, backend.console()::print));
 * }</pre>
 */
public interface PlatformConsole {

    /**
     * Sets the receiver of typed lines.
     *
     * @param listener the receiver, or {@code null} to ignore input
     */
    void setInputListener(@Nullable Consumer<String> listener);

    /**
     * Prints a line of command output. Thread-safe.
     *
     * @param line the text
     */
    void print(String line);
}
