package dev.gulp.backend.desktop;

import static org.lwjgl.glfw.GLFW.glfwGetMonitorContentScale;
import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;
import static org.lwjgl.system.MemoryUtil.NULL;

import dev.gulp.platform.Gl;
import dev.gulp.platform.PlatformInfo;
import java.util.Locale;
import org.lwjgl.glfw.GLFWVidMode;

/**
 * System information from JVM properties, GLFW and the OpenGL driver.
 *
 * <pre>{@code
 * logger.info("Running on " + info.osName() + " / " + info.gpuDescription());
 * }</pre>
 */
public final class DesktopInfo implements PlatformInfo {

    /** System property turning development features off: {@code -Dgulp.development=false}. */
    public static final String DEVELOPMENT_PROPERTY = "gulp.development";

    private final Gl gl;

    DesktopInfo(Gl gl) {
        this.gl = gl;
    }

    @Override
    public String osName() {
        return System.getProperty("os.name", "unknown") + " " + System.getProperty("os.version", "");
    }

    @Override
    public String architecture() {
        return normalizeArchitecture(System.getProperty("os.arch", "unknown"));
    }

    static String normalizeArchitecture(String osArch) {
        return switch (osArch.toLowerCase(Locale.ROOT)) {
            case "amd64", "x86_64" -> "x64";
            case "aarch64", "arm64" -> "arm64";
            default -> osArch;
        };
    }

    @Override
    public boolean isWeb() {
        return false;
    }

    @Override
    public boolean isDevelopment() {
        return Boolean.parseBoolean(System.getProperty(DEVELOPMENT_PROPERTY, "true"));
    }

    @Override
    public String systemLocale() {
        return Locale.getDefault().toLanguageTag();
    }

    @Override
    public int screenWidth() {
        return screenSize(true);
    }

    @Override
    public int screenHeight() {
        return screenSize(false);
    }

    private static int screenSize(boolean width) {
        long monitor = glfwGetPrimaryMonitor();
        if (monitor == NULL) {
            return 0;
        }
        GLFWVidMode mode = glfwGetVideoMode(monitor);
        if (mode == null) {
            return 0;
        }
        float[] scaleX = new float[1];
        float[] scaleY = new float[1];
        glfwGetMonitorContentScale(monitor, scaleX, scaleY);
        return width
                ? Math.round(mode.width() / Math.max(scaleX[0], 0.1f))
                : Math.round(mode.height() / Math.max(scaleY[0], 0.1f));
    }

    @Override
    public String gpuDescription() {
        return gl.getString(Gl.RENDERER) + " / OpenGL " + gl.getString(Gl.VERSION);
    }
}
