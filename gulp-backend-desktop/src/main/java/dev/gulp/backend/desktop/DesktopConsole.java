package dev.gulp.backend.desktop;

import dev.gulp.platform.PlatformConsole;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Reads commands typed in the terminal on a daemon thread and delivers them on the main thread before a frame.
 */
public final class DesktopConsole implements PlatformConsole {

    private final ArrayDeque<String> typed = new ArrayDeque<>();
    private @Nullable Consumer<String> listener;
    private boolean readerStarted;

    DesktopConsole() {}

    @Override
    public void setInputListener(@Nullable Consumer<String> listener) {
        this.listener = listener;
        if (listener != null && !readerStarted) {
            readerStarted = true;
            Thread.ofPlatform().daemon().name("gulp-terminal").start(this::readLines);
        }
    }

    private void readLines() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                synchronized (typed) {
                    typed.add(line);
                }
            }
        } catch (IOException e) {
            // Terminal closed; stop reading.
        }
    }

    void deliverTyped() {
        while (true) {
            String line;
            synchronized (typed) {
                line = typed.poll();
            }
            if (line == null) {
                return;
            }
            Consumer<String> current = listener;
            if (current != null) {
                current.accept(line);
            }
        }
    }

    @Override
    public void print(String line) {
        System.out.println(line);
    }
}
