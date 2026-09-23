package dev.gulp.backend.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DesktopInfoTest {

    @Test
    void normalizesArchitectureNames() {
        assertThat(DesktopInfo.normalizeArchitecture("amd64")).isEqualTo("x64");
        assertThat(DesktopInfo.normalizeArchitecture("x86_64")).isEqualTo("x64");
        assertThat(DesktopInfo.normalizeArchitecture("aarch64")).isEqualTo("arm64");
        assertThat(DesktopInfo.normalizeArchitecture("riscv64")).isEqualTo("riscv64");
    }
}
