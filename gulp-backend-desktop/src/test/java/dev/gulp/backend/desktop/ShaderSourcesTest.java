package dev.gulp.backend.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShaderSourcesTest {

    @Test
    void rewritesVersionAndDropsPrecision() {
        String es = """
                #version 300 es
                precision mediump float;
                in highp vec2 v_uv;
                out lowp vec4 fragColor;
                void main() { fragColor = vec4(v_uv, 0.0, 1.0); }
                """;

        assertThat(ShaderSources.toDesktop(es)).isEqualTo("""
                #version 330 core
                in vec2 v_uv;
                out vec4 fragColor;
                void main() { fragColor = vec4(v_uv, 0.0, 1.0); }
                """);
    }

    @Test
    void leavesDesktopSourcesAlone() {
        String desktop = "#version 330 core\nvoid main() {}\n";

        assertThat(ShaderSources.toDesktop(desktop)).isEqualTo(desktop);
    }
}
