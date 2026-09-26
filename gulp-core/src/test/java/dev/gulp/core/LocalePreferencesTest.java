package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.gulp.api.i18n.LocaleChangeEvent;
import dev.gulp.api.text.Text;
import dev.gulp.api.ui.Label;
import dev.gulp.core.data.PreferencesImpl;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Built-in settings kept in preferences, the translation fallback chain and text that follows the language. */
class LocalePreferencesTest extends UiFixture {

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private void start(String preferences) {
        startUi(settings -> {}, backend -> {
            backend.files()
                    .putAsset("test/lang/en_us.json", utf8("{\"hud.coins\": \"Coins: {0}\", \"only.en\": \"E\"}"));
            backend.files()
                    .putAsset(
                            "test/lang/pl_pl.json",
                            utf8("{\"hud.coins\": \"{0, plural, one {# moneta} few {# monety} many {# monet}"
                                    + " other {# monety}}\", \"only.pl\": \"P\"}"));
            backend.files().putAsset("test/lang/pl.json", utf8("{\"only.language\": \"L\"}"));
            backend.files()
                    .writeUserData(
                            PreferencesImpl.FILE,
                            java.nio.ByteBuffer.wrap(utf8(preferences)),
                            new dev.gulp.platform.PlatformCallback<>() {
                                @Override
                                public void success(@org.jspecify.annotations.Nullable Void value) {}

                                @Override
                                public void failure(Throwable error) {}
                            });
        });
    }

    @Test
    void savedSettingsApplyAtStart() {
        start("{\"gulp.locale\": \"pl_pl\", \"gulp.window.fullscreen\": true, \"gulp.window.vsync\": false,"
                + " \"gulp.ui.scale\": 1.5}");
        assertThat(game.translations().locale()).isEqualTo("pl_pl");
        assertThat(game.display().window().isFullscreen()).isTrue();
        assertThat(game.display().window().isVsync()).isFalse();
        assertThat(ui.scale()).isEqualTo(1.5f);

        game.display().window().setFullscreen(false);
        game.display().window().setVsync(true);
        assertThat(game.preferences().getBoolean("gulp.window.fullscreen", true))
                .isFalse();
        assertThat(game.preferences().getBoolean("gulp.window.vsync", false)).isTrue();
    }

    @Test
    void translationsFallBackAndTextFollowsTheLanguage() {
        start("{\"gulp.locale\": \"pl_pl\"}");
        assertThat(game.translations().tr("hud.coins", 1)).isEqualTo("1 moneta");
        assertThat(game.translations().tr("hud.coins", 3)).isEqualTo("3 monety");
        assertThat(game.translations().tr("hud.coins", 5)).isEqualTo("5 monet");
        assertThat(game.translations().tr("only.pl")).isEqualTo("P");
        assertThat(game.translations().tr("only.language")).as("language file").isEqualTo("L");
        assertThat(game.translations().tr("only.en")).as("default locale").isEqualTo("E");
        assertThat(game.translations().tr("missing.key")).as("the key itself").isEqualTo("missing.key");
        assertThat(game.translations().formatNumber(12_345.5)).isEqualTo("12 345,5");
        assertThat(game.translations().formatDate(0L)).isEqualTo("01.01.1970");
        assertThat(game.translations().format("{0} + {1}", 1, 2)).isEqualTo("1 + 2");

        Label label = new Label(Text.translatable("hud.coins", 5));
        open(dev.gulp.api.ui.Ui.center(label));
        float polish = label.width();
        assertThat(polish).isPositive();

        List<String> changes = new ArrayList<>();
        game.on(LocaleChangeEvent.class, e -> changes.add(e.locale()));
        game.translations().setLocale("en_us");
        step(2);
        assertThat(changes).containsExactly("en_us");
        assertThat(game.translations().tr("hud.coins", 5)).isEqualTo("Coins: 5");
        assertThat(game.preferences().getString("gulp.locale", "")).isEqualTo("en_us");
        assertThat(label.width()).as("the label measured the English text").isNotEqualTo(polish);
    }
}
