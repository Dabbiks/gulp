package dev.gulp.core.audio;

import dev.gulp.api.asset.AssetKey;
import dev.gulp.api.audio.Music;
import dev.gulp.api.audio.MusicEndEvent;
import dev.gulp.api.audio.MusicPlayer;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * {@link MusicPlayer}: one current track, tracks fading out, an optional playlist. Starting a track waits for its
 * decoder and, on the web, for the audio unlock.
 */
final class MusicPlayerImpl implements MusicPlayer {

    private final AudioImpl audio;
    private final List<StreamVoice> fading = new ArrayList<>();
    private @Nullable StreamVoice current;
    private @Nullable MusicImpl pending;
    private @Nullable MusicImpl startingMusic;
    private float pendingFade;
    private int request;
    private boolean starting;
    private boolean paused;
    private float volume = 1f;

    private final List<MusicImpl> playlist = new ArrayList<>();
    private final List<MusicImpl> order = new ArrayList<>();
    private int index;
    private boolean shuffle;
    private float playlistFade;
    private boolean advanced;

    MusicPlayerImpl(AudioImpl audio) {
        this.audio = audio;
    }

    private static MusicImpl impl(Music music) {
        if (music instanceof MusicImpl impl) {
            return impl;
        }
        throw new IllegalArgumentException("Music must come from assets(): " + music);
    }

    @Override
    public void play(Music music, float fadeInSeconds) {
        playlist.clear();
        begin(impl(music), fadeInSeconds, false);
    }

    @Override
    public void play(AssetKey<Music> music, float fadeInSeconds) {
        int expected = ++request;
        playlist.clear();
        audio.loadMusic(music, loaded -> {
            if (request == expected) {
                begin(impl(loaded), fadeInSeconds, false);
            }
        });
    }

    @Override
    public void crossfadeTo(Music music, float seconds) {
        playlist.clear();
        begin(impl(music), seconds, true);
    }

    @Override
    public void playlist(List<Music> tracks, boolean shuffleTracks, float crossfadeSeconds) {
        if (tracks.isEmpty()) {
            throw new IllegalArgumentException("A playlist needs at least one track");
        }
        playlist.clear();
        for (Music track : tracks) {
            playlist.add(impl(track));
        }
        shuffle = shuffleTracks;
        playlistFade = Math.max(0f, crossfadeSeconds);
        reorder();
        index = 0;
        begin(order.get(0), 0f, false);
        advanced = false;
    }

    private void reorder() {
        order.clear();
        order.addAll(playlist);
        if (shuffle) {
            for (int i = order.size() - 1; i > 0; i--) {
                int j = audio.randomInt(i + 1);
                MusicImpl swap = order.get(i);
                order.set(i, order.get(j));
                order.set(j, swap);
            }
        }
    }

    private void next() {
        index++;
        if (index >= order.size()) {
            reorder();
            index = 0;
        }
        advanced = false;
        MusicImpl track = order.get(index);
        if (playlistFade > 0f) {
            begin(track, playlistFade, true);
        } else {
            begin(track, 0f, false);
        }
    }

    private void begin(MusicImpl music, float fade, boolean crossfade) {
        request++;
        StreamVoice old = current;
        current = null;
        if (old != null) {
            if (crossfade) {
                old.fadeOut(fade);
                fading.add(old);
            } else {
                old.stop();
            }
        }
        if (!crossfade) {
            for (StreamVoice voice : new ArrayList<>(fading)) {
                voice.stop();
            }
            fading.clear();
        }
        pending = music;
        startingMusic = music;
        pendingFade = fade;
        starting = true;
        tryStart();
    }

    private void tryStart() {
        MusicImpl music = pending;
        if (music == null || !audio.isUnlocked()) {
            return;
        }
        pending = null;
        int expected = request;
        float fade = pendingFade;
        music.takeStream(
                stream -> {
                    if (expected != request) {
                        music.giveBack(stream);
                        return;
                    }
                    starting = false;
                    boolean loop = playlist.isEmpty() && music.isLooping();
                    StreamVoice voice =
                            audio.startMusic(new StreamVoice.MusicSource(music, stream, loop), music, volume);
                    if (voice == null) {
                        return;
                    }
                    voice.fadeIn(fade);
                    voice.userPaused = paused;
                    current = voice;
                },
                error -> {
                    starting = false;
                    audio.logger().error("Could not play music " + music.name(), error);
                });
    }

    void update() {
        if (pending != null) {
            tryStart();
        }
        StreamVoice voice = current;
        if (voice != null && !playlist.isEmpty() && playlistFade > 0f && !advanced && voice.music != null) {
            MusicImpl music = voice.music;
            float remaining = (music.frames() - voice.playedFrame()) / (float) music.sampleRate();
            if (remaining <= playlistFade) {
                advanced = true;
                audio.fire(new MusicEndEvent(music));
                next();
            }
        }
    }

    /** Called when a music voice ended. */
    void ended(StreamVoice voice, boolean natural) {
        fading.remove(voice);
        if (voice != current) {
            return;
        }
        current = null;
        if (natural && voice.music != null) {
            audio.fire(new MusicEndEvent(voice.music));
            if (!playlist.isEmpty()) {
                next();
            }
        }
    }

    @Override
    public void stop(float fadeOutSeconds) {
        request++;
        playlist.clear();
        pending = null;
        starting = false;
        StreamVoice voice = current;
        current = null;
        if (voice != null) {
            voice.fadeOut(fadeOutSeconds);
            if (fadeOutSeconds > 0f) {
                fading.add(voice);
            }
        }
    }

    @Override
    public void pause() {
        paused = true;
        setPaused(true);
    }

    @Override
    public void resume() {
        paused = false;
        setPaused(false);
    }

    private void setPaused(boolean on) {
        if (current != null) {
            current.userPaused = on;
        }
        for (StreamVoice voice : fading) {
            voice.userPaused = on;
        }
    }

    @Override
    public @Nullable Music current() {
        StreamVoice voice = current;
        if (voice != null) {
            return voice.music;
        }
        return starting ? startingMusic : null;
    }

    @Override
    public boolean isPlaying() {
        return !paused && (current != null || starting);
    }

    @Override
    public float position() {
        StreamVoice voice = current;
        if (voice == null || voice.music == null) {
            return 0f;
        }
        long frame = Math.min(voice.playedFrame(), voice.music.frames());
        return frame / (float) voice.music.sampleRate();
    }

    @Override
    public float volume() {
        return volume;
    }

    @Override
    public void setVolume(float newVolume) {
        volume = Math.max(0f, newVolume);
        if (current != null) {
            current.setVolume(volume);
        }
    }
}
