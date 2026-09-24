package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.gulp.api.PauseMode;
import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.asset.AssetReloadEvent;
import dev.gulp.api.audio.Audio;
import dev.gulp.api.audio.AudioClip;
import dev.gulp.api.audio.Playback;
import dev.gulp.api.audio.Sound;
import dev.gulp.api.event.EventHandler;
import dev.gulp.api.event.Listener;
import dev.gulp.api.event.lifecycle.TickStartEvent;
import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Texture;
import dev.gulp.api.input.ActionPressEvent;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.input.KeyPressEvent;
import dev.gulp.api.input.Keys;
import dev.gulp.api.module.GameModule;
import dev.gulp.api.module.ModuleInfo;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Registries;
import dev.gulp.api.render.RenderLayerEvent;
import dev.gulp.api.service.ServicePriority;
import dev.gulp.api.text.Text;
import dev.gulp.api.text.TextStyle;
import dev.gulp.backend.headless.HeadlessBackend;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import dev.gulp.core.data.PreferencesImpl;
import dev.gulp.platform.PlatformCallback;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * One game that uses every finished stage together: modules with dependencies, services, config, commands and
 * scheduled tasks (1), drawing with the camera (2), assets and the startup group (3), text, translations, hot reload
 * and resource packs (4), and input actions, sounds and preferences (5). Checks the places where the stages meet:
 * pause, time scale, module disable, reloads and restart.
 */
class CrossStageTest {

    static final InputAction JUMP =
            InputAction.builder(Key.of("test", "jump")).bind(Keys.SPACE).build();
    static final AssetKey<AudioClip> BEEP = AssetKey.audio("test:sounds/beep");
    static final Sound JUMP_SOUND =
            Sound.builder(Key.of("test", "jump")).file(BEEP).minInterval(0f).build();
    static final Sound MENU_SOUND = Sound.builder(Key.of("test", "menu"))
            .file(BEEP)
            .bus(Audio.UI)
            .minInterval(0f)
            .build();

    /** Counts coins; other modules find it as a service. */
    interface Bank {
        int coins();

        void add(int amount);
    }

    /** Provides the bank, reads its start value from config, and pays interest with a scheduled task. */
    @ModuleInfo(id = "bank")
    public static final class BankModule extends GameModule implements Bank {
        int coins;
        int paydays;

        @Override
        public void onLoad() {
            registries().register(Registries.INPUT_ACTION, JUMP);
            registries().register(Registries.SOUND, JUMP_SOUND);
            registries().register(Registries.SOUND, MENU_SOUND);
        }

        @Override
        public void onEnable() {
            coins = config().getInt("start", 0);
            services().register(Bank.class, this, this, ServicePriority.NORMAL);
            every(10, () -> paydays++);
            command("coins", context -> context.reply("Coins: " + coins));
        }

        @Override
        public int coins() {
            return coins;
        }

        @Override
        public void add(int amount) {
            coins += amount;
        }
    }

    /** Jumps on the jump action through a generated listener, plays a sound and draws a translated HUD. */
    @ModuleInfo(id = "player", dependsOn = "bank")
    public static final class PlayerModule extends GameModule {
        int jumps;
        int keyEvents;
        int hudFrames;

        @Nullable Playback lastSound;

        @Override
        public void onEnable() {
            listen(new JumpListener(this));
            on(KeyPressEvent.class, e -> keyEvents++);
            on(RenderLayerEvent.class, e -> {
                if (e.layer().name().equals("ui")) {
                    Texture logo = assets().get(AssetKey.texture("test:textures/logo"));
                    e.draw()
                            .color(Color.WHITE)
                            .image(logo.region(), 0, 0, 16, 16)
                            .text(
                                    Text.translatable(
                                            "hud.coins",
                                            services().getOrThrow(Bank.class).coins()),
                                    20,
                                    0,
                                    TextStyle.of(12));
                    hudFrames++;
                }
            });
        }
    }

    /** Generated dispatch for a stage 5 event. */
    public static final class JumpListener implements Listener {
        private final PlayerModule player;

        JumpListener(PlayerModule player) {
            this.player = player;
        }

