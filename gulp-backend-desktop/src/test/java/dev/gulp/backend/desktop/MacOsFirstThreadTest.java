package dev.gulp.backend.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MacOsFirstThreadTest {

    @Test
    void detectsMacOs() {
        assertThat(MacOsFirstThread.isMacOs("Mac OS X")).isTrue();
        assertThat(MacOsFirstThread.isMacOs("Darwin")).isTrue();
        assertThat(MacOsFirstThread.isMacOs("Windows 11")).isFalse();
        assertThat(MacOsFirstThread.isMacOs("Linux")).isFalse();
    }

    @Test
    void restartsMainClassWithFlagAndClassPath() {
        List<String> command = MacOsFirstThread.restartCommand(
                "/jdk/bin/java", List.of("-Xmx1g", "-Dfoo=bar"), "a.jar:b.jar", "com.example.Game --level 2");

        assertThat(command)
                .containsExactly(
                        "/jdk/bin/java",
                        "-XstartOnFirstThread",
                        "-Xmx1g",
                        "-Dfoo=bar",
                        "-cp",
                        "a.jar:b.jar",
                        "com.example.Game",
                        "--level",
                        "2");
    }

    @Test
    void restartsExecutableJar() {
        List<String> command = MacOsFirstThread.restartCommand("java", List.of(), "game.jar", "game.jar");

        assertThat(command).containsExactly("java", "-XstartOnFirstThread", "-jar", "game.jar");
    }
}
