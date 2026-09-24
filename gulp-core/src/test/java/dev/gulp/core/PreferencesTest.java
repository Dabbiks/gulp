package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.data.Preferences;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import dev.gulp.core.data.PreferencesImpl;
import dev.gulp.platform.PlatformCallback;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PreferencesTest {

    private @Nullable HeadlessRunner runner;

    @AfterEach
    void tearDown() {
        if (runner != null) {
            runner.stop();
        }
    }

    @Test
    void valuesAreTypedAndSavedShortlyAfterAChange() {
        TestGame game = new TestGame();
        runner = Fixtures.started(game);
        Preferences preferences = game.preferences();
        preferences.set("name", "Ala").set("level", 3).set("volume", 0.6f).set("fullscreen", true);
        assertThat(preferences.getString("name", "")).isEqualTo("Ala");
        assertThat(preferences.getInt("level", 0)).isEqualTo(3);
        assertThat(preferences.getFloat("volume", 0f)).isEqualTo(0.6f);
        assertThat(preferences.getBoolean("fullscreen", false)).isTrue();
        assertThat(preferences.getInt("name", 7)).isEqualTo(7);
        assertThat(preferences.getFloat("name", 7f)).isEqualTo(7f);
        assertThat(preferences.getBoolean("name", false)).isFalse();
        assertThat(preferences.getString("level", "x")).isEqualTo("x");
        assertThat(preferences.has("level")).isTrue();
        assertThat(preferences.keys()).containsExactly("name", "level", "volume", "fullscreen");
        preferences.remove("level").remove("missing");
        assertThat(preferences.has("level")).isFalse();
        assertThatThrownBy(() -> preferences.set(" ", 1)).isInstanceOf(IllegalArgumentException.class);

        assertThat(((PreferencesImpl) preferences).isDirty()).isTrue();
        runner.step(40);
        assertThat(((PreferencesImpl) preferences).isDirty()).isFalse();
        String saved = new String(runner.backend().files().userData(PreferencesImpl.FILE), StandardCharsets.UTF_8);
        assertThat(saved).contains("\"volume\": 0.6", "\"name\": \"Ala\"");
        preferences.set("name", "Ola");
        preferences.save();
        runner.step(1);
        assertThat(new String(runner.backend().files().userData(PreferencesImpl.FILE), StandardCharsets.UTF_8))
                .contains("Ola");
    }

    @Test
    void anUnreadableFileIsIgnored() {
        TestGame game = new TestGame();
        runner = Fixtures.started(
                game,
                b -> b.files()
                        .writeUserData(
                                PreferencesImpl.FILE,
                                ByteBuffer.wrap("{broken".getBytes(StandardCharsets.UTF_8)),
                                new PlatformCallback<>() {
                                    @Override
                                    public void success(@Nullable Void value) {}

                                    @Override
                                    public void failure(Throwable error) {}
                                }));
        assertThat(game.preferences().keys()).isEmpty();
    }
}