        @EventHandler
        void onAction(ActionPressEvent event) {
            if (event.action().equals(JUMP)) {
                player.jumps++;
                player.services().getOrThrow(Bank.class).add(1);
                player.lastSound = player.audio().play(JUMP_SOUND);
            }
        }
    }

    private @Nullable HeadlessRunner runner;
    private final BankModule bank = new BankModule();
    private final PlayerModule player = new PlayerModule();

    @AfterEach
    void tearDown() {
        if (runner != null) {
            runner.stop();
        }
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static void files(HeadlessBackend backend) {
        backend.files().useClasspathAssets(true);
        backend.files().putAsset("test/sounds/beep.wav", AudioTest.wav(0.2f));
        backend.files().putAsset("test/textures/logo.png", utf8("png"));
        backend.files().putAsset("test/config/bank.yml", utf8("start: 5\n"));
        backend.files().putAsset("test/lang/en_us.json", utf8("{\"hud.coins\": \"Coins: {0}\"}"));
        backend.files().putResourcePack("loud", "Louder beep", Map.of("test/sounds/beep.wav", AudioTest.wav(0.4f)));
    }

    private TestGame start(java.util.function.Consumer<HeadlessBackend> extra) {
        TestGame game = new TestGame().modules(bank, player);
        game.onLoad = () -> game.assets().startup().add(AssetKey.texture("test:textures/logo"));
        HeadlessRunner r = HeadlessRunner.start(game, b -> {
            files(b);
            extra.accept(b);
        });
        runner = r;
        for (int i = 0; i < 20 && !r.engine().isRunning(); i++) {
            r.step(1);
        }
        assertThat(r.engine().isRunning()).isTrue();
        return game;
    }

    private void tapSpace() {
        runner.backend().input().inject(l -> l.keyDown(Keys.SPACE.code(), 57, 0, false));
        runner.backend().input().inject(l -> l.keyUp(Keys.SPACE.code(), 57, 0));
    }

    @Test
    void stagesWorkTogetherInOneGame() {
        TestGame game = start(b -> {});
        assertThat(bank.coins()).as("config read before onEnable").isEqualTo(5);
        assertThat(game.assets().isLoaded(BEEP))
                .as("sounds load with the startup group")
                .isTrue();
        assertThatThrownBy(() -> game.registries().register(Registries.INPUT_ACTION, JUMP))
                .as("registries are frozen after load")
                .isInstanceOf(IllegalStateException.class);

        tapSpace();
        runner.step(1);
        assertThat(player.jumps).isEqualTo(1);
        assertThat(player.keyEvents).isEqualTo(1);
        assertThat(bank.coins()).isEqualTo(6);
        assertThat(player.lastSound.isPlaying()).isTrue();
        assertThat(player.hudFrames).isPositive();
        assertThat(game.display().stats().drawCalls()).isPositive();
        List<String> replies = new ArrayList<>();
        assertThat(game.commands().dispatch("/coins", replies::add)).isTrue();
        assertThat(replies).containsExactly("Coins: 6");
        runner.step(20);
        assertThat(bank.paydays).isEqualTo(2);
        assertThat(game.audio().activeVoices()).as("the 0.2 s beep ended").isZero();
    }

    @Test
    void pauseStopsGameTimeButNotInputOrUiSounds() {
        TestGame game = start(b -> {});
        Playback gameSound = game.audio().play(JUMP_SOUND).setLooping(true);
        Playback uiSound = game.audio().play(MENU_SOUND).setLooping(true);
        assertThat(MENU_SOUND.pauseMode()).isEqualTo(PauseMode.ALWAYS);
        game.engine().pause();
        long tick = game.engine().tick();
        int paydays = bank.paydays;
        runner.backend().input().inject(l -> l.keyDown(Keys.SPACE.code(), 57, 0, false));
        runner.step(30);
        assertThat(game.engine().tick()).isEqualTo(tick);
        assertThat(bank.paydays).isEqualTo(paydays);
        assertThat(game.input().pressed(JUMP))
                .as("actions update on real-time ticks while paused")
                .isTrue();
        assertThat(runner.backend().audio().isPaused(0)).isTrue();
        assertThat(runner.backend().audio().isPaused(1)).isFalse();
        game.engine().resume();
        runner.step(1);
        assertThat(runner.backend().audio().isPaused(0)).isFalse();
        assertThat(gameSound.isPlaying()).isTrue();
        assertThat(uiSound.isPlaying()).isTrue();
    }

    @Test
    void slowMotionDoesNotLoseTapsBetweenGameTicks() {
        TestGame game = start(b -> {});
        game.engine().setTimeScale(0.25f);
        List<Boolean> seen = new ArrayList<>();
        game.on(TickStartEvent.class, e -> seen.add(game.input().justPressed(JUMP)));
        tapSpace();
        runner.step(12);
        assertThat(seen).as("one game tick every four frames").hasSizeBetween(2, 3);
        assertThat(seen.get(0)).as("the tap reaches the next game tick").isTrue();
        assertThat(seen.get(1)).isFalse();
        assertThat(player.jumps).isEqualTo(1);
    }

    @Test
    void disablingAModuleRemovesItsListenersTasksAndServices() {
        TestGame game = start(b -> {});
        game.modules().disable("bank");
        assertThat(game.modules().isEnabled("player")).as("dependents go first").isFalse();
        assertThat(game.services().get(Bank.class)).isNull();
        int paydays = bank.paydays;
        tapSpace();
        runner.step(30);
        assertThat(player.jumps).isZero();
        assertThat(player.keyEvents).isZero();
        assertThat(bank.paydays).isEqualTo(paydays);
        assertThat(game.commands().dispatch("/coins", s -> {})).isFalse();
        assertThat(game.input().pressed(JUMP)).as("input itself keeps working").isFalse();

        game.modules().enable("player");
        tapSpace();
        runner.step(1);
        assertThat(player.jumps).isEqualTo(1);
    }

    @Test
    void reloadsAndResourcePacksReplaceSoundsInPlace() {
        TestGame game = start(b -> {});
        List<String> reloaded = new ArrayList<>();
        game.on(AssetReloadEvent.class, e -> reloaded.add(e.key().key().toString()));
        Playback playing = game.audio().play(JUMP_SOUND).setLooping(true);
        runner.backend().files().putAsset("test/sounds/beep.wav", AudioTest.wav(0.3f));
        runner.backend().files().simulateAssetChange("test/sounds/beep.wav");
        runner.step(8);
        assertThat(reloaded).contains("test:sounds/beep");
        assertThat(playing.isPlaying()).as("voices of the old clip stop").isFalse();
        assertThat(game.assets().get(BEEP).duration()).isEqualTo(0.3f);
        assertThat(game.audio().play(JUMP_SOUND).isPlaying()).isTrue();

        game.assets().resourcePacks().setEnabled(List.of("loud"));
        runner.step(8);
        assertThat(game.assets().get(BEEP).duration()).isEqualTo(0.4f);
        assertThat(game.audio().play(JUMP_SOUND).isPlaying()).isTrue();
        assertThat(game.tr("hud.coins", 3)).isEqualTo("Coins: 3");
    }

    @Test
    void stoppingFreesVoicesAndKeepsBindingsAndVolumes() {
        TestGame game = start(b -> {});
        game.audio().play(JUMP_SOUND).setLooping(true);
        game.input().bindings().rebind(JUMP, 0, Keys.J);
        game.audio().bus(Audio.SFX).setVolume(0.25f);
        HeadlessBackend backend = runner.backend();
        runner.stop();
        runner = null;
        assertThat(backend.audio().voicesInUse()).isZero();
        byte[] saved = backend.files().userData(PreferencesImpl.FILE);
        assertThat(saved).isNotNull();

        TestGame second = start(b -> b.files()
                .writeUserData(PreferencesImpl.FILE, ByteBuffer.wrap(saved), new PlatformCallback<>() {
                    @Override
                    public void success(@Nullable Void value) {}

                    @Override
                    public void failure(Throwable error) {}
                }));
        assertThat(second.input().bindings().of(JUMP)).containsExactly(Keys.J);
        assertThat(second.audio().bus(Audio.SFX).volume()).isEqualTo(0.25f);
        runner.backend().input().inject(l -> l.keyDown(Keys.J.code(), 36, 0, false));
        runner.step(1);
        assertThat(player.jumps).isEqualTo(1);
    }
}
