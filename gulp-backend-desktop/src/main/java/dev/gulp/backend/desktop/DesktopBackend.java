package dev.gulp.backend.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import dev.gulp.core.GeneratedModules;
import dev.gulp.core.MainQueue;
import dev.gulp.platform.PlatformAudio;
import dev.gulp.platform.PlatformBackend;
import dev.gulp.platform.PlatformDecoders;
import dev.gulp.platform.PlatformInput;
import dev.gulp.platform.PlatformNet;
import dev.gulp.platform.WindowConfig;
import java.nio.file.Path;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

/**
 * Desktop platform on LWJGL 3: GLFW window with an OpenGL 3.3 core context.
 *
 * <p>Implemented: loop, window, graphics, files, log, terminal console, executor, system information and generated code.
 * Input and audio arrive in stage 5, decoders in stage 2, network in stage 10; until then their accessors throw
 * {@link UnsupportedOperationException}.
 *
 * <pre>{@code
 * DesktopBackend backend = DesktopBackend.create("demo", new WindowConfig("Demo", 960, 540, true, false, true));
 * }</pre>
 */
public final class DesktopBackend implements PlatformBackend {

    /** System property: close the window after this many frames (used by CI smoke tests). */
    public static final String EXIT_AFTER_FRAMES_PROPERTY = "gulp.desktop.exitAfterFrames";

    private final GLFWErrorCallback errorCallback;
    private final DesktopWindow window;
    private final DesktopGl gl = new DesktopGl();
    private final DesktopLoop loop;
    private final DesktopExecutor executor = new DesktopExecutor();
    private final DesktopInfo info;
    private final MainQueue mainQueue = new MainQueue();
    private final DesktopLog log;
    private final DesktopConsole console = new DesktopConsole();
    private final DesktopFiles files;
    private boolean disposed;

    private DesktopBackend(GLFWErrorCallback errorCallback, DesktopWindow window, Path dataDirectory, DesktopLog log) {
        this.errorCallback = errorCallback;
        this.window = window;
        this.log = log;
        this.files = new DesktopFiles(DesktopFiles.defaultAssetsDirectory(), dataDirectory, executor, mainQueue);
        this.loop = new DesktopLoop(window, Long.getLong(EXIT_AFTER_FRAMES_PROPERTY, 0L), this::beforeFrame);
        this.info = new DesktopInfo(gl);
    }

    /**
     * Initialises GLFW, opens the window and makes its OpenGL 3.3 core context current on this thread.
     *
     * @param appId the game id, used for the user data directory
     * @param config window parameters
     * @return the backend
     * @throws IllegalStateException if GLFW cannot start or OpenGL 3.3 core is unavailable
     */
    public static DesktopBackend create(String appId, WindowConfig config) {
        Path dataDirectory = DesktopFiles.defaultDataDirectory(appId);
        DesktopLog log = new DesktopLog(dataDirectory.resolve("logs"));
        GLFWErrorCallback errorCallback =
                GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            errorCallback.free();
            log.close();
            throw new IllegalStateException("Unable to initialise GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, config.resizable() ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_SCALE_TO_MONITOR, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        long monitor = config.fullscreen() ? glfwGetPrimaryMonitor() : NULL;
        int width = config.width();
        int height = config.height();
        GLFWVidMode mode = monitor != NULL ? glfwGetVideoMode(monitor) : null;
        if (mode != null) {
            width = mode.width();
            height = mode.height();
        }

        long handle = glfwCreateWindow(width, height, config.title(), monitor, NULL);
        if (handle == NULL) {
            glfwTerminate();
            errorCallback.free();
            log.close();
            throw new IllegalStateException("Could not create a window with an OpenGL 3.3 core context."
                    + " Update the graphics driver; on virtual machines enable 3D acceleration.");
        }
        if (monitor == NULL) {
            centerOnPrimaryMonitor(handle);
        }

        glfwMakeContextCurrent(handle);
        GL.createCapabilities();
        glfwSwapInterval(config.vsync() ? 1 : 0);

        DesktopWindow window = new DesktopWindow(handle, config.fullscreen());
        glfwShowWindow(handle);
        return new DesktopBackend(errorCallback, window, dataDirectory, log);
    }

    private static void centerOnPrimaryMonitor(long handle) {
        GLFWVidMode mode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (mode == null || glfwGetPlatform() == GLFW_PLATFORM_WAYLAND) {
            return;
        }
        int[] w = new int[1];
        int[] h = new int[1];
        glfwGetWindowSize(handle, w, h);
        glfwSetWindowPos(handle, (mode.width() - w[0]) / 2, (mode.height() - h[0]) / 2);
    }

    @Override
    public String name() {
        return DesktopGameLauncher.NAME;
    }

    @Override
    public DesktopLoop loop() {
        return loop;
    }

    @Override
    public DesktopGl gl() {
        return gl;
    }

    @Override
    public DesktopWindow window() {
        return window;
    }

    @Override
    public PlatformInput input() {
        throw notYet("PlatformInput", 5);
    }

    @Override
    public PlatformAudio audio() {
        throw notYet("PlatformAudio", 5);
    }

    @Override
    public DesktopFiles files() {
        return files;
    }

    @Override
    public PlatformDecoders decoders() {
        throw notYet("PlatformDecoders", 2);
    }

    @Override
    public DesktopExecutor executor() {
        return executor;
    }

    @Override
    public PlatformNet net() {
        throw notYet("PlatformNet", 10);
    }

    @Override
    public DesktopInfo info() {
        return info;
    }

    @Override
    public GeneratedModules modules() {
        return GeneratedModules.shared();
    }

    @Override
    public DesktopLog log() {
        return log;
    }

    @Override
    public DesktopConsole console() {
        return console;
    }

    private void beforeFrame() {
        mainQueue.drain(
                error -> log.write(DesktopLog.ERROR, "gulp", "Unhandled exception in a platform callback", error));
        console.deliverTyped();
    }

    private static UnsupportedOperationException notYet(String service, int stage) {
        return new UnsupportedOperationException(
                service + " is not implemented on desktop yet (roadmap stage " + stage + ")");
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        executor.shutdown();
        long handle = window.handle();
        Callbacks.glfwFreeCallbacks(handle);
        glfwDestroyWindow(handle);
        glfwTerminate();
        errorCallback.free();
        log.close();
    }
}
