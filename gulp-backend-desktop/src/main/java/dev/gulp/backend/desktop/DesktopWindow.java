package dev.gulp.backend.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import dev.gulp.platform.CursorMode;
import dev.gulp.platform.DecodedImage;
import dev.gulp.platform.PlatformWindow;
import dev.gulp.platform.WindowListener;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWVidMode;

/**
 * GLFW window. Logical size is the framebuffer size divided by the content scale, which gives points on macOS and
 * scaled pixels on Windows and Linux.
 *
 * <pre>{@code
 * gl.viewport(0, 0, window.framebufferWidth(), window.framebufferHeight());
 * }</pre>
 */
public final class DesktopWindow implements PlatformWindow {

    private final long handle;
    private final int[] intA = new int[1];
    private final int[] intB = new int[1];
    private final float[] floatA = new float[1];
    private final float[] floatB = new float[1];
    private int framebufferWidth;
    private int framebufferHeight;
    private float contentScale;
    private boolean fullscreen;
    private int windowedX;
    private int windowedY;
    private int windowedWidth;
    private int windowedHeight;
    private @Nullable WindowListener listener;

    DesktopWindow(long handle, boolean fullscreen) {
        this.handle = handle;
        this.fullscreen = fullscreen;
        refreshFramebuffer();
        refreshContentScale();

        glfwSetFramebufferSizeCallback(handle, (window, width, height) -> {
            framebufferWidth = width;
            framebufferHeight = height;
            notifyResized();
        });
        glfwSetWindowContentScaleCallback(handle, (window, x, y) -> {
            contentScale = Math.max(x, 0.1f);
            if (listener != null) {
                listener.contentScaleChanged(contentScale);
            }
            notifyResized();
        });
        glfwSetWindowFocusCallback(handle, (window, focused) -> {
            if (listener != null) {
                listener.focusChanged(focused);
            }
        });
        glfwSetWindowCloseCallback(handle, window -> {
            if (listener != null) {
                listener.closeRequested();
            }
        });
    }

    long handle() {
        return handle;
    }

    private void refreshFramebuffer() {
        glfwGetFramebufferSize(handle, intA, intB);
        framebufferWidth = intA[0];
        framebufferHeight = intB[0];
    }

    private void refreshContentScale() {
        glfwGetWindowContentScale(handle, floatA, floatB);
        contentScale = Math.max(floatA[0], 0.1f);
    }

    private void notifyResized() {
        if (listener != null) {
            listener.resized(width(), height(), framebufferWidth, framebufferHeight);
        }
    }

    @Override
    public int width() {
        return Math.round(framebufferWidth / contentScale);
    }

    @Override
    public int height() {
        return Math.round(framebufferHeight / contentScale);
    }

    @Override
    public int framebufferWidth() {
        return framebufferWidth;
    }

    @Override
    public int framebufferHeight() {
        return framebufferHeight;
    }

    @Override
    public float contentScale() {
        return contentScale;
    }

    @Override
    public void setTitle(String title) {
        glfwSetWindowTitle(handle, title);
    }

    @Override
    public void setIcon(DecodedImage icon) {
        int platform = glfwGetPlatform();
        if (platform == GLFW_PLATFORM_COCOA || platform == GLFW_PLATFORM_WAYLAND) {
            return; // these platforms have no per-window icons
        }
        try (GLFWImage.Buffer images = GLFWImage.malloc(1)) {
            images.get(0).set(icon.width(), icon.height(), icon.pixels());
            glfwSetWindowIcon(handle, images);
        }
    }

    @Override
    public void setSize(int width, int height) {
        if (fullscreen) {
            return;
        }
        glfwGetWindowSize(handle, intA, intB);
        float screenPerPoint = width() > 0 ? intA[0] / (float) width() : 1f;
        glfwSetWindowSize(handle, Math.round(width * screenPerPoint), Math.round(height * screenPerPoint));
    }

    @Override
    public boolean isFullscreen() {
        return fullscreen;
    }

    @Override
    public void setFullscreen(boolean fullscreen) {
        if (fullscreen == this.fullscreen) {
            return;
        }
        if (fullscreen) {
            glfwGetWindowPos(handle, intA, intB);
            windowedX = intA[0];
            windowedY = intB[0];
            glfwGetWindowSize(handle, intA, intB);
            windowedWidth = intA[0];
            windowedHeight = intB[0];
            long monitor = glfwGetPrimaryMonitor();
            GLFWVidMode mode = glfwGetVideoMode(monitor);
            if (mode == null) {
                return;
            }
            glfwSetWindowMonitor(handle, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
        } else {
            int width = windowedWidth > 0 ? windowedWidth : 1280;
            int height = windowedHeight > 0 ? windowedHeight : 720;
            glfwSetWindowMonitor(handle, NULL, windowedX, windowedY, width, height, GLFW_DONT_CARE);
        }
        this.fullscreen = fullscreen;
    }

    @Override
    public void setVsync(boolean vsync) {
        glfwSwapInterval(vsync ? 1 : 0);
    }

    @Override
    public void setCursorMode(CursorMode mode) {
        int value =
                switch (mode) {
                    case NORMAL -> GLFW_CURSOR_NORMAL;
                    case HIDDEN -> GLFW_CURSOR_HIDDEN;
                    case CAPTURED -> GLFW_CURSOR_DISABLED;
                };
        glfwSetInputMode(handle, GLFW_CURSOR, value);
    }

    @Override
    public boolean isFocused() {
        return glfwGetWindowAttrib(handle, GLFW_FOCUSED) == GLFW_TRUE;
    }

    @Override
    public boolean shouldClose() {
        return glfwWindowShouldClose(handle);
    }

    @Override
    public void requestClose() {
        glfwSetWindowShouldClose(handle, true);
    }

    @Override
    public void setListener(@Nullable WindowListener listener) {
        this.listener = listener;
    }
}
