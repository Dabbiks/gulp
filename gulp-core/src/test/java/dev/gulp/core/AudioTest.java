package dev.gulp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.audio.Audio;
import dev.gulp.api.audio.AudioClip;
import dev.gulp.api.audio.Bus;
import dev.gulp.api.audio.Music;
import dev.gulp.api.audio.MusicEndEvent;
import dev.gulp.api.audio.PauseMode;
import dev.gulp.api.audio.PcmSource;
import dev.gulp.api.audio.Playback;
import dev.gulp.api.audio.Sound;
import dev.gulp.api.math.Vec2;
import dev.gulp.api.registry.Key;
import dev.gulp.api.registry.Registries;
import dev.gulp.backend.headless.HeadlessAudio;
import dev.gulp.backend.headless.HeadlessBackend;
import dev.gulp.backend.headless.HeadlessRunner;
import dev.gulp.core.Fixtures.TestGame;
import dev.gulp.core.audio.WavDecoder;
import dev.gulp.core.data.PreferencesImpl;
import dev.gulp.platform.DecodedAudio;
import dev.gulp.platform.PlatformCallback;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AudioTest {

    private static final AssetKey<AudioClip> BEEP = AssetKey.audio("test:sounds/beep");
    private static final AssetKey<AudioClip> BOOP = AssetKey.audio("test:sounds/boop");
    private static final AssetKey<Music> THEME = AssetKey.music("test:music/theme");
    private static final AssetKey<Music> SHORT = AssetKey.music("test:music/short");

    private static final Sound PICKUP = Sound.builder(Key.of("test", "pickup"))
            .file(BEEP)
            .file(BOOP)
            .pitchRange(0.9f, 1.1f)
            .volumeRange(0.5f, 1f)
            .maxInstances(2)
            .minInterval(0f)
            .build();
    private static final Sound STEP =
            Sound.builder(Key.of("test", "step")).file(BEEP).minInterval(0.1f).build();
    private static final Sound CLICK = Sound.builder(Key.of("test", "click"))
            .file(BEEP)
            .bus(Audio.UI)
            .minInterval(0f)
            .build();
    private static final Sound ALARM = Sound.builder(Key.of("test", "alarm"))
            .file(BEEP)
            .priority(5)
            .minInterval(0f)
            .distance(1f, 11f)
            .build();
    private static final Sound LINE = Sound.builder(Key.of("test", "line"))
            .file(BOOP)
            .bus(Audio.VOICE)
            .minInterval(0f)
            .pauseMode(PauseMode.ALWAYS)
            .build();

    private @Nullable HeadlessRunner runner;
    private final List<String> log = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (runner != null) {
            runner.stop();
        }
    }

    /** A WAV file: 16-bit mono sine at 44.1 kHz. */
    static byte[] wav(float seconds) {
        return wav(seconds, 1, 16, 1);
    }

    static byte[] wav(float seconds, int channels, int bits, int format) {
        int frames = Math.round(seconds * 44_100);
        int bytesPerSample = bits / 8;
        int dataLength = frames * channels * bytesPerSample;
        ByteBuffer out = ByteBuffer.allocate(44 + dataLength).order(ByteOrder.LITTLE_ENDIAN);
        out.put("RIFF".getBytes()).putInt(36 + dataLength).put("WAVE".getBytes());
        out.put("fmt ".getBytes()).putInt(16).putShort((short) format).putShort((short) channels);
        out.putInt(44_100).putInt(44_100 * channels * bytesPerSample);
        out.putShort((short) (channels * bytesPerSample)).putShort((short) bits);
        out.put("data".getBytes()).putInt(dataLength);
        for (int i = 0; i < frames * channels; i++) {
            double value = Math.sin(i * 0.05) * 0.5;
            switch (bits) {
                case 8 -> out.put((byte) (value * 127 + 128));
                case 16 -> out.putShort((short) (value * 32767));
                case 24 -> {
                    int sample = (int) (value * 8_388_607);
                    out.put((byte) sample).put((byte) (sample >> 8)).put((byte) (sample >> 16));
                }
                default -> {
                    if (format == 3) {
                        out.putFloat((float) value);
                    } else {
                        out.putInt((int) (value * Integer.MAX_VALUE));
                    }
                }
            }
        }
        return out.array();
    }

    private static void files(HeadlessBackend backend) {
        backend.files().putAsset("test/sounds/beep.wav", wav(0.1f));
        backend.files().putAsset("test/sounds/boop.wav", wav(0.2f));
        backend.files().putAsset("test/music/theme.wav", wav(1f));
        backend.files().putAsset("test/music/short.wav", wav(0.5f));
    }

    private TestGame start(Consumer<HeadlessBackend> prepare) {
        TestGame game = new TestGame();
        game.onLoad = () -> {
            for (Sound sound : List.of(PICKUP, STEP, CLICK, ALARM, LINE)) {
                game.registries().register(Registries.SOUND, sound);
            }
        };
        runner = HeadlessRunner.start(game, prepare.andThen(AudioTest::files));
        for (int i = 0; i < 20 && !runner.engine().isRunning(); i++) {
            runner.step(1);
        }
        assertThat(runner.engine().isRunning()).isTrue();
        return game;
    }

    private TestGame start() {
        return start(b -> {});
    }

    private HeadlessAudio platform() {
        return runner.backend().audio();
    }

    private <T> T loaded(TestGame game, AssetKey<T> key) {
        game.assets().load(key);
        for (int i = 0; i < 10 && !game.assets().isLoaded(key); i++) {
            runner.step(1);
        }
        return game.assets().get(key);
    }

    @Test
    void soundsLoadWithStartupAndRespectLimits() {
        TestGame game = start();
        Audio audio = game.audio();
        assertThat(game.assets().isLoaded(BEEP)).isTrue();
        assertThat(game.assets().get(BEEP).duration()).isCloseTo(0.1f, within(1e-3f));
        Playback first = audio.play(PICKUP);
        Playback second = audio.play(PICKUP, 0.5f, 1f);
        Playback third = audio.play(PICKUP);
        assertThat(first.isPlaying()).isTrue();
        assertThat(second.isPlaying()).isTrue();
        assertThat(third.isPlaying()).as("instance limit").isFalse();
        assertThat(first.pitch()).isBetween(0.9f, 1.1f);
        assertThat(first.volume()).isBetween(0.5f, 1f);
        assertThat(audio.activeVoices()).isEqualTo(2);
        assertThat(audio.maxVoices()).isEqualTo(32);
        runner.step(20);
        assertThat(first.isPlaying()).isFalse();
        assertThat(audio.activeVoices()).isZero();
        assertThat(platform().voicesInUse()).isZero();

        assertThat(audio.play(STEP).isPlaying()).isTrue();
        assertThat(audio.play(STEP).isPlaying()).as("minimum interval").isFalse();
        runner.step(7);
        assertThat(audio.play(STEP).isPlaying()).isTrue();

        Playback loop = audio.play(game.assets().get(BOOP), Audio.SFX).setLooping(true);
        runner.step(30);
        assertThat(loop.isPlaying()).isTrue();
        assertThat(loop.isLooping()).isTrue();
        loop.setLooping(false);
        runner.step(30);
        assertThat(loop.isPlaying()).isFalse();
        assertThatThrownBy(() -> audio.play(STEP, 1f, 1f).setPitch(0f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> audio.play(game.assets().get(BEEP), "nope"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void busesDuckFilterAndPersistVolumes() {
        TestGame game = start();
        Audio audio = game.audio();
        Bus sfx = audio.bus(Audio.SFX);
        sfx.setVolume(0.5f);
        sfx.setLowpass(800);
        sfx.setHighpass(100);
        sfx.setReverb(0.4f);
        Playback beep = audio.play(STEP).setVolume(1f);
        runner.step(1);
        assertThat(platform().gain(0)).isCloseTo(0.5f, within(1e-4f));
        assertThat(platform().lowpass(0)).isEqualTo(800f);
        assertThat(platform().highpass(0)).isEqualTo(100f);
        assertThat(platform().reverb(0)).isEqualTo(0.4f);
        audio.bus(Audio.MASTER).setMuted(true);
        runner.step(1);
        assertThat(platform().gain(0)).isZero();
        audio.bus(Audio.MASTER).setMuted(false);
        audio.bus(Audio.MASTER).setLowpass(400);
        runner.step(1);
        assertThat(platform().lowpass(0)).isEqualTo(400f);
        assertThat(beep.isPlaying()).isTrue();
        beep.fadeOut(0.05f);
        runner.step(5);
        assertThat(beep.isPlaying()).isFalse();

        Bus custom = audio.createBus("ambience");
        assertThat(audio.createBus("ambience")).isSameAs(custom);
        assertThat(audio.buses())
                .extracting(Bus::name)
                .containsExactly("master", "music", "sfx", "ui", "voice", "ambience");
        assertThatThrownBy(() -> audio.createBus("Bad Name")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> custom.duck("ambience", 0.5f)).isInstanceOf(IllegalArgumentException.class);
        custom.duck(Audio.SFX, 0.2f);
        custom.stopDucking(Audio.SFX);
        assertThat(custom.toString()).contains("ambience");

        Music theme = loaded(game, THEME);
        audio.music().play(theme, 0f);
        runner.step(1);
        assertThat(audio.bus(Audio.MUSIC).effectiveVolume()).isEqualTo(1f);
        Playback line = audio.play(LINE).setLooping(true);
        runner.step(20);
        assertThat(audio.bus(Audio.MUSIC).effectiveVolume()).isCloseTo(0.35f, within(1e-4f));
        line.stop();
        runner.step(40);
        assertThat(audio.bus(Audio.MUSIC).effectiveVolume()).isEqualTo(1f);

        HeadlessBackend first = runner.backend();
        runner.stop();
        byte[] saved = first.files().userData(PreferencesImpl.FILE);
        TestGame second = start(b -> b.files().writeUserData(PreferencesImpl.FILE, ByteBuffer.wrap(saved), noop()));
        assertThat(second.audio().bus(Audio.SFX).volume()).isEqualTo(0.5f);
        assertThat(second.audio().bus(Audio.MASTER).isMuted()).isFalse();
        assertThat(second.preferences().getFloat("audio.sfx.volume", 0f)).isEqualTo(0.5f);
    }

    @Test
    void gameSoundsPauseWithTheGameAndUiSoundsDoNot() {
        TestGame game = start();
        Playback game1 = game.audio().play(STEP).setLooping(true);
        Playback ui = game.audio().play(CLICK).setLooping(true);
        game.engine().pause();
        runner.step(1);
        assertThat(platform().isPaused(0)).isTrue();
        assertThat(platform().isPaused(1)).isFalse();
        game.engine().resume();
        runner.step(1);
        assertThat(platform().isPaused(0)).isFalse();
        game1.stop();
        ui.stop();
        assertThat(game.audio().activeVoices()).isZero();
        game.audio().play(CLICK);
        game.audio().stopAll();
        assertThat(game.audio().activeVoices()).isZero();
    }

    @Test
    void fullPoolStealsTheWeakestVoice() {
        TestGame game = start();
        Audio audio = game.audio();
        AudioClip beep = game.assets().get(BEEP);
        List<Playback> loops = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            loops.add(audio.play(beep, Audio.SFX).setLooping(true).setVolume(i == 7 ? 0.1f : 1f));
        }
        runner.step(1);
        assertThat(audio.activeVoices()).isEqualTo(32);
        Playback alarm = audio.play(ALARM).setLooping(true);
        assertThat(alarm.isPlaying()).isTrue();
        assertThat(loops.get(7).isPlaying()).as("quietest stolen").isFalse();
        assertThat(audio.activeVoices()).isEqualTo(32);
        audio.stopAll();
        for (int i = 0; i < 32; i++) {
            audio.play(ALARM).setLooping(true);
        }
        assertThat(audio.play(beep, Audio.SFX).isPlaying())
                .as("lower priority never steals")
                .isFalse();
    }

    @Test
    void positionedSoundsFadeAndPanWithDistance() {
        TestGame game = start();
        Audio audio = game.audio();
        audio.setListener(new Vec2(0, 0));
        assertThat(audio.listener()).isEqualTo(new Vec2(0, 0));
        Playback near = audio.playAt(ALARM, 0.5f, 0).setLooping(true);
        Playback middle = audio.playAt(ALARM, 6f, 0).setLooping(true);
        Playback far = audio.playAt(ALARM, -30f, 0).setLooping(true);
        runner.step(1);
        assertThat(platform().gain(0)).isEqualTo(1f);
        assertThat(platform().gain(1)).isCloseTo(0.5f, within(1e-4f));
        assertThat(platform().pan(1)).isGreaterThan(0f);
        assertThat(platform().gain(2)).isZero();
        assertThat(platform().pan(2)).isEqualTo(-1f);
        middle.setPosition(-6f, 0);
        runner.step(1);
        assertThat(platform().pan(1)).isLessThan(0f);
        audio.setListener(null);
        assertThat(audio.listener()).isEqualTo(game.display().camera().position());
        near.stop();
        far.stop();
    }

    @Test
    void musicLoopsCrossfadesAndPlaysPlaylists() {
        TestGame game = start();
        Audio audio = game.audio();
        game.on(MusicEndEvent.class, e -> log.add("end " + e.music().name()));
        Music theme = loaded(game, THEME);
        Music shortTrack = loaded(game, SHORT);
        assertThat(theme.duration()).isCloseTo(1f, within(1e-3f));
        assertThat(theme.channels()).isEqualTo(1);
        assertThat(theme.sampleRate()).isEqualTo(44_100);
        assertThat(theme.toString()).contains("test:music/theme");
        theme.setLoopPoints(11_025, 44_100);
        assertThat(theme.loopStart()).isEqualTo(11_025);
        assertThatThrownBy(() -> theme.setLoopPoints(10, 5)).isInstanceOf(IllegalArgumentException.class);

        audio.music().play(theme, 0.2f);
        assertThat(audio.music().isPlaying()).isTrue();
        assertThat(audio.music().current()).isSameAs(theme);
        runner.step(150);
        assertThat(audio.music().isPlaying()).as("loops").isTrue();
        assertThat(audio.music().position()).isBetween(0f, 1f);
        assertThat(log).isEmpty();

        audio.music().pause();
        assertThat(audio.music().isPlaying()).isFalse();
        audio.music().resume();
        audio.music().setVolume(0.5f);
        assertThat(audio.music().volume()).isEqualTo(0.5f);

        shortTrack.setLooping(false);
        assertThat(shortTrack.isLooping()).isFalse();
        audio.music().crossfadeTo(shortTrack, 0.2f);
        runner.step(60);
        assertThat(log).containsExactly("end test:music/short");
        assertThat(audio.music().isPlaying()).isFalse();
        assertThat(audio.music().current()).isNull();

        log.clear();
        audio.music().playlist(List.of(shortTrack, theme), false, 0f);
        runner.step(40);
        assertThat(log).containsExactly("end test:music/short");
        assertThat(audio.music().current()).isSameAs(theme);
        audio.music().playlist(List.of(shortTrack, theme), true, 0.1f);
        runner.step(80);
        assertThat(log).hasSizeGreaterThan(1);
        assertThatThrownBy(() -> audio.music().playlist(List.of(), false, 0f))
                .isInstanceOf(IllegalArgumentException.class);

        audio.music().stop(0.1f);
        runner.step(10);
        assertThat(audio.music().isPlaying()).isFalse();
        assertThat(audio.music().position()).isZero();

        audio.music().play(SHORT, 0f);
        runner.step(2);
        assertThat(audio.music().current()).isSameAs(shortTrack);
        audio.music().stop(0f);
        game.assets().unload(THEME);
    }

    @Test
    void pcmStreamsPlayUntilTheSourceEnds() {
        TestGame game = start();
        int[] produced = {0};
        PcmSource tone = new PcmSource() {
            @Override
            public int channels() {
                return 2;
            }

            @Override
            public int sampleRate() {
                return 22_050;
            }

            @Override
            public int read(float[] samples, int frames) {
                if (produced[0] >= 4_410) {
                    return -1;
                }
                int count = Math.min(frames, 4_410 - produced[0]);
                for (int i = 0; i < count * 2; i++) {
                    samples[i] = i % 2 == 0 ? 2f : -0.5f;
                }
                produced[0] += count;
                return count;
            }
        };
        Playback stream = game.audio().stream(tone, Audio.SFX);
        assertThat(stream.isPlaying()).isTrue();
        assertThat(stream.setLooping(true).isLooping()).isFalse();
        runner.step(20);
        assertThat(stream.isPlaying()).isFalse();
        assertThat(produced[0]).isEqualTo(4_410);
        assertThatThrownBy(() -> game.audio().stream(
                        new PcmSource() {
                            @Override
                            public int channels() {
                                return 3;
                            }

                            @Override
                            public int sampleRate() {
                                return 1;
                            }

                            @Override
                            public int read(float[] samples, int frames) {
                                return -1;
                            }
                        },
                        Audio.SFX))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lockedAudioSkipsSoundsAndDelaysMusic() {
        TestGame game = start();
        Music theme = loaded(game, THEME);
        platform().setUnlocked(false);
        assertThat(game.audio().isUnlocked()).isFalse();
        Playback skipped = game.audio().play(STEP);
        assertThat(skipped.isPlaying()).isFalse();
        skipped.stop();
        skipped.fadeOut(1f);
        assertThat(skipped.setVolume(1f)
                        .setPitch(2f)
                        .setLooping(true)
                        .setPosition(1, 1)
                        .isLooping())
                .isFalse();
        assertThat(skipped.volume()).isZero();
        assertThat(skipped.pitch()).isEqualTo(1f);
        assertThat(game.audio().play(game.assets().get(BEEP), Audio.SFX).isPlaying())
                .isFalse();
        assertThat(game.audio().stream(new SilentPcm(), Audio.SFX).isPlaying()).isFalse();
        game.audio().music().play(theme, 0f);
        runner.step(2);
        assertThat(game.audio().music().isPlaying()).isTrue();
        assertThat(game.audio().activeVoices()).isZero();
        platform().setUnlocked(true);
        runner.step(1);
        assertThat(game.audio().activeVoices()).isEqualTo(1);
    }

    @Test
    void soundsWhoseFilesAreMissingWarnOnce() {
        TestGame game = start();
        Sound ghost = Sound.builder(Key.of("test", "ghost"))
                .file(AssetKey.audio("test:sounds/ghost"))
                .build();
        assertThat(game.audio().play(ghost).isPlaying()).isFalse();
        runner.step(10);
        assertThat(game.audio().play(ghost).isPlaying()).isFalse();
        assertThatThrownBy(() -> Sound.builder(Key.of("test", "empty")).build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wavDecoderReadsCommonFormats() {
        for (int[] format : new int[][] {{1, 8, 1}, {2, 16, 1}, {1, 24, 1}, {1, 32, 1}, {2, 32, 3}}) {
            DecodedAudio decoded = WavDecoder.decode(ByteBuffer.wrap(wav(0.01f, format[0], format[1], format[2])));
            assertThat(decoded.channels()).isEqualTo(format[0]);
            assertThat(decoded.sampleRate()).isEqualTo(44_100);
            assertThat(decoded.samples().remaining()).isEqualTo(441 * format[0]);
            assertThat(decoded.samples().get(10)).isNotZero();
        }
        WavDecoder.Stream stream = WavDecoder.open(ByteBuffer.wrap(wav(0.01f)));
        stream.seek(400);
        ShortBuffer rest = ShortBuffer.allocate(100);
        assertThat(stream.read(rest)).isEqualTo(41);
        stream.close();
        assertThat(WavDecoder.isWav(ByteBuffer.wrap(new byte[4]))).isFalse();
        assertThatThrownBy(() -> WavDecoder.open(ByteBuffer.wrap("nope".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WavDecoder.open(ByteBuffer.wrap(wav(0.01f, 1, 16, 2))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class SilentPcm implements PcmSource {
        @Override
        public int channels() {
            return 1;
        }

        @Override
        public int sampleRate() {
            return 8_000;
        }

        @Override
        public int read(float[] samples, int frames) {
            return 0;
        }
    }

    private static <T> PlatformCallback<T> noop() {
        return new PlatformCallback<>() {
            @Override
            public void success(T value) {}

            @Override
            public void failure(Throwable error) {}
        };
    }
}
