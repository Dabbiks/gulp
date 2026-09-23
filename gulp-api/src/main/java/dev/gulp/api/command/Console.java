package dev.gulp.api.command;

import java.util.List;

/**
 * The developer console: runs commands typed by the developer and keeps a history. Available in development builds
 * (or when enabled in {@code GameSettings}); on desktop it also reads commands from the terminal. The in-game overlay
 * under {@code ~} arrives in stage 9.
 *
 * <pre>{@code
 * Console console = commands().console();
 * if (console.isAvailable()) console.submit("/timescale 0.5");
 * }</pre>
 */
public interface Console {

    /**
     * Returns whether the console accepts input.
     *
     * @return {@code true} if enabled for this build
     */
    boolean isAvailable();

    /**
     * Runs a line as a command and adds it to the history. A leading slash is optional. Ignored if the console is not
     * available.
     *
     * @param line the input line
     */
    void submit(String line);

    /**
     * Prints a line of output.
     *
     * @param line the text
     */
    void print(String line);

    /**
     * Returns previous input lines, oldest first.
     *
     * @return an unmodifiable copy, at most 100 lines
     */
    List<String> history();

    /**
     * Returns the output lines printed so far, oldest first.
     *
     * @return an unmodifiable copy, at most 500 lines
     */
    List<String> output();

    /**
     * Suggests completions for a partial input line.
     *
     * @param partial the line typed so far
     * @return full lines that complete it
     */
    List<String> complete(String partial);
}
