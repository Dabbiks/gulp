package dev.gulp.core;

import java.util.ArrayList;
import java.util.List;

/** Work posted from any thread and run on the main thread at the start of the next frame. */
public final class MainQueue {

    private final List<Runnable> queued = new ArrayList<>();
    private final List<Runnable> running = new ArrayList<>();

    /** Creates an empty queue. */
    public MainQueue() {}

    /**
     * Posts work. Thread-safe.
     *
     * @param work runs on the main thread before the next frame
     */
    public void post(Runnable work) {
        synchronized (queued) {
            queued.add(work);
        }
    }

    /**
     * Runs everything posted before this call; work posted while draining waits for the next drain.
     *
     * @param onError receives exceptions thrown by the work
     */
    public void drain(java.util.function.Consumer<Throwable> onError) {
        synchronized (queued) {
            if (queued.isEmpty()) {
                return;
            }
            running.addAll(queued);
            queued.clear();
        }
        try {
            for (Runnable work : running) {
                try {
                    work.run();
                } catch (Throwable error) {
                    onError.accept(error);
                }
            }
        } finally {
            running.clear();
        }
    }
}
