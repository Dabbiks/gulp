package dev.gulp.backend.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.lwjgl.glfw.GLFW.*;

import org.junit.jupiter.api.Test;

class DesktopInputTest {

    @Test
    void glfwKeysMapToHidUsageIds() {
        assertThat(DesktopInput.hid(GLFW_KEY_A)).isEqualTo(4);
        assertThat(DesktopInput.hid(GLFW_KEY_Z)).isEqualTo(29);
        assertThat(DesktopInput.hid(GLFW_KEY_1)).isEqualTo(30);
        assertThat(DesktopInput.hid(GLFW_KEY_0)).isEqualTo(39);
        assertThat(DesktopInput.hid(GLFW_KEY_SPACE)).isEqualTo(44);
        assertThat(DesktopInput.hid(GLFW_KEY_ESCAPE)).isEqualTo(41);
        assertThat(DesktopInput.hid(GLFW_KEY_F12)).isEqualTo(69);
        assertThat(DesktopInput.hid(GLFW_KEY_UP)).isEqualTo(82);
        assertThat(DesktopInput.hid(GLFW_KEY_KP_0)).isEqualTo(98);
        assertThat(DesktopInput.hid(GLFW_KEY_RIGHT_SUPER)).isEqualTo(231);
        assertThat(DesktopInput.hid(GLFW_KEY_UNKNOWN)).isZero();
        assertThat(DesktopInput.hid(GLFW_KEY_LAST + 1)).isZero();
    }
}
