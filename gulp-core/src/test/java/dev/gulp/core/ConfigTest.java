package dev.gulp.core;

import static dev.gulp.core.Fixtures.started;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.data.Codec;
import dev.gulp.api.data.CodecException;
import dev.gulp.api.data.Config;
import dev.gulp.api.data.ConfigReloadEvent;
import dev.gulp.api.data.ConfigSection;
import dev.gulp.api.data.JsonNumber;
import dev.gulp.api.registry.Key;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import dev.gulp.platform.PlatformLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConfigTest {

    private static final String DEFAULTS = """
            # defaults shipped with the game
            difficulty: normal
            lives: 3
            player:
              speed: 7.5
              name: Hero
              items: [sword, shield]
            big: 5000000000
            rate: 0.25
            debug: false
            keys:
              - test:a
              - test:b
            """;

    private HeadlessRunner runner;

    @AfterEach
    void stop() {
        if (runner != null) {
            runner.stop();
        }
    }

    private Config start(TestGame game, String user) {
        runner = started(game, backend -> {
            backend.files().putAsset("test/config/game.yml", DEFAULTS.getBytes(UTF_8));
            if (user != null) {
                backend.files()
                        .writeUserData("config/game.yml", java.nio.ByteBuffer.wrap(user.getBytes(UTF_8)), noCallback());
            }
        });
        return game.config();
    }

    private static <T> dev.gulp.platform.PlatformCallback<T> noCallback() {
        return new dev.gulp.platform.PlatformCallback<>() {
            @Override
            public void success(T result) {}

            @Override
            public void failure(Throwable error) {}
        };
    }

    @Test
    void userValuesOverrideDefaultsDeeply() {
        TestGame game = new TestGame();
        List<String> seenInOnLoad = new ArrayList<>();
        game.onLoad = () -> seenInOnLoad.add(game.config().getString("difficulty", "?"));
        Config config = start(game, "difficulty: hard\nplayer:\n  speed: 9\n");

        assertThat(seenInOnLoad).containsExactly("hard");
        assertThat(config.name()).isEqualTo("game");
        assertThat(config.path()).isEmpty();
        assertThat(config.getString("difficulty", "?")).isEqualTo("hard");
        assertThat(config.getFloat("player.speed", 0f)).isEqualTo(9f);
        assertThat(config.getString("player.name", "?")).isEqualTo("Hero");
        assertThat(config.getInt("lives", 0)).isEqualTo(3);
        assertThat(config.getLong("big", 0)).isEqualTo(5_000_000_000L);
        assertThat(config.getDouble("rate", 0)).isEqualTo(0.25);
        assertThat(config.getBoolean("debug", true)).isFalse();
        assertThat(config.getList("player.items", Codec.STRING)).containsExactly("sword", "shield");
        assertThat(config.getList("keys", Codec.KEY)).containsExactly(Key.parse("test:a"), Key.parse("test:b"));
        assertThat(config.get("player.items", Codec.listOf(Codec.STRING))).hasSize(2);
        assertThat(config.keys()).contains("difficulty", "player");
        assertThat(config.contains("player.speed")).isTrue();
        assertThat(config.getValue("player.speed")).isEqualTo(JsonNumber.of(9));
    }

    @Test
    void gettersFallBackOnMissingOrWrongTypes() {
        Config config = start(new TestGame(), null);

        assertThat(config.getString("missing", "d")).isEqualTo("d");
        assertThat(config.getString("lives", "d")).isEqualTo("3");
        assertThat(config.getString("debug", "d")).isEqualTo("false");
        assertThat(config.getString("player", "d")).isEqualTo("d");
        assertThat(config.getInt("difficulty", 7)).isEqualTo(7);
        assertThat(config.getLong("missing", 8)).isEqualTo(8);
        assertThat(config.getFloat("missing", 1.5f)).isEqualTo(1.5f);
        assertThat(config.getDouble("missing", 2.5)).isEqualTo(2.5);
        assertThat(config.getBoolean("lives", true)).isTrue();
        assertThat(config.getList("missing", Codec.STRING)).isEmpty();
        assertThat(config.getSection("lives")).isNull();
        assertThat(config.getValue("player.speed.deeper")).isNull();
        assertThat(config.get("missing", Codec.INT)).isNull();
        assertThat(config.get("missing", Codec.INT, 4)).isEqualTo(4);
        assertThatThrownBy(() -> config.getList("player.items", Codec.INT))
                .isInstanceOf(CodecException.class)
                .hasMessageContaining("player.items[0]");
        assertThatThrownBy(() -> config.get("difficulty", Codec.INT))
                .isInstanceOf(CodecException.class)
                .hasMessageContaining("difficulty");
    }

    @Test
    void sectionsAreViewsOfTheConfig() {
        Config config = start(new TestGame(), null);
        ConfigSection player = config.getSection("player");

        assertThat(player).isNotNull();
        assertThat(player.path()).isEqualTo("player");
        assertThat(player.keys()).containsExactlyInAnyOrder("speed", "name", "items");
        assertThat(player.getFloat("speed", 0f)).isEqualTo(7.5f);
        assertThat(player.getSection("missing")).isNull();

        player.set("speed", 12);
        assertThat(config.getInt("player.speed", 0)).isEqualTo(12);
    }

    @Test
    void setSaveAndReload() {
        TestGame game = new TestGame();
        Config config = start(game, null);
        List<String> reloaded = new ArrayList<>();
        game.on(ConfigReloadEvent.class, e -> reloaded.add(e.config().name()));

        config.set("difficulty", "hard");
        config.set("audio.volume", 0.5);
        config.set("tags", List.of("a", "b"));
        config.set("map", Map.of("k", 1));
        config.set("lives", null);
        config.set("owner", Key.parse("test:me"), Codec.KEY);
        List<String> saved = new ArrayList<>();
        config.save().thenSync(nothing -> saved.add("saved"));
        runner.step(1);

        assertThat(saved).containsExactly("saved");
        String file = new String(runner.backend().files().userData("config/game.yml"), UTF_8);
        assertThat(file)
                .contains("difficulty: hard\n")
                .contains("audio:\n  volume: 0.5\n")
                .doesNotContain("lives");
        assertThat(config.get("owner", Codec.KEY)).isEqualTo(Key.parse("test:me"));

        config.set("difficulty", "easy");
        List<String> done = new ArrayList<>();
        config.reload().thenSync(nothing -> done.add("reloaded"));
        runner.step(1);

        assertThat(done).containsExactly("reloaded");
        assertThat(reloaded).containsExactly("game");
        assertThat(config.getString("difficulty", "?")).isEqualTo("hard");
        assertThat(config.getInt("lives", 0))
                .as("default comes back after removal")
                .isEqualTo(3);
        assertThat(config.toString()).contains("difficulty");
    }

    @Test
    void brokenFilesAreLoggedAndIgnored() {
        Config config = start(new TestGame(), "difficulty: [unclosed\n");

        assertThat(config.getString("difficulty", "?")).isEqualTo("normal");
        assertThat(runner.backend().log().messages(PlatformLog.ERROR))
                .anyMatch(m -> m.startsWith("Cannot read config config/game.yml"));
    }

    @Test
    void nonMapFilesAreRejected() {
        Config config = start(new TestGame(), "- just\n- a list\n");

        assertThat(config.getString("difficulty", "?")).isEqualTo("normal");
        assertThat(runner.backend().log().messages(PlatformLog.ERROR)).anyMatch(m -> m.contains("must be a map"));
    }

    @Test
    void missingFilesGiveAnEmptyConfig() {
        TestGame game = new TestGame();
        runner = started(game);

        assertThat(game.config().keys()).isEmpty();
        assertThat(game.config().getInt("anything", 5)).isEqualTo(5);
    }
}
