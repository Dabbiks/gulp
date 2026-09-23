package dev.gulp.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.graphics.Color;
import org.junit.jupiter.api.Test;

class GameSettingsTest {

    @Test
    void hasDocumentedDefaults() {
        GameSettings settings = new GameSettings();

        assertThat(settings.title()).isEqualTo("Gulp");
        assertThat(settings.windowWidth()).isEqualTo(1280);
        assertThat(settings.windowHeight()).isEqualTo(720);
        assertThat(settings.isResizable()).isTrue();
        assertThat(settings.isFullscreen()).isFalse();
        assertThat(settings.isVsync()).isTrue();
        assertThat(settings.targetFps()).isZero();
        assertThat(settings.ticksPerSecond()).isEqualTo(60);
        assertThat(settings.clearColor()).isEqualTo(Color.BLACK);
    }

    @Test
    void settersAreFluent() {
        Color sky = Color.rgb(0x1d2b53);

        GameSettings settings = new GameSettings()
                .title("Coin Hunter")
                .windowSize(960, 540)
                .resizable(false)
                .fullscreen(true)
                .vsync(false)
                .targetFps(144)
                .ticksPerSecond(20)
                .clearColor(sky);

        assertThat(settings.title()).isEqualTo("Coin Hunter");
        assertThat(settings.windowWidth()).isEqualTo(960);
        assertThat(settings.windowHeight()).isEqualTo(540);
        assertThat(settings.isResizable()).isFalse();
        assertThat(settings.isFullscreen()).isTrue();
        assertThat(settings.isVsync()).isFalse();
        assertThat(settings.targetFps()).isEqualTo(144);
        assertThat(settings.ticksPerSecond()).isEqualTo(20);
        assertThat(settings.clearColor()).isEqualTo(sky);
    }

    @Test
    void rejectsInvalidValues() {
        GameSettings settings = new GameSettings();

        assertThatThrownBy(() -> settings.windowSize(0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings.windowSize(10, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings.targetFps(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings.ticksPerSecond(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> settings.ticksPerSecond(1001)).isInstanceOf(IllegalArgumentException.class);
    }
}
