package dev.gulp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The project generator on the real template of this checkout. */
class NewProjectTest {

    private static final Path GULP_HOME = Path.of("..").toAbsolutePath().normalize();

    @TempDir
    Path temp;

    @Test
    void theTemplateBecomesANamedProject() throws IOException {
        Path project = temp.resolve("coin-hunter");
        String main = NewProject.create(GULP_HOME, project, "org.example.coins", "Coin Hunter 2");
        assertThat(main).isEqualTo("org.example.coins.CoinHunter2");

        Path game = project.resolve("src/main/java/org/example/coins/CoinHunter2.java");
        assertThat(game).exists();
        assertThat(Files.readString(game))
                .contains("package org.example.coins;", "class CoinHunter2 extends Game", "return \"coinhunter2\";")
                .contains("settings.title(\"Coin Hunter 2\")")
                .doesNotContain("MyGame", "mygame");
        assertThat(Files.readString(project.resolve("src/main/java/org/example/coins/Walker.java")))
                .contains("@ComponentInfo(key = \"coinhunter2:walker\")", "CoinHunter2.moveLeft");
        assertThat(project.resolve("src/main/resources/assets/coinhunter2/lang/pl_pl.json"))
                .exists();
        assertThat(project.resolve("src/test/java/org/example/coins/CoinHunter2Test.java"))
                .exists();
        assertThat(Files.readString(project.resolve("build.gradle.kts")))
                .contains("mainClass = \"org.example.coins.CoinHunter2\"", "group = \"org.example\"");
        assertThat(Files.readString(project.resolve("settings.gradle.kts")))
                .contains("rootProject.name = \"coinhunter2\"");
        assertThat(project.resolve(".gitignore")).exists();
        assertThat(project.resolve("gradlew")).exists();
        assertThat(project.resolve("gradle/wrapper/gradle-wrapper.properties")).exists();
        assertThat(Files.readString(project.resolve("gradle.properties")))
                .contains("gulp.home=" + GULP_HOME.toString().replace('\\', '/'));

        assertThatThrownBy(() -> NewProject.create(GULP_HOME, project, "org.example.coins", "Again"))
                .hasMessageContaining("not empty");
    }

    @Test
    void namesAreChecked() {
        assertThat(NewProject.className("coin hunter")).isEqualTo("CoinHunter");
        assertThat(NewProject.className("Zażółć gra")).isEqualTo("ZaGra");
        assertThatThrownBy(() -> NewProject.className("2 fast")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NewProject.create(GULP_HOME, temp.resolve("x"), "Bad.Package", "Game"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NewProject.create(temp, temp.resolve("x"), "org.example", "Game"))
                .hasMessageContaining("No template");
    }
}
