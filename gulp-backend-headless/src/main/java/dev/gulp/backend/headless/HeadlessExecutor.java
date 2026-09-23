package dev.gulp.backend.headless;

import dev.gulp.platform.PlatformExecutor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Runs tasks synchronously on the calling thread, which keeps tests deterministic.
 *
 * <pre>{@code
 * backend.executor().execute(() -> generateChunk()); // runs before execute returns
 * }</pre>
 */
public final class HeadlessExecutor implements PlatformExecutor {

    private boolean shutdown;
    private int executedCount;

    HeadlessExecutor() {}

    /**
     * Returns how many tasks ran.
     *
     * @return the executed task count
     */
    public int executedCount() {
        return executedCount;
    }

    @Override
    public void execute(Runnable task) {
        if (shutdown) {
            throw new RejectedExecutionException("Executor is shut down");
        }
        executedCount++;
        task.run();
    }

    @Override
    public boolean isConcurrent() {
        return false;
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }
}
