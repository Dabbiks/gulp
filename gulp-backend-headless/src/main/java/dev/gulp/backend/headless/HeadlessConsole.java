package dev.gulp.backend.headless;

import dev.gulp.platform.PlatformConsole;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Terminal simulated in memory: typed lines are delivered before the next frame; printed lines are recorded.
 *
 * <pre>{@code
 * backend.console().type("/tps");
 * runner.step(1);
 * assertThat(backend.console().printed()).anyMatch(line -> line.startsWith("TPS"));
 * }</pre>
 */
public final class HeadlessConsole implements PlatformConsole {

    private final ArrayDeque<String> typed = new ArrayDeque<>();
    private final List<String> printed = new ArrayList<>();
    private @Nullable Consumer<String> listener;

    HeadlessConsole() {}

    /**
     * Types a line into the terminal.
     *
     * @param line the input
     */
    public void type(String line) {
        typed.add(line);
    }

    /**
     * Returns everything printed so far.
     *
     * @return a copy, oldest first
     */
    public synchronized List<String> printed() {
        return List.copyOf(printed);
    }

    void deliverTyped() {
        while (!typed.isEmpty()) {
            String line = typed.removeFirst();
            if (listener != null) {
                listener.accept(line);
            }
        }
    }

    @Override
    public void setInputListener(@Nullable Consumer<String> listener) {
        this.listener = listener;
    }

    @Override
    public synchronized void print(String line) {
        printed.add(line);
    }
}
