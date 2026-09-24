package dev.gulp.backend.web;

import dev.gulp.platform.PlatformAudio;
import dev.gulp.platform.PlatformAudioStream;
import java.nio.ShortBuffer;
import org.teavm.jso.typedarrays.Int16Array;

/**
 * Voices on WebAudio through {@code gulp-runtime.js}. The audio context starts suspended and unlocks with the first
 * click or key press; until then {@link #isUnlocked()} is {@code false}. Without WebAudio every voice request fails.
 */
final class WebAudio implements PlatformAudio {

    /** Number of voices. */
    static final int VOICES = 32;

    private final boolean available;
    private final boolean[] used = new boolean[VOICES];

    WebAudio() {
        this.available = Js.audioInit(VOICES);
    }

    /**
     * Returns whether the browser has WebAudio.
     *
     * @return {@code false} when the game runs silent
     */
    boolean isAvailable() {
        return available;
    }

    static Int16Array toJs(ShortBuffer samples) {
        short[] array = new short[samples.remaining()];
        samples.duplicate().get(array);
        return Int16Array.copyFromJavaArray(array);
    }

    @Override
    public boolean isUnlocked() {
        return available && Js.audioUnlocked();
    }

    @Override
    public int createBuffer(ShortBuffer samples, int channels, int sampleRate) {
        return available ? Js.audioBuffer(toJs(samples), channels, sampleRate) : 0;
    }

    @Override
    public void deleteBuffer(int buffer) {
        if (available) {
            Js.deleteAudioBuffer(buffer);
        }
    }

    @Override
    public int acquireVoice() {
        if (!available) {
            return -1;
        }
        for (int i = 0; i < VOICES; i++) {
            if (!used[i]) {
                used[i] = true;
                return i;
            }
        }
        return -1;
    }

    @Override
    public void releaseVoice(int voice) {
        Js.voiceStop(voice);
        Js.voiceGain(voice, 1f);
        Js.voicePitch(voice, 1f);
        Js.voicePan(voice, 0f);
        Js.voiceFilter(voice, 0f, 0f);
        Js.voiceReverb(voice, 0f);
        used[voice] = false;
    }

    @Override
    public void play(int voice, int buffer, boolean loop) {
        Js.voicePlay(voice, buffer, loop);
    }

    @Override
    public void queue(int voice, int buffer) {
        Js.voiceQueue(voice, buffer);
    }

    @Override
    public int unqueueProcessed(int voice) {
        return Js.voiceUnqueue(voice);
    }

    @Override
    public void setPaused(int voice, boolean paused) {
        Js.voicePause(voice, paused);
    }

    @Override
    public void setLooping(int voice, boolean loop) {
        Js.voiceLooping(voice, loop);
    }

    @Override
    public void stop(int voice) {
        Js.voiceStop(voice);
    }

    @Override
    public boolean isPlaying(int voice) {
        return Js.voicePlaying(voice);
    }

    @Override
    public void setGain(int voice, float gain) {
        Js.voiceGain(voice, gain);
    }

    @Override
    public void setPitch(int voice, float pitch) {
        Js.voicePitch(voice, pitch);
    }

    @Override
    public void setPan(int voice, float pan) {
        Js.voicePan(voice, pan);
    }

    @Override
    public void setFilter(int voice, float lowpassHertz, float highpassHertz) {
        Js.voiceFilter(voice, lowpassHertz, highpassHertz);
    }

    @Override
    public void setReverb(int voice, float send) {
        Js.voiceReverb(voice, send);
    }

    @Override
    public void setMasterGain(float gain) {
        if (available) {
            Js.masterGain(gain);
        }
    }

    /** Music decoded by the browser and kept there; read in pieces. */
    static final class Stream implements PlatformAudioStream {
        private final int id;
        private final int channels;
        private final int sampleRate;
        private final long frames;

        Stream(int id, int channels, int sampleRate, long frames) {
            this.id = id;
            this.channels = channels;
            this.sampleRate = sampleRate;
            this.frames = frames;
        }

        @Override
        public int channels() {
            return channels;
        }

        @Override
        public int sampleRate() {
            return sampleRate;
        }

        @Override
        public long frames() {
            return frames;
        }

        @Override
        public int read(ShortBuffer out) {
            short[] samples = Js.streamRead(id, out.remaining() / channels).copyToJavaArray();
            out.put(samples);
            return samples.length / channels;
        }

        @Override
        public void seek(long frame) {
            Js.streamSeek(id, frame);
        }

        @Override
        public void close() {
            Js.streamClose(id);
        }
    }
}
