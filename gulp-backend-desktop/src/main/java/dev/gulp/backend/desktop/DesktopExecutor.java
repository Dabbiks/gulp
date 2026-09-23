package dev.gulp.backend.desktop;

import dev.gulp.platform.PlatformExecutor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Runs each task on its own virtual thread.
 *
 * <pre>{@code
 * backend.executor().execute(() -> decodeInBackground());
 * }</pre>
 */
public final class DesktopExecutor implements PlatformExecutor {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    DesktopExecutor() {}

    @Override
    public void execute(Runnable task) {
        executor.execute(task);
    }

    @Override
    public boolean isConcurrent() {
        return true;
    }

    @Override
    public void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
