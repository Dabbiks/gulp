package dev.gulp.backend.desktop;

import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;

import dev.gulp.platform.FrameHandler;
import dev.gulp.platform.PlatformLoop;

/**
 * {@code while} loop on the main thread: poll events, run a frame, swap buffers. Frame pacing comes from VSync.
 *
 * <pre>{@code
 * backend.loop().run(handler); // returns when the window closes
 * }</pre>
 */
public final class DesktopLoop implements PlatformLoop {

    private final DesktopWindow window;
    private final long exitAfterFrames;
    private boolean running;
    private boolean stopRequested;

    DesktopLoop(DesktopWindow window, long exitAfterFrames) {
        this.window = window;
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void run(FrameHandler handler) {
        if (running) {
            throw new IllegalStateException("Loop is already running");
        }
        running = true;
        stopRequested = false;
        long frames = 0;
        try {
            while (!stopRequested) {
                glfwPollEvents();
                boolean keepRunning = handler.frame(System.nanoTime());
                glfwSwapBuffers(window.handle());
                frames++;
                if (!keepRunning || (exitAfterFrames > 0 && frames >= exitAfterFrames)) {
                    break;
                }
            }
        } finally {
            running = false;
            handler.exit();
        }
    }

    @Override
    public void stop() {
        stopRequested = true;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
